#!/usr/bin/env bash
set -euo pipefail
umask 077
[[ $# -eq 1 ]] || { printf 'Usage: %s EIGHT_IMAGE_ARCHIVE_DIRECTORY\n' "$0" >&2; exit 2; }
script_dir="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")" && pwd)"
project_dir="$(cd -- "$script_dir/../.." && pwd)"
export KUBECONFIG="${HERO_ASSOCIATION_K3D_KUBECONFIG:-$script_dir/.kubeconfig}"
[[ "$(kubectl config current-context)" == k3d-hero-association ]] || exit 1
images="$(node --input-type=module - "$project_dir" "$1" <<'JS'
import { pathToFileURL } from 'node:url'
const { inspectArchive, requirePassingK3dE2EVerification } = await import(pathToFileURL(process.argv[2] + '/e2e/archive-images.js'))
const archive = await inspectArchive(process.argv[3])
await requirePassingK3dE2EVerification(archive)
if (archive.promoteComponent) throw new Error('Initial staging requires a complete eight-service archive')
process.stdout.write(archive.images.world.ref + '\n' + archive.images.quest.ref)
JS
)"
mapfile -t catalog_images <<< "$images"
for service in world quest; do
  if kubectl -n hero-association get "deployment/$service" >/dev/null 2>&1; then
    printf '%s already exists; use the normal verified promotion workflow.\n' "$service" >&2
    exit 1
  fi
done
secret_dir="$script_dir/secrets"
mkdir -p "$secret_dir"
for key in WORLD_DATABASE_PASSWORD QUEST_DATABASE_PASSWORD HERO_ASSOCIATION_WORLD_SERVICE_KEY HERO_ASSOCIATION_QUEST_CORE_SERVICE_KEY HERO_ASSOCIATION_QUEST_EXPEDITION_SERVICE_KEY HERO_ASSOCIATION_ASSETS_QUEST_SERVICE_KEY; do
  if [[ ! -s "$secret_dir/$key" ]]; then
    openssl rand -hex -out "$secret_dir/$key" 32
    perl -pi -e 'chomp' "$secret_dir/$key"
  fi
done
kubectl -n hero-association create secret generic hero-association-world-credentials \
  --from-file="$secret_dir/WORLD_DATABASE_PASSWORD" --from-file="$secret_dir/HERO_ASSOCIATION_WORLD_SERVICE_KEY" \
  --dry-run=client -o yaml | kubectl apply -f -
kubectl -n hero-association create secret generic hero-association-quest-credentials \
  --from-file="$secret_dir/QUEST_DATABASE_PASSWORD" --from-file="$secret_dir/HERO_ASSOCIATION_QUEST_CORE_SERVICE_KEY" \
  --from-file="$secret_dir/HERO_ASSOCIATION_QUEST_EXPEDITION_SERVICE_KEY" --from-file="$secret_dir/HERO_ASSOCIATION_ASSETS_QUEST_SERVICE_KEY" \
  --dry-run=client -o yaml | kubectl apply -f -
kubectl apply -f "$project_dir/deploy/k8s/backend/service-accounts.yaml"
"${K3D_BIN:-$script_dir/.tools/k3d}" image import "${catalog_images[@]}" --cluster hero-association
index=0
for service in world quest; do
  kubectl apply -f "$project_dir/deploy/k8s/backend/postgres-$service.yaml"
  kubectl -n hero-association rollout status "deployment/postgres-$service" --timeout=5m
  schema_exists="$(kubectl -n hero-association exec "deployment/postgres-$service" -c postgres -- \
    psql -U "hero_association_$service" -d "hero_association_$service" -At -c "SELECT EXISTS (SELECT 1 FROM information_schema.tables WHERE table_schema='public')")"
  [[ "$schema_exists" == f ]] || { printf 'Refusing initial staging over an existing %s schema.\n' "$service" >&2; exit 1; }
  image="${catalog_images[$index]}"
  job="$(sed "s#hero-association-$service:k3d#$image#g" "$project_dir/deploy/k8s/backend/$service-db-bootstrap.yaml" | kubectl create -f - -o jsonpath='{.metadata.name}')"
  kubectl -n hero-association wait --for=condition=complete "job/$job" --timeout=5m
  node "$script_dir/sync-$service-realm.mjs"
  index=$((index + 1))
done
kubectl apply -f "$project_dir/deploy/k8s/mesh/world-quest-authorization.yaml"
index=0
for service in world quest; do
  sed "s#hero-association-$service:k3d#${catalog_images[$index]}#g" "$project_dir/deploy/k8s/backend/$service.yaml" | kubectl apply -f -
  kubectl -n hero-association rollout status "deployment/$service" --timeout=5m
  index=$((index + 1))
done
printf 'World and Quest staged. Promote the same verified archive with --reset-game-db to cut over all eight services and reset five lab databases.\n'
