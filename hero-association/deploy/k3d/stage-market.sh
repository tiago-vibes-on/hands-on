#!/usr/bin/env bash
set -euo pipefail
umask 077
[[ $# -eq 1 ]] || { printf 'Usage: %s SIX_IMAGE_ARCHIVE_DIRECTORY\n' "$0" >&2; exit 2; }
script_dir="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")" && pwd)"
project_dir="$(cd -- "$script_dir/../.." && pwd)"
kubeconfig="${HERO_ASSOCIATION_K3D_KUBECONFIG:-$script_dir/.kubeconfig}"
export KUBECONFIG="$kubeconfig"
[[ "$(kubectl config current-context)" == k3d-hero-association ]] || exit 1
market_image="$(node --input-type=module - "$project_dir" "$1" <<'JS'
import { pathToFileURL } from 'node:url'
const { inspectArchive, requirePassingK3dE2EVerification } = await import(pathToFileURL(process.argv[2] + '/e2e/archive-images.js'))
const archive = await inspectArchive(process.argv[3])
await requirePassingK3dE2EVerification(archive)
if (archive.promoteComponent) throw new Error('Initial Market staging requires a complete six-service extraction archive')
process.stdout.write(archive.images.market.ref)
JS
)"
if kubectl -n hero-association get deployment/market >/dev/null 2>&1; then
  printf 'Market already exists; use the normal verified promotion workflow.\n' >&2
  exit 1
fi
namespace=hero-association
secret_dir="$script_dir/secrets"
mkdir -p "$secret_dir"
# Initial staging creates only Market resources; coupled promotion switches callers and all three seeds.
for key in MARKET_DATABASE_PASSWORD HERO_ASSOCIATION_ASSETS_MARKET_SERVICE_KEY; do
  if [[ ! -s "$secret_dir/$key" ]]; then
    openssl rand -hex -out "$secret_dir/$key" 32
    perl -pi -e 'chomp' "$secret_dir/$key"
  fi
done
kubectl -n "$namespace" create secret generic hero-association-market-credentials \
  --from-file="$secret_dir/MARKET_DATABASE_PASSWORD" \
  --from-file="$secret_dir/HERO_ASSOCIATION_ASSETS_MARKET_SERVICE_KEY" \
  --dry-run=client -o yaml | kubectl apply -f -
kubectl apply -f "$project_dir/deploy/k8s/backend/service-accounts.yaml"
kubectl apply -f "$project_dir/deploy/k8s/backend/postgres-market.yaml"
kubectl -n "$namespace" rollout status deployment/postgres-market --timeout=5m
schema_exists="$(kubectl -n "$namespace" exec deployment/postgres-market -c postgres -- \
  psql -U hero_association_market -d hero_association_market -At \
  -c "SELECT to_regclass('public.market_order') IS NOT NULL")"
if [[ "$schema_exists" != f ]]; then
  printf 'Refusing initial staging over an existing Market schema. Inspect the interrupted cutover.\n' >&2
  exit 1
fi
"${K3D_BIN:-$script_dir/.tools/k3d}" image import "$market_image" --cluster hero-association
job="$(sed "s#hero-association-market:k3d#$market_image#g" \
  "$project_dir/deploy/k8s/backend/market-db-bootstrap.yaml" | kubectl create -f - -o jsonpath='{.metadata.name}')"
kubectl -n "$namespace" wait --for=condition=complete "job/$job" --timeout=5m
node "$script_dir/sync-market-realm.mjs"
kubectl apply -f "$project_dir/deploy/k8s/mesh/core-market-authorization.yaml"
sed "s#hero-association-market:k3d#$market_image#g" \
  "$project_dir/deploy/k8s/backend/market.yaml" | kubectl apply -f -
kubectl -n "$namespace" rollout status deployment/market --timeout=5m
printf 'Market staged. Ensure Assets is staged, then promote this verified archive with --reset-game-db to cut over all three writers.\n'
