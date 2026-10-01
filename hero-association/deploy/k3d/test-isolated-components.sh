#!/usr/bin/env bash
set -euo pipefail
umask 077

script_dir="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")" && pwd)"
project_dir="$(cd -- "$script_dir/../.." && pwd)"
kubeconfig="${HERO_ASSOCIATION_K3D_KUBECONFIG:-$script_dir/.kubeconfig}"
namespace=hero-association-components
if [[ $# -gt 1 ]]; then
  printf 'Usage: %s [core|bff|expedition|all]\n' "$0" >&2
  exit 2
fi
component="${1:-all}"
case "$component" in
  core|bff|expedition|all) ;;
  *) printf 'Unknown component: %s\n' "$component" >&2; exit 2 ;;
esac
selected() {
  [[ "$component" == all || "$component" == "$1" ]]
}
temporary_directory="$(mktemp -d /tmp/hero-association-components.XXXXXXXX)"
created=false
forward_pids=()

kc() {
  kubectl --kubeconfig "$kubeconfig" "$@"
}

cleanup() {
  status=$?
  for pid in "${forward_pids[@]}"; do
    kill "$pid" 2>/dev/null || true
    wait "$pid" 2>/dev/null || true
  done
  if [[ "$created" == true ]]; then
    if ! kc delete namespace "$namespace" --wait=true --timeout=5m >&2; then
      printf 'Remove leftover component-test namespace manually: %s\n' "$namespace" >&2
      status=1
    fi
  fi
  rm -rf -- "$temporary_directory"
  exit "$status"
}
trap cleanup EXIT

if [[ ! -f "$kubeconfig" || "$(kc config current-context)" != k3d-hero-association ]]; then
  printf 'This test requires the k3d-hero-association kubeconfig.\n' >&2
  exit 1
fi
if kc get namespace "$namespace" >/dev/null 2>&1; then
  printf '%s already exists; inspect it before retrying.\n' "$namespace" >&2
  exit 1
fi

for key in CORE_DATABASE_PASSWORD \
  HERO_ASSOCIATION_EXPEDITION_RABBITMQ_PASSWORD \
  HERO_ASSOCIATION_EXPEDITION_WORKER_RABBITMQ_PASSWORD \
  HERO_ASSOCIATION_CORE_SETTLEMENT_RABBITMQ_PASSWORD; do
  openssl rand -hex -out "$temporary_directory/$key" 32
  perl -pi -e 'chomp' "$temporary_directory/$key"
done
kc create namespace "$namespace"
created=true
kc -n "$namespace" create secret generic hero-association-k3d-credentials \
  --from-file="$temporary_directory/CORE_DATABASE_PASSWORD"
kc -n "$namespace" create secret generic hero-association-expedition-credentials \
  --from-file="$temporary_directory/HERO_ASSOCIATION_EXPEDITION_RABBITMQ_PASSWORD" \
  --from-file="$temporary_directory/HERO_ASSOCIATION_EXPEDITION_WORKER_RABBITMQ_PASSWORD" \
  --from-file="$temporary_directory/HERO_ASSOCIATION_CORE_SETTLEMENT_RABBITMQ_PASSWORD"
kc -n "$namespace" create configmap hero-association-expedition-rabbitmq-setup \
  --from-file=prepare-expedition.mjs="$project_dir/backend/rabbitmq/prepare-expedition.mjs"
kubectl kustomize --load-restrictor=LoadRestrictionsNone \
  "$project_dir/deploy/k8s/component-tests" | kc apply -f -
for workload in deployment/postgres-core deployment/redis-core deployment/redis-bff \
  statefulset/rabbitmq-expedition statefulset/redis-expedition; do
  kc -n "$namespace" rollout status "$workload" --timeout=5m
done

rabbit_job="$(kc create -f "$project_dir/deploy/k8s/expedition/rabbitmq-setup-job.yaml" \
  --dry-run=client -o json | node -e '
    let input = "";
    process.stdin.on("data", chunk => input += chunk);
    process.stdin.on("end", () => {
      const job = JSON.parse(input);
      job.metadata.namespace = "hero-association-components";
      process.stdout.write(JSON.stringify(job));
    });
  ' | kc create -f - -o jsonpath='{.metadata.name}')"
kc -n "$namespace" wait --for=condition=complete "job/$rabbit_job" --timeout=5m

forward() {
  local name="$1" service="$2" target_port="$3" local_port="" pid=""
  kubectl --kubeconfig "$kubeconfig" -n "$namespace" port-forward --address 127.0.0.1 \
    "service/$service" ":$target_port" \
    > "$temporary_directory/$name.port-forward.log" 2>&1 &
  pid=$!
  forward_pids+=("$pid")
  for attempt in {1..50}; do
    local_port="$(sed -nE \
      "s/^Forwarding from 127[.]0[.]0[.]1:([0-9]+) -> $target_port$/\\1/p" \
      "$temporary_directory/$name.port-forward.log" | head -1)"
    [[ -n "$local_port" ]] && break
    if ! kill -0 "$pid" 2>/dev/null; then break; fi
    sleep 0.2
  done
  if [[ -z "$local_port" ]]; then
    sed -n '1,30p' "$temporary_directory/$name.port-forward.log" >&2
    printf 'Could not forward disposable %s.\n' "$service" >&2
    exit 1
  fi
  forwarded_port="$local_port"
}

forward postgres postgres-core 5432
postgres_port="$forwarded_port"
forward core-redis redis-core 6379
core_redis_port="$forwarded_port"
forward bff-redis redis-bff 6379
bff_redis_port="$forwarded_port"
forward expedition-redis redis-expedition 6379
expedition_redis_port="$forwarded_port"
forward rabbit rabbitmq-expedition 5672
rabbit_port="$forwarded_port"

(
  export HERO_ASSOCIATION_COMPONENT_RABBIT_PORT="$rabbit_port"
  export HERO_ASSOCIATION_COMPONENT_RABBIT_ADMIN_PASSWORD="$( < "$temporary_directory/HERO_ASSOCIATION_EXPEDITION_RABBITMQ_PASSWORD")"
  export HERO_ASSOCIATION_COMPONENT_RABBIT_WORKER_PASSWORD="$( < "$temporary_directory/HERO_ASSOCIATION_EXPEDITION_WORKER_RABBITMQ_PASSWORD")"
  export HERO_ASSOCIATION_COMPONENT_RABBIT_CORE_PASSWORD="$( < "$temporary_directory/HERO_ASSOCIATION_CORE_SETTLEMENT_RABBITMQ_PASSWORD")"
  if selected core; then
  "$project_dir/backend/mvnw" -f "$project_dir/backend/pom.xml" \
    -pl hero-association-core -am \
    -Dtest=K3dExpeditionAppliedPublisherIT -Dsurefire.failIfNoSpecifiedTests=false test
  fi
  if selected expedition; then
  "$project_dir/backend/mvnw" -f "$project_dir/backend/pom.xml" \
    -pl hero-association-expedition -am \
    -Dtest=K3dRabbitSettlementTransportIT -Dsurefire.failIfNoSpecifiedTests=false test
  fi
)

if selected core; then
QUARKUS_DATASOURCE_JDBC_URL="jdbc:postgresql://127.0.0.1:$postgres_port/hero_association" \
  QUARKUS_DATASOURCE_USERNAME=hero_association \
  QUARKUS_DATASOURCE_PASSWORD="$(< "$temporary_directory/CORE_DATABASE_PASSWORD")" \
  QUARKUS_DATASOURCE_DEVSERVICES_ENABLED=false \
  QUARKUS_REDIS_HOSTS="redis://127.0.0.1:$core_redis_port" \
  QUARKUS_REDIS_DEVSERVICES_ENABLED=false \
  "$project_dir/backend/mvnw" -f "$project_dir/backend/pom.xml" \
    -pl hero-association-core -am \
    '-Dtest=*Test,!ExpeditionAppliedPublisherTest' \
    -Dsurefire.failIfNoSpecifiedTests=false test
fi

if selected bff; then
QUARKUS_REDIS_HOSTS="redis://127.0.0.1:$bff_redis_port" \
  QUARKUS_REDIS_DEVSERVICES_ENABLED=false \
  "$project_dir/backend/mvnw" -f "$project_dir/backend/pom.xml" \
    -pl hero-association-bff -am test
fi

if selected expedition; then
QUARKUS_REDIS_HOSTS="redis://127.0.0.1:$expedition_redis_port" \
  QUARKUS_REDIS_DEVSERVICES_ENABLED=false \
  "$project_dir/backend/mvnw" -f "$project_dir/backend/pom.xml" \
    -pl hero-association-expedition -am \
    '-Dtest=*Test,!RabbitSettlementTransportTest' \
    -Dsurefire.failIfNoSpecifiedTests=false test
fi

printf '%s component tests passed against disposable k3d resources.\n' "$component"
