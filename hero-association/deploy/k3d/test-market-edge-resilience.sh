#!/usr/bin/env bash
set -euo pipefail

script_dir="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")" && pwd)"
export KUBECONFIG="$script_dir/.kubeconfig"

if [[ ! -f "$KUBECONFIG" || "$(kubectl config current-context)" != 'k3d-hero-association' ]]; then
  printf 'Use the isolated hero-association k3d kubeconfig first.\n' >&2
  exit 1
fi

gateway_namespace=envoy-gateway-system
proxy_selector='app.kubernetes.io/component=proxy,gateway.envoyproxy.io/owning-gateway-name=hero-association'
mapfile -t proxy_deployments < <(kubectl -n "$gateway_namespace" get deployments -l "$proxy_selector" -o jsonpath='{range .items[*]}{.metadata.name}{"\n"}{end}')
if [[ ${#proxy_deployments[@]} -ne 1 ]]; then
  printf 'Expected exactly one generated Hero Association Envoy proxy Deployment.\n' >&2
  exit 1
fi
proxy_deployment="${proxy_deployments[0]}"

redis_replicas="$(kubectl -n "$gateway_namespace" get deployment/redis-gateway -o jsonpath='{.spec.replicas}')"
proxy_replicas="$(kubectl -n "$gateway_namespace" get deployment/"$proxy_deployment" -o jsonpath='{.spec.replicas}')"
if [[ "$redis_replicas" != 1 || "$proxy_replicas" != 1 ]]; then
  printf 'Expected the normal one-Redis, one-proxy lab; found Redis=%s, proxy=%s.\n' "$redis_replicas" "$proxy_replicas" >&2
  exit 1
fi

redis_changed=false
proxy_changed=false
restore() {
  local result=$?
  trap - EXIT
  set +e
  if [[ "$proxy_changed" == true ]]; then
    kubectl -n "$gateway_namespace" scale deployment/"$proxy_deployment" --replicas="$proxy_replicas" || result=1
    kubectl -n "$gateway_namespace" rollout status deployment/"$proxy_deployment" --timeout=180s || result=1
  fi
  if [[ "$redis_changed" == true ]]; then
    kubectl -n "$gateway_namespace" scale deployment/redis-gateway --replicas="$redis_replicas" || result=1
    kubectl -n "$gateway_namespace" rollout status deployment/redis-gateway --timeout=180s || result=1
  fi
  exit "$result"
}
trap restore EXIT
trap 'exit 130' INT
trap 'exit 143' TERM

run_market_load() {
  (cd "$script_dir/../../e2e" && npm run test:market:k6)
}

run_outage_check() {
  (cd "$script_dir/../../e2e" &&
    HERO_ASSOCIATION_K3D_PLAYWRIGHT_CONFIG=playwright.k3d.market-outage.config.js node run-k3d-playwright.js)
}

assert_market_logs() {
  local proxy_pod="$1"
  local since_time="$2"
  local mode="$3"
  for attempt in {1..10}; do
    if kubectl -n "$gateway_namespace" logs pod/"$proxy_pod" -c envoy --since-time="$since_time" --tail=-1 |
      node -e '
      let data = "";
      process.stdin.on("data", chunk => data += chunk);
      process.stdin.on("end", () => {
        let requests = 0;
        let local429 = 0;
        let local500 = 0;
        let upstream429 = 0;
        for (const line of data.split("\n")) {
          let entry;
          try { entry = JSON.parse(line); } catch { continue; }
          if (entry.method !== "POST" || entry["x-envoy-origin-path"] !== "/api/v1/market/orders") continue;
          requests++;
          if (entry.response_code === 500 && entry.response_code_details === "rate_limiter_error") local500++;
          if (entry.response_code !== 429) continue;
          if (entry.response_code_details === "via_upstream") upstream429++;
          else local429++;
        }
        console.log(`${process.argv[2]}: ${requests} market POSTs, ${local429} Envoy 429s, ${local500} Envoy 500s, ${upstream429} BFF 429s`);
        if (requests === 0 || (process.argv[1] === "outage" && (local500 === 0 || upstream429 !== 0)) ||
            (process.argv[1] === "two-proxies" && (local429 === 0 || upstream429 !== 0))) process.exitCode = 1;
      });
    ' "$mode" "$proxy_pod"; then
      return 0
    fi
    sleep 1
  done
  return 1
}

printf 'Testing gateway Redis outage; Envoy must reject market orders before BFF.\n'
kubectl -n "$gateway_namespace" scale deployment/redis-gateway --replicas=0
redis_changed=true
kubectl -n "$gateway_namespace" wait --for=delete pod -l app=redis-gateway --timeout=120s
outage_since="$(date -u '+%Y-%m-%dT%H:%M:%SZ')"
for attempt in {1..30}; do
  rate_limit_ready="$(kubectl -n "$gateway_namespace" get deployment/envoy-ratelimit -o jsonpath='{.status.readyReplicas}')"
  if [[ "$rate_limit_ready" != '1' ]]; then break; fi
  sleep 1
done
if [[ "$rate_limit_ready" == '1' ]]; then
  printf 'Rate-limit service remained ready after gateway Redis stopped.\n' >&2
  exit 1
fi
run_outage_check
mapfile -t proxy_pods < <(kubectl -n "$gateway_namespace" get pods -l "$proxy_selector" -o jsonpath='{range .items[*]}{.metadata.name}{"\n"}{end}')
if [[ ${#proxy_pods[@]} -ne 1 ]]; then
  printf 'Expected one Envoy proxy Pod during the Redis outage; found %s.\n' "${#proxy_pods[@]}" >&2
  exit 1
fi
assert_market_logs "${proxy_pods[0]}" "$outage_since" outage
kubectl -n "$gateway_namespace" scale deployment/redis-gateway --replicas="$redis_replicas"
kubectl -n "$gateway_namespace" rollout status deployment/redis-gateway --timeout=180s
kubectl -n "$gateway_namespace" rollout status deployment/envoy-ratelimit --timeout=180s
redis_changed=false

printf 'Testing one user budget across two Envoy proxies.\n'
kubectl -n "$gateway_namespace" scale deployment/"$proxy_deployment" --replicas=2
proxy_changed=true
kubectl -n "$gateway_namespace" rollout status deployment/"$proxy_deployment" --timeout=180s
ready_proxy_replicas="$(kubectl -n "$gateway_namespace" get deployment/"$proxy_deployment" -o jsonpath='{.status.readyReplicas}')"
if [[ "$ready_proxy_replicas" != 2 ]]; then
  printf 'Expected two ready Envoy proxy Pods; found %s.\n' "$ready_proxy_replicas" >&2
  exit 1
fi
mapfile -t proxy_pods < <(kubectl -n "$gateway_namespace" get pods -l "$proxy_selector" -o jsonpath='{range .items[*]}{.metadata.name}{"\n"}{end}')
if [[ ${#proxy_pods[@]} -ne 2 ]]; then
  printf 'Expected two Envoy proxy Pods; found %s.\n' "${#proxy_pods[@]}" >&2
  exit 1
fi
shared_since="$(date -u '+%Y-%m-%dT%H:%M:%SZ')"
run_market_load
for proxy_pod in "${proxy_pods[@]}"; do
  assert_market_logs "$proxy_pod" "$shared_since" two-proxies
done
printf 'Both resilience checks passed; restoring the normal one-proxy lab.\n'
