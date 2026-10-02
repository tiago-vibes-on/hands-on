#!/usr/bin/env bash
set -euo pipefail
set -m

script_dir="$(cd -- "$(dirname -- "$0")" && pwd)"
project_dir="$(cd -- "$script_dir/../.." && pwd)"
backend_dir="$project_dir/backend"
namespace=hero-association
export KUBECONFIG="$script_dir/.kubeconfig"
state_dir="$script_dir/.hybrid-state"

usage() {
  printf 'Usage: %s <run|restore|status> <core|bff|expedition|market|assets|frontend>\n' "$0" >&2
  exit 2
}

[[ $# -eq 2 ]] || usage
action="$1"
service="$2"
case "$action" in run|restore|status) ;; *) usage ;; esac
case "$service" in
  core) host_port=17081; service_port=8081; ready_path=/q/health/ready; service_account=hero-association-core; istio=true ;;
  bff) host_port=17080; service_port=8080; ready_path=/q/health/ready; service_account=hero-association-bff; istio=true ;;
  expedition) host_port=17083; service_port=8083; ready_path=/q/health/ready; service_account=hero-association-expedition; istio=true ;;
  market) host_port=17084; service_port=8084; ready_path=/q/health/ready; service_account=hero-association-market; istio=true ;;
  assets) host_port=17085; service_port=8085; ready_path=/q/health/ready; service_account=hero-association-assets; istio=true ;;
  frontend) host_port=15172; service_port=80; ready_path=/; service_account=default; istio=false ;;
  *) usage ;;
esac

[[ -f "$KUBECONFIG" ]] || { printf 'Missing isolated k3d kubeconfig.\n' >&2; exit 1; }
[[ "$(kubectl config current-context)" == k3d-hero-association ]] ||
  { printf 'Refusing to change a cluster other than k3d-hero-association.\n' >&2; exit 1; }

state_file="$state_dir/$service.state"
hpa_file="$state_dir/$service.hpa.json"
app_pid=
forward_pids=

port_listening() {
  ss -H -ltn "( sport = :$1 )" | grep -q .
}

wait_port() {
  local port="$1"
  local attempt
  for attempt in $(seq 1 20); do
    if port_listening "$port"; then return 0; fi
    sleep 1
  done
  printf 'Local port %s did not become available.\n' "$port" >&2
  return 1
}

forward_loop() {
  local target_namespace="$1" target="$2" mapping="$3" forward_child=
  trap 'if [[ -n "$forward_child" ]]; then kill "$forward_child" 2>/dev/null || true; wait "$forward_child" 2>/dev/null || true; fi; exit 0' TERM INT
  while :; do
    kubectl -n "$target_namespace" port-forward --address 127.0.0.1 "$target" "$mapping" &
    forward_child="$!"
    wait "$forward_child" || true
    forward_child=
    sleep 1
  done
}

start_forward() {
  local target_namespace="$1" target="$2" local_port="$3" remote_port="$4"
  if port_listening "$local_port"; then
    printf 'Port %s is already in use; stop the conflicting process first.\n' "$local_port" >&2
    return 1
  fi
  forward_loop "$target_namespace" "$target" "$local_port:$remote_port" >/dev/null 2>&1 &
  forward_pids="$forward_pids $!"
  wait_port "$local_port"
}

stop_forwards() {
  local pid
  for pid in $forward_pids; do
    kill "$pid" 2>/dev/null || true
    wait "$pid" 2>/dev/null || true
  done
}

secret_value() {
  local secret="$1" key="$2"
  kubectl -n "$namespace" get secret "$secret" -o "jsonpath={.data.$key}" | base64 -d
}

process_start_time() {
  local pid="$1"
  [[ "$pid" =~ ^[0-9]+$ && -r "/proc/$pid/stat" ]] || return 1
  sed -E 's/^.*\) //' "/proc/$pid/stat" | awk '{print $20}'
}

process_matches() {
  local pid="$1" expected_start="$2" actual_start
  [[ "$pid" =~ ^[0-9]+$ && "$expected_start" =~ ^[0-9]+$ && -r "/proc/$pid/stat" ]] || return 1
  actual_start="$(process_start_time "$pid")"
  [[ "$actual_start" == "$expected_start" ]]
}

stop_saved_forwards() {
  local entry pid start
  [[ -f "$state_file" ]] || return 0
  for entry in $(sed -n '2p' "$state_file"); do
    [[ "$entry" =~ ^[0-9]+:[0-9]+$ ]] || continue
    pid="${entry%%:*}"
    start="${entry#*:}"
    if process_matches "$pid" "$start"; then
      kill "$pid" 2>/dev/null || true
    fi
  done
}

stop_saved_app() {
  local entry pid start pgid
  [[ -f "$state_file" ]] || return 0
  entry="$(sed -n '3p' "$state_file")"
  [[ "$entry" =~ ^[0-9]+:[0-9]+$ ]] || return 0
  pid="${entry%%:*}"
  start="${entry#*:}"
  process_matches "$pid" "$start" || return 0
  pgid="$(ps -o pgid= -p "$pid" | tr -d ' ')"
  [[ "$pgid" == "$pid" ]] ||
    { printf 'Saved host process %s has an unexpected process group. Stop it manually.\n' "$pid" >&2; return 1; }
  kill -- "-$pid" 2>/dev/null || true
  for attempt in $(seq 1 15); do
    if ! port_listening "$host_port"; then return 0; fi
    sleep 1
  done
  printf 'Saved host process %s did not stop; refusing to start a second %s.\n' "$pid" "$service" >&2
  return 1
}

restore_cluster() {
  local replicas
  [[ -f "$state_file" ]] || { printf 'No saved %s state to restore.\n' "$service" >&2; return 1; }
  replicas="$(sed -n '1p' "$state_file")"
  [[ "$replicas" =~ ^[0-9]+$ && "$replicas" -gt 0 ]] ||
    { printf 'Invalid saved replica count for %s.\n' "$service" >&2; return 1; }
  kubectl -n "$namespace" scale "deployment/$service" --replicas="$replicas" || return 1
  kubectl -n "$namespace" rollout status "deployment/$service" --timeout=5m || return 1
  kubectl -n "$namespace" patch "service/$service" --type=merge \
    -p '{"spec":{"selector":{"hybrid":null}}}' || return 1
  kubectl -n "$namespace" delete "deployment/$service-hybrid-bridge" \
    "configmap/$service-hybrid-bridge" --ignore-not-found=true || return 1
  if [[ -f "$hpa_file" ]]; then
    kubectl apply -f "$hpa_file" || return 1
    rm -- "$hpa_file"
  fi
  stop_saved_forwards
  rm -- "$state_file"
  printf 'Restored %s to %s k3d replicas and its original Service route.\n' "$service" "$replicas"
}

if [[ "$action" == status ]]; then
  kubectl -n "$namespace" get "deployment/$service" "service/$service"
  kubectl -n "$namespace" get "hpa/$service" --ignore-not-found=true
  if [[ -f "$state_file" ]]; then
    printf '%s has saved hybrid state; run %s restore %s to stop its saved host process and recover k3d.\n' "$service" "$0" "$service"
  fi
  exit 0
fi

if [[ "$action" == restore ]]; then
  stop_saved_app
  if port_listening "$host_port"; then
    printf 'Port %s is still listening. Stop the host %s process before restoring k3d.\n' "$host_port" "$service" >&2
    exit 1
  fi
  restore_cluster
  exit 0
fi

[[ ! -e "$state_file" ]] ||
  { printf '%s is already in hybrid state; restore it first.\n' "$service" >&2; exit 1; }
[[ "$(kubectl -n "$namespace" get "service/$service" -o jsonpath='{.spec.selector.app}')" == "$service" ]] ||
  { printf '%s Service has an unexpected selector.\n' "$service" >&2; exit 1; }
[[ -z "$(kubectl -n "$namespace" get "service/$service" -o jsonpath='{.spec.selector.hybrid}')" ]] ||
  { printf '%s Service is already routed to a hybrid endpoint.\n' "$service" >&2; exit 1; }
replicas="$(kubectl -n "$namespace" get "deployment/$service" -o jsonpath='{.spec.replicas}')"
[[ "$replicas" =~ ^[0-9]+$ && "$replicas" -gt 0 ]] ||
  { printf '%s must have ready k3d replicas before switching.\n' "$service" >&2; exit 1; }
kubectl -n "$namespace" rollout status "deployment/$service" --timeout=2m

wsl_ip="$(ip -4 route get 1.1.1.1 | awk '{for (i=1; i<=NF; i++) if ($i=="src") {print $(i+1); exit}}')"
[[ "$wsl_ip" =~ ^[0-9]+\.[0-9]+\.[0-9]+\.[0-9]+$ ]] ||
  { printf 'Could not determine the private WSL IPv4 address.\n' >&2; exit 1; }
if port_listening "$host_port"; then
  printf 'Host port %s is already in use.\n' "$host_port" >&2
  exit 1
fi

if [[ "$service" == core || "$service" == expedition || "$service" == market || "$service" == assets ]]; then
  (cd "$backend_dir" && ./mvnw --batch-mode -pl hero-association-lib/combat-engine -am install)
fi
if [[ "$service" == frontend && ! -d "$project_dir/frontend/node_modules" ]]; then
  (cd "$project_dir/frontend" && npm ci)
fi

cleanup() {
  local result="$?"
  trap - EXIT
  if [[ -n "$app_pid" ]] && kill -0 "$app_pid" 2>/dev/null; then
    kill -- "-$app_pid" 2>/dev/null || true
    wait "$app_pid" 2>/dev/null || true
  fi
  if [[ -f "$state_file" ]]; then
    for attempt in $(seq 1 15); do
      if ! port_listening "$host_port"; then break; fi
      sleep 1
    done
    if port_listening "$host_port"; then
      printf 'Host %s still listens on %s; not starting a second copy. Stop it and run %s restore %s.\n' \
        "$service" "$host_port" "$0" "$service" >&2
      result=1
    elif ! restore_cluster; then
      printf 'Automatic restore failed. Inspect state, then run %s restore %s.\n' "$0" "$service" >&2
      result=1
    fi
  fi
  stop_forwards
  exit "$result"
}
trap cleanup EXIT
trap 'exit 130' INT
trap 'exit 143' TERM

if [[ "$service" != frontend ]]; then
  # The cluster already provides observability and other backing services.
  # Do not let Quarkus dev mode launch an unrelated LGTM/Testcontainers stack.
  export QUARKUS_OBSERVABILITY_ENABLED=false
  export QUARKUS_DEVSERVICES_ENABLED=false
  case "$service" in
    core) keycloak_port=17181; otlp_port=14317 ;;
    market) keycloak_port=17184; otlp_port=14347 ;;
    assets) keycloak_port=17185; otlp_port=14348 ;;
    bff) keycloak_port=17180; otlp_port=14327 ;;
    expedition) keycloak_port=17182; otlp_port=14337 ;;
  esac
  start_forward "$namespace" service/keycloak "$keycloak_port" 8080
  start_forward hero-association-observability service/otel-lgtm "$otlp_port" 4317
  export HERO_ASSOCIATION_OIDC_AUTH_SERVER_URL="http://127.0.0.1:$keycloak_port/realms/hero-association"
  export HERO_ASSOCIATION_OIDC_ISSUER=https://auth.heroassociation.test/realms/hero-association
  export HERO_ASSOCIATION_OTLP_ENDPOINT="http://127.0.0.1:$otlp_port"
  export HERO_ASSOCIATION_OTEL_DISABLED=false
fi

case "$service" in
  core)
    start_forward "$namespace" service/postgres-core 15432 5432
    start_forward "$namespace" service/redis-core 16380 6379
    start_forward "$namespace" service/rabbitmq-expedition 15675 5672
    export QUARKUS_DATASOURCE_JDBC_URL=jdbc:postgresql://127.0.0.1:15432/hero_association
    export QUARKUS_DATASOURCE_USERNAME=hero_association
    export QUARKUS_DATASOURCE_PASSWORD="$(secret_value hero-association-k3d-credentials CORE_DATABASE_PASSWORD)"
    export QUARKUS_HIBERNATE_ORM_SCHEMA_MANAGEMENT_STRATEGY=validate
    export QUARKUS_HIBERNATE_ORM_SQL_LOAD_SCRIPT=no-file
    start_forward "$namespace" service/assets 18087 8085
    export HERO_ASSOCIATION_ASSETS_BASE_URL=http://127.0.0.1:18087
    export HERO_ASSOCIATION_ASSETS_CORE_SERVICE_KEY="$(secret_value hero-association-assets-credentials HERO_ASSOCIATION_ASSETS_CORE_SERVICE_KEY)"
    export HERO_ASSOCIATION_CORE_BOOTSTRAP_MODE=false
    export HERO_ASSOCIATION_CORE_EXPEDITION_ENABLED=true
    export HERO_ASSOCIATION_EXPEDITION_RABBITMQ_HOST=127.0.0.1
    export HERO_ASSOCIATION_EXPEDITION_RABBITMQ_HOST_PORT=15675
    export HERO_ASSOCIATION_EXPEDITION_CORE_SERVICE_KEY="$(secret_value hero-association-expedition-credentials HERO_ASSOCIATION_EXPEDITION_CORE_SERVICE_KEY)"
    export HERO_ASSOCIATION_CORE_SETTLEMENT_RABBITMQ_PASSWORD="$(secret_value hero-association-expedition-credentials HERO_ASSOCIATION_CORE_SETTLEMENT_RABBITMQ_PASSWORD)"
    ;;
  bff)
    start_forward "$namespace" service/redis-bff 16379 6379
    start_forward "$namespace" service/core 18081 8081
    start_forward "$namespace" service/expedition 18083 8083
    start_forward "$namespace" service/market 18085 8084
    start_forward "$namespace" service/assets 18086 8085
    export HERO_ASSOCIATION_ASSETS_BASE_URL=http://127.0.0.1:18086
    export HERO_ASSOCIATION_MARKET_BASE_URL=http://127.0.0.1:18085
    export HERO_ASSOCIATION_CORE_BASE_URL=http://127.0.0.1:18081
    export HERO_ASSOCIATION_EXPEDITION_BASE_URL=http://127.0.0.1:18083
    export HERO_ASSOCIATION_EXPEDITION_WEBSOCKET_ENABLED=true
    export HERO_ASSOCIATION_EDGE_AUTH_ENABLED=true
    export HERO_ASSOCIATION_BFF_COOKIE_FORCE_SECURE=true
    export HERO_ASSOCIATION_BFF_OIDC_CLIENT_SECRET="$(secret_value hero-association-k3d-credentials HERO_ASSOCIATION_BFF_OIDC_CLIENT_SECRET)"
    export HERO_ASSOCIATION_BFF_OIDC_STATE_SECRET="$(secret_value hero-association-k3d-credentials HERO_ASSOCIATION_BFF_OIDC_STATE_SECRET)"
    export HERO_ASSOCIATION_BFF_CSRF_TOKEN_SIGNATURE_KEY="$(secret_value hero-association-k3d-credentials HERO_ASSOCIATION_BFF_CSRF_TOKEN_SIGNATURE_KEY)"
    export HERO_ASSOCIATION_EXPEDITION_BFF_SERVICE_KEY="$(secret_value hero-association-expedition-credentials HERO_ASSOCIATION_EXPEDITION_BFF_SERVICE_KEY)"
    ;;
  assets)
    start_forward "$namespace" service/postgres-assets 15435 5432
    start_forward "$namespace" service/core 18088 8081
    export QUARKUS_DATASOURCE_JDBC_URL=jdbc:postgresql://127.0.0.1:15435/hero_association_assets
    export QUARKUS_DATASOURCE_USERNAME=hero_association_assets
    export QUARKUS_DATASOURCE_PASSWORD="$(secret_value hero-association-assets-credentials ASSETS_DATABASE_PASSWORD)"
    export QUARKUS_HIBERNATE_ORM_SCHEMA_MANAGEMENT_STRATEGY=validate
    export QUARKUS_HIBERNATE_ORM_SQL_LOAD_SCRIPT=no-file
    export HERO_ASSOCIATION_CORE_BASE_URL=http://127.0.0.1:18088
    export HERO_ASSOCIATION_ASSETS_CORE_SERVICE_KEY="$(secret_value hero-association-assets-credentials HERO_ASSOCIATION_ASSETS_CORE_SERVICE_KEY)"
    export HERO_ASSOCIATION_ASSETS_MARKET_SERVICE_KEY="$(secret_value hero-association-market-credentials HERO_ASSOCIATION_ASSETS_MARKET_SERVICE_KEY)"
    ;;
  market)
    start_forward "$namespace" service/postgres-market 15434 5432
    start_forward "$namespace" service/assets 18084 8085
    export QUARKUS_DATASOURCE_JDBC_URL=jdbc:postgresql://127.0.0.1:15434/hero_association_market
    export QUARKUS_DATASOURCE_USERNAME=hero_association_market
    export QUARKUS_DATASOURCE_PASSWORD="$(secret_value hero-association-market-credentials MARKET_DATABASE_PASSWORD)"
    export QUARKUS_HIBERNATE_ORM_SCHEMA_MANAGEMENT_STRATEGY=validate
    export QUARKUS_HIBERNATE_ORM_SQL_LOAD_SCRIPT=no-file
    export HERO_ASSOCIATION_ASSETS_BASE_URL=http://127.0.0.1:18084
    export HERO_ASSOCIATION_ASSETS_MARKET_SERVICE_KEY="$(secret_value hero-association-market-credentials HERO_ASSOCIATION_ASSETS_MARKET_SERVICE_KEY)"
    ;;
  expedition)
    start_forward "$namespace" service/redis-expedition 16381 6379
    start_forward "$namespace" service/rabbitmq-expedition 15676 5672
    start_forward "$namespace" service/core 18082 8081
    export HERO_ASSOCIATION_EXPEDITION_REDIS_HOST_PORT=16381
    export HERO_ASSOCIATION_EXPEDITION_RABBITMQ_HOST=127.0.0.1
    export HERO_ASSOCIATION_EXPEDITION_RABBITMQ_HOST_PORT=15676
    export HERO_ASSOCIATION_CORE_BASE_URL=http://127.0.0.1:18082
    export HERO_ASSOCIATION_EXPEDITION_WORKER_ENABLED=true
    export HERO_ASSOCIATION_EXPEDITION_PLAYER_API_ENABLED=true
    export HERO_ASSOCIATION_EXPEDITION_VISUAL_API_ENABLED=true
    export HERO_ASSOCIATION_EXPEDITION_SETTLEMENT_ENABLED=true
    export HERO_ASSOCIATION_EXPEDITION_ADMISSION_RECONCILER_ENABLED=true
    export HERO_ASSOCIATION_EXPEDITION_WORKER_RABBITMQ_PASSWORD="$(secret_value hero-association-expedition-credentials HERO_ASSOCIATION_EXPEDITION_WORKER_RABBITMQ_PASSWORD)"
    export HERO_ASSOCIATION_EXPEDITION_CORE_SERVICE_KEY="$(secret_value hero-association-expedition-credentials HERO_ASSOCIATION_EXPEDITION_CORE_SERVICE_KEY)"
    export HERO_ASSOCIATION_EXPEDITION_BFF_SERVICE_KEY="$(secret_value hero-association-expedition-credentials HERO_ASSOCIATION_EXPEDITION_BFF_SERVICE_KEY)"
    ;;
  frontend)
    export VITE_EXPEDITION_ENABLED=true
    ;;
esac

umask 077
mkdir -p "$state_dir"
saved_forward_pids=
for pid in $forward_pids; do
  saved_forward_pids="$saved_forward_pids $pid:$(process_start_time "$pid")"
done
printf '%s\n%s\n' "$replicas" "$saved_forward_pids" > "$state_file"
if kubectl -n "$namespace" get "hpa/$service" >/dev/null 2>&1; then
  kubectl -n "$namespace" get "hpa/$service" -o json |
    node -e "let s='';process.stdin.on('data',c=>s+=c);process.stdin.on('end',()=>{const h=JSON.parse(s);process.stdout.write(JSON.stringify({apiVersion:h.apiVersion,kind:h.kind,metadata:{name:h.metadata.name,namespace:h.metadata.namespace},spec:h.spec})+'\n')})" > "$hpa_file"
  kubectl -n "$namespace" delete "hpa/$service"
fi
kubectl -n "$namespace" scale "deployment/$service" --replicas=0
kubectl -n "$namespace" wait --for=delete pod -l "app=$service" --timeout=120s

run_app() {
  case "$service" in
    core)
      cd "$backend_dir/hero-association-core"
      exec ./mvnw quarkus:dev -Dquarkus.http.host="$wsl_ip" -Dquarkus.http.port="$host_port" \
        -Dquarkus.hibernate-orm.schema-management.strategy=validate \
        -Dquarkus.hibernate-orm.sql-load-script=no-file
      ;;
    bff)
      cd "$backend_dir/hero-association-bff"
      exec ./mvnw quarkus:dev -Dquarkus.http.host="$wsl_ip" -Dquarkus.http.port="$host_port"
      ;;
    expedition|market|assets)
      cd "$backend_dir/hero-association-$service"
      exec ../mvnw quarkus:dev -Dquarkus.http.host="$wsl_ip" -Dquarkus.http.port="$host_port"
      ;;
    frontend)
      cd "$project_dir/frontend"
      exec npm run dev -- --host "$wsl_ip"
      ;;
  esac
}
run_app </dev/null &
app_pid="$!"
printf '%s:%s\n' "$app_pid" "$(process_start_time "$app_pid")" >> "$state_file"
ready=false
for attempt in $(seq 1 180); do
  if curl --silent --show-error --fail --max-time 2 "http://$wsl_ip:$host_port$ready_path" >/dev/null 2>&1; then
    ready=true
    break
  fi
  if ! kill -0 "$app_pid" 2>/dev/null; then
    printf 'Host %s exited before becoming ready.\n' "$service" >&2
    wait "$app_pid" || true
    exit 1
  fi
  sleep 1
done
[[ "$ready" == true ]] || { printf 'Host %s did not become ready.\n' "$service" >&2; exit 1; }

sed -e "s/__SERVICE__/$service/g" \
    -e "s/__SERVICE_PORT__/$service_port/g" \
    -e "s/__WSL_HOST_IP__/$wsl_ip/g" \
    -e "s/__WSL_PORT__/$host_port/g" \
    -e "s@__READY_PATH__@$ready_path@g" \
    -e "s/__SERVICE_ACCOUNT__/$service_account/g" \
    -e "s/__ISTIO_INJECT__/$istio/g" \
    "$script_dir/../k8s/hybrid/bridge.yaml" | kubectl apply -f -
kubectl -n "$namespace" rollout status "deployment/$service-hybrid-bridge" --timeout=3m
kubectl -n "$namespace" patch "service/$service" --type=merge \
  -p "{\"spec\":{\"selector\":{\"app\":\"$service\",\"hybrid\":\"wsl\"}}}"
printf 'Host %s is live through Envoy at https://heroassociation.test. Press Ctrl-C to restore k3d.\n' "$service"
wait "$app_pid"
