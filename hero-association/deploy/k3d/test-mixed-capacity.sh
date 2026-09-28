#!/usr/bin/env bash
set -euo pipefail

script_dir="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")" && pwd)"
e2e_dir="$script_dir/../../e2e"
manifest="$script_dir/../k8s/backend/mixed-capacity-test.yaml"
export KUBECONFIG="$script_dir/.kubeconfig"
namespace=hero-association
database="ha_capacity_$(openssl rand -hex 4)"
database_created=false
resources_created=false
bootstrap_job=
stage_log=
load_pid=
stage_seconds="${HERO_ASSOCIATION_K3D_MIXED_SECONDS:-60}"
stage_list="${HERO_ASSOCIATION_K3D_MIXED_STAGES:-4 8 16 32 64}"
prewarm_replicas="${HERO_ASSOCIATION_K3D_MIXED_PREWARM_REPLICAS:-2}"

if [[ $# -ne 0 ]]; then
  printf 'Usage: %s\n' "$0" >&2
  exit 2
fi
if [[ ! -f "$KUBECONFIG" || "$(kubectl config current-context)" != k3d-hero-association ]]; then
  printf 'The isolated hero-association k3d kubeconfig is required.\n' >&2
  exit 1
fi
if [[ ! "$stage_seconds" =~ ^[0-9]+$ ]] || (( stage_seconds < 10 || stage_seconds > 300 )); then
  printf 'HERO_ASSOCIATION_K3D_MIXED_SECONDS must be between 10 and 300.\n' >&2
  exit 2
fi
if [[ "$prewarm_replicas" != 2 && "$prewarm_replicas" != 8 ]]; then
  printf 'HERO_ASSOCIATION_K3D_MIXED_PREWARM_REPLICAS must be 2 or 8.\n' >&2
  exit 2
fi
read -r -a stages <<< "$stage_list"
if (( ${#stages[@]} == 0 )); then
  printf 'At least one load stage is required.\n' >&2
  exit 2
fi
for clients in "${stages[@]}"; do
  if [[ ! "$clients" =~ ^[0-9]+$ ]] || (( clients < 1 || clients > 128 )); then
    printf 'Each HERO_ASSOCIATION_K3D_MIXED_STAGES value must be 1 to 128.\n' >&2
    exit 2
  fi
done
for resource in \
  deployment/core-capacity-test deployment/bff-capacity-test \
  service/core-capacity-test service/bff-capacity-test \
  hpa/core-capacity-test hpa/bff-capacity-test \
  httproute/bff-capacity-test peerauthentication/core-capacity-test-strict-mtls \
  authorizationpolicy/core-capacity-test-from-bff; do
  if kubectl -n "$namespace" get "$resource" >/dev/null 2>&1; then
    printf 'Refusing to replace existing %s.\n' "$resource" >&2
    exit 1
  fi
done

admin_query() {
  kubectl -n "$namespace" exec deployment/postgres-core -c postgres -- \
    psql -X -q -v ON_ERROR_STOP=1 -U hero_association -d hero_association -At -c "$1"
}
query() {
  kubectl -n "$namespace" exec deployment/postgres-core -c postgres -- \
    psql -X -q -v ON_ERROR_STOP=1 -U hero_association -d "$database" -At -c "$1"
}

cleanup() {
  result=$?
  trap - EXIT
  if [[ -n "$load_pid" ]] && kill -0 "$load_pid" 2>/dev/null; then
    kill "$load_pid" 2>/dev/null || true
    wait "$load_pid" 2>/dev/null || true
  fi
  if [[ -n "$stage_log" ]]; then
    rm -f -- "$stage_log"
  fi
  if (( result != 0 )) && [[ "$resources_created" == true ]]; then
    kubectl -n "$namespace" get pods -l 'app in (core-capacity-test,bff-capacity-test)' -o wide >&2 || true
    kubectl -n "$namespace" logs deployment/core-capacity-test -c core-capacity-test --tail=40 >&2 || true
    kubectl -n "$namespace" logs deployment/bff-capacity-test -c bff-capacity-test --tail=40 >&2 || true
  fi
  if [[ "$resources_created" == true ]]; then
    if ! kubectl delete -f "$manifest" --ignore-not-found=true --wait=true --timeout=5m >&2; then
      result=1
    fi
    if ! kubectl -n "$namespace" wait --for=delete pod \
      -l 'app in (core-capacity-test,bff-capacity-test)' --timeout=2m >&2; then
      result=1
    fi
  fi
  if [[ -n "$bootstrap_job" ]]; then
    if ! kubectl -n "$namespace" delete job "$bootstrap_job" --ignore-not-found=true --wait=false >&2; then
      result=1
    fi
  fi
  if [[ "$database_created" == true ]]; then
    dropped=false
    for attempt in {1..6}; do
      if admin_query "DROP DATABASE \"$database\" WITH (FORCE)" >/dev/null 2>&1; then
        dropped=true
        break
      fi
      sleep 5
    done
    if [[ "$dropped" != true ]]; then
      printf 'Could not remove temporary database %s after six attempts.\n' "$database" >&2
      result=1
    fi
  fi
  exit "$result"
}
trap cleanup EXIT

if [[ "$(admin_query "SELECT count(*) FROM pg_database WHERE datname = '$database'")" != 0 ]]; then
  printf 'Refusing to reuse existing database %s.\n' "$database" >&2
  exit 1
fi
printf 'Creating temporary database %s.\n' "$database"
admin_query "CREATE DATABASE \"$database\""
database_created=true
bootstrap_job="$(kubectl set env --local -f "$script_dir/../k8s/backend/core-db-bootstrap.yaml" \
  "QUARKUS_DATASOURCE_JDBC_URL=jdbc:postgresql://postgres-core:5432/$database" -o yaml \
  | kubectl create -f - -o jsonpath='{.metadata.name}')"
if ! kubectl -n "$namespace" wait --for=condition=complete "job/$bootstrap_job" --timeout=5m; then
  kubectl -n "$namespace" logs "job/$bootstrap_job" --all-containers=true >&2 || true
  exit 1
fi
query "UPDATE agency SET name = 'Capacity Lab Agency', name_normalized = 'capacity lab agency' WHERE id = '019c4c00-0001-7000-8000-000000000001'" >/dev/null
if [[ "$(query "SELECT name FROM agency WHERE id = '019c4c00-0001-7000-8000-000000000001'")" != 'Capacity Lab Agency' ]]; then
  printf 'The isolated agency marker could not be created.\n' >&2
  exit 1
fi

printf 'Starting isolated BFF and Core with 2-8 Pod HPAs.\n'
resources_created=true
sed "s/REPLACE_WITH_TEST_DATABASE/$database/g" "$manifest" | kubectl create -f - >/dev/null
kubectl -n "$namespace" rollout status deployment/core-capacity-test --timeout=5m
kubectl -n "$namespace" rollout status deployment/bff-capacity-test --timeout=5m
if [[ "$prewarm_replicas" == 8 ]]; then
  printf 'Holding both test HPAs at eight Pods for a fixed-capacity run.\n'
  kubectl -n "$namespace" patch hpa core-capacity-test --type=merge -p '{"spec":{"minReplicas":8}}' >/dev/null
  kubectl -n "$namespace" patch hpa bff-capacity-test --type=merge -p '{"spec":{"minReplicas":8}}' >/dev/null
  prewarmed=false
  for attempt in {1..30}; do
    bff_ready="$(kubectl -n "$namespace" get deployment bff-capacity-test -o jsonpath='{.status.readyReplicas}')"
    core_ready="$(kubectl -n "$namespace" get deployment core-capacity-test -o jsonpath='{.status.readyReplicas}')"
    if [[ "$bff_ready" == 8 && "$core_ready" == 8 ]]; then
      prewarmed=true
      break
    fi
    sleep 10
  done
  if [[ "$prewarmed" != true ]]; then
    printf 'Both capacity Deployments must reach eight Ready Pods.\n' >&2
    exit 1
  fi
fi

last_passing_clients=
last_passing_rps=
first_failing_clients=
for clients in "${stages[@]}"; do
  stage_log="$(mktemp)"
  printf 'Running %s clients for %s seconds with 90%% reads and 10%% activity writes.\n' "$clients" "$stage_seconds"
  (cd "$e2e_dir" && \
    HERO_ASSOCIATION_K3D_MIXED_SECONDS="$stage_seconds" \
    HERO_ASSOCIATION_K3D_MIXED_CLIENTS="$clients" \
    npm run load:mixed:k3d) >"$stage_log" 2>&1 &
  load_pid=$!
  peak_bff=2
  peak_core=2
  while kill -0 "$load_pid" 2>/dev/null; do
    sleep 10
    bff_replicas="$(kubectl -n "$namespace" get hpa bff-capacity-test -o jsonpath='{.status.currentReplicas}')"
    core_replicas="$(kubectl -n "$namespace" get hpa core-capacity-test -o jsonpath='{.status.currentReplicas}')"
    if (( bff_replicas > peak_bff )); then peak_bff="$bff_replicas"; fi
    if (( core_replicas > peak_core )); then peak_core="$core_replicas"; fi
    printf 'Capacity sample: BFF=%s Core=%s\n' "$bff_replicas" "$core_replicas"
  done
  if ! wait "$load_pid"; then
    cat "$stage_log" >&2
    printf 'Playwright failed before a valid capacity measurement.\n' >&2
    exit 1
  fi
  load_pid=
  cat "$stage_log"
  result_json="$(sed -n 's/.*K3D_MIXED_RESULT //p' "$stage_log" | tail -n 1)"
  if [[ -z "$result_json" ]]; then
    printf 'No K3D_MIXED_RESULT was reported.\n' >&2
    exit 1
  fi
  if stage_summary="$(node -e '
    const result = JSON.parse(process.argv[1])
    const failureCount = [result.read, result.write].reduce((total, operation) =>
      total + operation.transportErrors + Object.entries(operation.statuses)
        .filter(([status]) => status !== "200")
        .reduce((count, [, value]) => count + value, 0), 0)
    const total = result.read.requests + result.write.requests + result.read.transportErrors + result.write.transportErrors
    const errorRate = total ? failureCount / total : 1
    console.log(`clients=${result.clients} rps=${result.requestsPerSecond} read_p95=${result.read.latencyMs?.p95}ms write_p95=${result.write.latencyMs?.p95}ms errors=${failureCount}/${total} rate=${(errorRate * 100).toFixed(2)}%`)
    process.exit(result.read.requests > 0 && result.write.requests > 0 && errorRate < 0.01 &&
      result.read.latencyMs.p95 <= 500 && result.write.latencyMs.p95 <= 500 ? 0 : 1)
  ' "$result_json")"; then
    last_passing_clients="$clients"
    last_passing_rps="$(node -e 'console.log(JSON.parse(process.argv[1]).requestsPerSecond)' "$result_json")"
    printf 'LAB SLO PASS: %s. Peak replicas: BFF=%s Core=%s.\n' "$stage_summary" "$peak_bff" "$peak_core"
  else
    first_failing_clients="$clients"
    printf 'LAB SLO BREACH: %s. Peak replicas: BFF=%s Core=%s.\n' "$stage_summary" "$peak_bff" "$peak_core"
    break
  fi
  rm -f -- "$stage_log"
  stage_log=
done

if [[ -n "$first_failing_clients" ]]; then
  if [[ -n "$last_passing_clients" ]]; then
    printf 'Measured lab boundary: %s clients passed at %s requests/s; %s clients breached the <1%% error and <=500ms p95 criteria.\n' "$last_passing_clients" "$last_passing_rps" "$first_failing_clients"
  else
    printf 'The first stage (%s clients) breached the lab target; no passing boundary was established. Warm-up or a lighter stage may be needed.\n' "$first_failing_clients"
  fi
else
  printf 'No boundary reached. Highest tested passing stage: %s clients at %s requests/s. This is a lower bound, not a maximum.\n' "$last_passing_clients" "$last_passing_rps"
fi
