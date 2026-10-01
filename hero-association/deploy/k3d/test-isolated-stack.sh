#!/usr/bin/env bash
set -euo pipefail
umask 077

script_dir="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")" && pwd)"
project_dir="$(cd -- "$script_dir/../.." && pwd)"
kubeconfig="${HERO_ASSOCIATION_K3D_KUBECONFIG:-$script_dir/.kubeconfig}"
ca_certificate="${HERO_ASSOCIATION_LOCAL_CA_CERTIFICATE:-$project_dir/tls/certs/local-ca.crt}"
namespace=hero-association-e2e
if [[ $# -gt 1 ]]; then
  printf 'Usage: %s [FOUR_IMAGE_ARCHIVE_DIRECTORY]\n' "$0" >&2
  exit 2
fi
temporary_directory="$(mktemp -d /tmp/hero-association-e2e.XXXXXXXX)"
created=false
rabbit_forward_pid=""
archive_directory="${1:-}"
candidate_file="$temporary_directory/candidate.json"

kc() {
  kubectl --kubeconfig "$kubeconfig" "$@"
}

cleanup() {
  status=$?
  if [[ -n "$rabbit_forward_pid" ]]; then
    kill "$rabbit_forward_pid" 2>/dev/null || true
    wait "$rabbit_forward_pid" 2>/dev/null || true
  fi
  if [[ "$created" == true ]]; then
    if [[ "$status" -ne 0 ]]; then
      kc -n "$namespace" get pods -o wide >&2 || true
      kc -n "$namespace" logs -l app=bff -c bff \
        --tail=80 --prefix=true >&2 || true
    fi
    kc delete namespace "$namespace" --wait=true --timeout=5m >&2 ||
      printf 'Remove leftover E2E namespace manually: %s\n' "$namespace" >&2
  fi
  rm -rf -- "$temporary_directory"
}
trap cleanup EXIT

if [[ ! -f "$kubeconfig" || "$ca_certificate" != /* || ! -s "$ca_certificate" ]]; then
  printf 'Missing k3d kubeconfig or absolute path to a readable local CA certificate.\n' >&2
  exit 1
fi
if [[ "$(kc config current-context)" != 'k3d-hero-association' ]]; then
  printf 'Refusing to use a Kubernetes context other than k3d-hero-association.\n' >&2
  exit 1
fi
if kc get namespace "$namespace" >/dev/null 2>&1; then
  printf '%s already exists. Inspect it and remove it explicitly before retrying.\n' "$namespace" >&2
  exit 1
fi
if [[ -n "$archive_directory" ]]; then
  node "$project_dir/e2e/k3d-candidate-archive.mjs" prepare \
    "$candidate_file" "$archive_directory"
  core_image="$(node -p 'JSON.parse(require("fs").readFileSync(process.argv[1], "utf8")).images.core.ref' "$candidate_file")"
  bff_image="$(node -p 'JSON.parse(require("fs").readFileSync(process.argv[1], "utf8")).images.bff.ref' "$candidate_file")"
  expedition_image="$(node -p 'JSON.parse(require("fs").readFileSync(process.argv[1], "utf8")).images.expedition.ref' "$candidate_file")"
  frontend_image="$(node -p 'JSON.parse(require("fs").readFileSync(process.argv[1], "utf8")).images.frontend.ref' "$candidate_file")"
  k3d_binary="${K3D_BIN:-$script_dir/.tools/k3d}"
  "$k3d_binary" image import "$core_image" "$bff_image" \
    "$expedition_image" "$frontend_image" --cluster hero-association
else
  for service in core bff expedition frontend; do
    image="$(kc -n hero-association get deployment "$service" \
      -o jsonpath='{.spec.template.spec.containers[0].image}')"
    if [[ -z "$image" ]]; then
      printf 'No image configured for daily %s Deployment.\n' "$service" >&2
      exit 1
    fi
    case "$service" in
      core) core_image="$image" ;;
      bff) bff_image="$image" ;;
      expedition) expedition_image="$image" ;;
      frontend) frontend_image="$image" ;;
    esac
  done
fi


for key in CORE_DATABASE_PASSWORD KEYCLOAK_DATABASE_PASSWORD \
  KEYCLOAK_ADMIN_PASSWORD HERO_ASSOCIATION_BFF_OIDC_CLIENT_SECRET \
  HERO_ASSOCIATION_BFF_OIDC_STATE_SECRET HERO_ASSOCIATION_BFF_CSRF_TOKEN_SIGNATURE_KEY \
  HERO_ASSOCIATION_EXPEDITION_RABBITMQ_PASSWORD \
  HERO_ASSOCIATION_EXPEDITION_WORKER_RABBITMQ_PASSWORD \
  HERO_ASSOCIATION_CORE_SETTLEMENT_RABBITMQ_PASSWORD \
  HERO_ASSOCIATION_EXPEDITION_CORE_SERVICE_KEY \
  HERO_ASSOCIATION_EXPEDITION_BFF_SERVICE_KEY; do
  openssl rand -hex -out "$temporary_directory/$key" 32
  perl -pi -e 'chomp' "$temporary_directory/$key"
done

node "$script_dir/generate-realm.mjs" \
  "$project_dir/backend/keycloak/realm/hero-association-realm.json" \
  "$temporary_directory/hero-association-realm.json" \
  https://app.e2e.heroassociation.test
node -e '
  const fs = require("node:fs");
  const file = process.argv[1];
  const realm = JSON.parse(fs.readFileSync(file, "utf8"));
  realm.accessTokenLifespan = 30;
  fs.writeFileSync(file, JSON.stringify(realm));
' "$temporary_directory/hero-association-realm.json"

kc create namespace "$namespace"
created=true
kc -n "$namespace" create secret generic hero-association-k3d-credentials \
  --from-file="$temporary_directory/CORE_DATABASE_PASSWORD" \
  --from-file="$temporary_directory/KEYCLOAK_DATABASE_PASSWORD" \
  --from-file="$temporary_directory/KEYCLOAK_ADMIN_PASSWORD" \
  --from-file="$temporary_directory/HERO_ASSOCIATION_BFF_OIDC_CLIENT_SECRET" \
  --from-file="$temporary_directory/HERO_ASSOCIATION_BFF_OIDC_STATE_SECRET" \
  --from-file="$temporary_directory/HERO_ASSOCIATION_BFF_CSRF_TOKEN_SIGNATURE_KEY"
kc -n "$namespace" create secret generic hero-association-expedition-credentials \
  --from-file="$temporary_directory/HERO_ASSOCIATION_EXPEDITION_RABBITMQ_PASSWORD" \
  --from-file="$temporary_directory/HERO_ASSOCIATION_EXPEDITION_WORKER_RABBITMQ_PASSWORD" \
  --from-file="$temporary_directory/HERO_ASSOCIATION_CORE_SETTLEMENT_RABBITMQ_PASSWORD" \
  --from-file="$temporary_directory/HERO_ASSOCIATION_EXPEDITION_CORE_SERVICE_KEY" \
  --from-file="$temporary_directory/HERO_ASSOCIATION_EXPEDITION_BFF_SERVICE_KEY"
kc -n "$namespace" create configmap hero-association-k3d-realm \
  --from-file="$temporary_directory/hero-association-realm.json"
kc -n "$namespace" create configmap hero-association-k3d-theme \
  --from-file=theme.properties="$project_dir/backend/keycloak/theme/hero-association/login/theme.properties" \
  --from-file=hero-association.css="$project_dir/backend/keycloak/theme/hero-association/login/resources/css/hero-association.css"
kc -n "$namespace" create configmap hero-association-expedition-rabbitmq-setup \
  --from-file=prepare-expedition.mjs="$project_dir/backend/rabbitmq/prepare-expedition.mjs"

kubectl kustomize "$project_dir/deploy/k8s/e2e" | sed \
    -e "s#hero-association-core:k3d#$core_image#g" \
    -e "s#hero-association-bff:k3d#$bff_image#g" \
    -e "s#hero-association-expedition:k3d#$expedition_image#g" \
    -e "s#hero-association-frontend:k3d#$frontend_image#g" | kc apply -f -
for workload in deployment/postgres-core deployment/postgres-keycloak \
  deployment/redis-bff deployment/redis-core statefulset/redis-expedition \
  statefulset/rabbitmq-expedition; do
  kc -n "$namespace" rollout status "$workload" --timeout=5m
done

rabbit_job="$(kc create -f "$project_dir/deploy/k8s/expedition/rabbitmq-setup-job.yaml" \
  --dry-run=client -o json | node -e '
    let input = "";
    process.stdin.on("data", chunk => input += chunk);
    process.stdin.on("end", () => {
      const job = JSON.parse(input);
      job.metadata.namespace = "hero-association-e2e";
      process.stdout.write(JSON.stringify(job));
    });
  ' | kc create -f - -o jsonpath='{.metadata.name}')"
kc -n "$namespace" wait --for=condition=complete "job/$rabbit_job" --timeout=5m

# Probe the disposable broker over AMQP: the Expedition worker must not read
# Core's settlement queue. Use an assigned loopback port to avoid collisions.
kubectl --kubeconfig "$kubeconfig" -n "$namespace" port-forward --address 127.0.0.1 \
  service/rabbitmq-expedition :5672 \
  > "$temporary_directory/rabbit-port-forward.log" 2>&1 &
rabbit_forward_pid=$!
rabbit_local_port=""
for attempt in {1..50}; do
  rabbit_local_port="$(sed -nE \
    's/^Forwarding from 127[.]0[.]0[.]1:([0-9]+) -> 5672$/\1/p' \
    "$temporary_directory/rabbit-port-forward.log" | head -1)"
  [[ -n "$rabbit_local_port" ]] && break
  if ! kill -0 "$rabbit_forward_pid" 2>/dev/null; then break; fi
  sleep 0.2
done
if [[ -z "$rabbit_local_port" ]]; then
  sed -n '1,30p' "$temporary_directory/rabbit-port-forward.log" >&2
  printf 'Could not forward disposable RabbitMQ to loopback.\n' >&2
  exit 1
fi
HERO_ASSOCIATION_E2E_RABBIT_PORT="$rabbit_local_port" \
  HERO_ASSOCIATION_E2E_RABBIT_WORKER_PASSWORD="$( < "$temporary_directory/HERO_ASSOCIATION_EXPEDITION_WORKER_RABBITMQ_PASSWORD")" \
  HERO_ASSOCIATION_E2E_RABBIT_CORE_PASSWORD="$( < "$temporary_directory/HERO_ASSOCIATION_CORE_SETTLEMENT_RABBITMQ_PASSWORD")" \
  "$project_dir/backend/mvnw" -f "$project_dir/backend/pom.xml" \
    -pl hero-association-expedition -am \
    -Dtest=K3dRabbitPermissionsIT -Dsurefire.failIfNoSpecifiedTests=false test
kill "$rabbit_forward_pid" 2>/dev/null || true
wait "$rabbit_forward_pid" 2>/dev/null || true
rabbit_forward_pid=""
printf 'Disposable RabbitMQ denied cross-role access to both settlement queues.\n'

bootstrap_image="$core_image"
bootstrap_job="$(kc create -f "$project_dir/deploy/k8s/backend/core-db-bootstrap.yaml" \
  --dry-run=client -o json | HERO_ASSOCIATION_E2E_CORE_IMAGE="$bootstrap_image" node -e '
    let input = "";
    process.stdin.on("data", chunk => input += chunk);
    process.stdin.on("end", () => {
      const job = JSON.parse(input);
      job.metadata.namespace = "hero-association-e2e";
      job.spec.template.spec.containers[0].image = process.env.HERO_ASSOCIATION_E2E_CORE_IMAGE;
      process.stdout.write(JSON.stringify(job));
    });
  ' | kc create -f - -o jsonpath='{.metadata.name}')"
kc -n "$namespace" wait --for=condition=complete "job/$bootstrap_job" --timeout=5m

for workload in deployment/keycloak deployment/core deployment/expedition \
  deployment/bff deployment/frontend; do
  kc -n "$namespace" rollout status "$workload" --timeout=5m
done
for route in bff keycloak frontend market-order-placement; do
  kc -n "$namespace" wait \
    --for=jsonpath='{.status.parents[0].conditions[?(@.type=="Accepted")].status}'=True \
    "httproute/$route" --timeout=2m
done
if [[ -n "$archive_directory" ]]; then
  node "$project_dir/e2e/k3d-candidate-archive.mjs" verify \
    "$candidate_file" - "$kubeconfig" "$namespace"
fi

gateway_ip="$(docker inspect k3d-hero-association-serverlb --format \
  '{{(index .NetworkSettings.Networks "k3d-hero-association").IPAddress}}')"
if [[ ! "$gateway_ip" =~ ^([0-9]{1,3}\.){3}[0-9]{1,3}$ ]]; then
  printf 'Unable to resolve the k3d Gateway IPv4 address.\n' >&2
  exit 1
fi
playwright_version="$(node -p "require('$project_dir/e2e/package-lock.json').packages['node_modules/@playwright/test'].version")"
playwright_image="mcr.microsoft.com/playwright:v${playwright_version}-noble"
for url in \
  https://app.e2e.heroassociation.test/ \
  https://auth.e2e.heroassociation.test/realms/hero-association/.well-known/openid-configuration; do
  docker run --rm --network k3d-hero-association \
    --add-host "app.e2e.heroassociation.test:$gateway_ip" \
    --add-host "auth.e2e.heroassociation.test:$gateway_ip" \
    --mount "type=bind,src=$ca_certificate,dst=/tmp/local-ca.crt,readonly" \
    "$playwright_image" \
    curl --silent --show-error --fail --max-time 30 \
      --cacert /tmp/local-ca.crt --output /dev/null "$url"
done

docker run --rm --init --network k3d-hero-association --ipc host \
  --add-host "app.e2e.heroassociation.test:$gateway_ip" \
  --add-host "auth.e2e.heroassociation.test:$gateway_ip" \
  --user "$(id -u):$(id -g)" \
  --volume "$project_dir/e2e:/work" --workdir /work \
  "$playwright_image" \
  npx playwright test --config playwright.k3d.isolated.config.js

# Creature definitions must still be available from PostgreSQL when only
# this disposable Core cache is unavailable.
kc -n "$namespace" scale deployment/redis-core --replicas=0
for attempt in {1..30}; do
  core_cache_pods="$(kc -n "$namespace" get pods -l app=redis-core -o jsonpath='{.items[*].metadata.name}')"
  [[ -z "$core_cache_pods" ]] && break
  sleep 1
done
if [[ -n "$core_cache_pods" ]]; then
  printf 'Disposable Core Redis did not stop.\n' >&2
  exit 1
fi
if [[ "$(kc -n "$namespace" get deployment/core -o jsonpath='{.status.readyReplicas}')" != 1 ]]; then
  printf 'Core became unready during optional creature-cache outage.\n' >&2
  exit 1
fi
docker run --rm --init --network k3d-hero-association --ipc host \
  --add-host "app.e2e.heroassociation.test:$gateway_ip" \
  --add-host "auth.e2e.heroassociation.test:$gateway_ip" \
  --user "$(id -u):$(id -g)" \
  --volume "$project_dir/e2e:/work" --workdir /work \
  "$playwright_image" \
  npx playwright test --config playwright.k3d.isolated.config.js \
    --grep 'User2 default party enters Troll Field'
core_logs="$(kc -n "$namespace" logs deployment/core -c core --since=10m)"
if [[ "$core_logs" != *'Creature cache read failed for Troll; using PostgreSQL'* ]]; then
  printf 'Map journey passed without evidence of Core PostgreSQL creature fallback.\n' >&2
  exit 1
fi
kc -n "$namespace" scale deployment/redis-core --replicas=1
kc -n "$namespace" rollout status deployment/redis-core --timeout=2m
printf 'Disposable Core creature-cache outage used PostgreSQL fallback and recovered.\n'

mkdir -m 700 "$temporary_directory/session"
run_session_phase() {
  docker run --rm --init --network k3d-hero-association --ipc host \
    --add-host "app.e2e.heroassociation.test:$gateway_ip" \
    --add-host "auth.e2e.heroassociation.test:$gateway_ip" \
    --user "$(id -u):$(id -g)" \
    --volume "$project_dir/e2e:/work" \
    --volume "$temporary_directory/session:/session" --workdir /work \
    "$playwright_image" npx playwright test \
      --config playwright.k3d.isolated.session.config.js --grep "$1"
}
bff_uids() {
  kc -n "$namespace" get pods -l app=bff -o json | node -e '
    let input = "";
    process.stdin.on("data", chunk => input += chunk);
    process.stdin.on("end", () => {
      const pods = JSON.parse(input).items.filter(pod => !pod.metadata.deletionTimestamp);
      process.stdout.write(pods.map(pod => pod.metadata.uid).join(" "));
    });
  '
}
before_pods="$(bff_uids)"
if [[ "$(wc -w <<< "$before_pods")" -ne 2 ]]; then
  printf 'Expected two ready disposable BFF Pods before session test.\n' >&2
  exit 1
fi
run_session_phase 'before BFF restart'
kc -n "$namespace" rollout restart deployment/bff
kc -n "$namespace" rollout status deployment/bff --timeout=5m
after_pods="$(bff_uids)"
if [[ "$(wc -w <<< "$after_pods")" -ne 2 ]]; then
  printf 'Expected two replacement disposable BFF Pods.\n' >&2
  exit 1
fi
for old_uid in $before_pods; do
  if [[ " $after_pods " == *" $old_uid "* ]]; then
    printf 'A pre-restart BFF Pod still serves the disposable stack.\n' >&2
    exit 1
  fi
done
if [[ -n "$archive_directory" ]]; then
  node "$project_dir/e2e/k3d-candidate-archive.mjs" verify \
    "$candidate_file" - "$kubeconfig" "$namespace"
fi
run_session_phase 'after BFF restart'
printf 'Redis-backed BFF session survived replacement of both replicas.\n'

kc -n "$namespace" scale deployment/redis-bff --replicas=0
kc -n "$namespace" rollout status deployment/redis-bff --timeout=2m
run_session_phase 'BFF Redis outage fails closed'
kc -n "$namespace" scale deployment/redis-bff --replicas=1
kc -n "$namespace" rollout status deployment/redis-bff --timeout=2m
run_session_phase 'fresh login works after BFF Redis recovery'
printf 'Disposable BFF Redis outage failed closed and recovered.\n'

mapfile -t token_keys < <(kc -n "$namespace" exec deployment/redis-bff -c redis -- \
  redis-cli --scan --pattern 'oidc:token:*')
if [[ "${#token_keys[@]}" -ne 1 || -z "${token_keys[0]}" ]]; then
  printf 'Expected exactly one token-state key in disposable BFF Redis after fresh login.\n' >&2
  exit 1
fi
if [[ "$(kc -n "$namespace" exec deployment/redis-bff -c redis -- \
  redis-cli EXPIRE "${token_keys[0]}" 1)" != 1 ]]; then
  printf 'Could not expire disposable BFF token state.\n' >&2
  exit 1
fi
sleep 2
run_session_phase 'expired BFF token state fails closed'
printf 'Expired disposable BFF token state failed closed.\n'

mkdir -m 700 "$temporary_directory/k6"
docker run --rm --init --network k3d-hero-association --ipc host \
  --add-host "app.e2e.heroassociation.test:$gateway_ip" \
  --add-host "auth.e2e.heroassociation.test:$gateway_ip" \
  --user "$(id -u):$(id -g)" \
  --env HERO_ASSOCIATION_K6_APP_URL=https://app.e2e.heroassociation.test \
  --volume "$project_dir/e2e:/work:ro" \
  --volume "$temporary_directory/k6:/session" --workdir /work \
  "$playwright_image" node /work/prepare-k6-session.mjs
docker run --rm --init --network k3d-hero-association \
  --add-host "app.e2e.heroassociation.test:$gateway_ip" \
  --add-host "auth.e2e.heroassociation.test:$gateway_ip" \
  --user "$(id -u):$(id -g)" \
  --volume "$project_dir/e2e:/work:ro" \
  --volume "$temporary_directory/k6:/session:ro" \
  grafana/k6:2.3.0 run /work/market-rate-limit.k6.js
printf 'Disposable k3d market k6 thresholds passed.\n'

printf 'Disposable full-stack E2E browser checks passed. Removing the namespace.\n'
kc delete namespace "$namespace" --wait=true --timeout=5m
kc wait --for=delete "namespace/$namespace" --timeout=5m
created=false
if [[ -n "$archive_directory" ]]; then
  node "$project_dir/e2e/k3d-candidate-archive.mjs" pass "$candidate_file"
  printf 'Exact four-image archive verified in disposable k3d and recorded as passed.\n'
else
  printf 'Disposable full-stack E2E browser checks completed.\n'
fi
