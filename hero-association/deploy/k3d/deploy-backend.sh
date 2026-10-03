#!/usr/bin/env bash
set -euo pipefail

script_dir="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")" && pwd)"
kubeconfig_path="$script_dir/.kubeconfig"
secret_dir="$script_dir/secrets"
namespace=hero-association

if [[ $# -gt 1 || ( $# -eq 1 && "$1" != "--reset-core-db" ) ]]; then
  printf 'Usage: %s [--reset-core-db]\n' "$0" >&2
  exit 2
fi
reset_core_db="${1:-}"

if [[ ! -f "$kubeconfig_path" ]]; then
  printf 'Missing %s. Create the k3d cluster first.\n' "$kubeconfig_path" >&2
  exit 1
fi
export KUBECONFIG="$kubeconfig_path"
if [[ "$(kubectl config current-context)" != 'k3d-hero-association' ]]; then
  printf 'Refusing to deploy outside the hero-association k3d context.\n' >&2
  exit 1
fi

umask 077
mkdir -p "$secret_dir"
for key in CORE_DATABASE_PASSWORD MARKET_DATABASE_PASSWORD ASSETS_DATABASE_PASSWORD WORLD_DATABASE_PASSWORD QUEST_DATABASE_PASSWORD HERO_ASSOCIATION_WORLD_SERVICE_KEY HERO_ASSOCIATION_QUEST_CORE_SERVICE_KEY HERO_ASSOCIATION_QUEST_EXPEDITION_SERVICE_KEY HERO_ASSOCIATION_ASSETS_QUEST_SERVICE_KEY HERO_ASSOCIATION_ASSETS_CORE_SERVICE_KEY HERO_ASSOCIATION_ASSETS_MARKET_SERVICE_KEY KEYCLOAK_DATABASE_PASSWORD \
  KEYCLOAK_ADMIN_PASSWORD HERO_ASSOCIATION_BFF_OIDC_CLIENT_SECRET \
  HERO_ASSOCIATION_BFF_OIDC_STATE_SECRET \
  HERO_ASSOCIATION_BFF_CSRF_TOKEN_SIGNATURE_KEY; do
  if [[ ! -s "$secret_dir/$key" ]]; then
    openssl rand -hex 32 | tr -d '\n' > "$secret_dir/$key"
  fi
done

node "$script_dir/generate-realm.mjs" \
  "$script_dir/../../backend/keycloak/realm/hero-association-realm.json" \
  "$secret_dir/hero-association-realm.json"

kubectl apply -f "$script_dir/../k8s/base/namespace.yaml"
kubectl -n "$namespace" create secret generic hero-association-k3d-credentials \
  --from-file="$secret_dir/CORE_DATABASE_PASSWORD" \
  --from-file="$secret_dir/KEYCLOAK_DATABASE_PASSWORD" \
  --from-file="$secret_dir/KEYCLOAK_ADMIN_PASSWORD" \
  --from-file="$secret_dir/HERO_ASSOCIATION_BFF_OIDC_CLIENT_SECRET" \
  --from-file="$secret_dir/HERO_ASSOCIATION_BFF_OIDC_STATE_SECRET" \
  --from-file="$secret_dir/HERO_ASSOCIATION_BFF_CSRF_TOKEN_SIGNATURE_KEY" \
  --dry-run=client -o yaml | kubectl apply -f -
kubectl -n "$namespace" create secret generic hero-association-market-credentials \
  --from-file="$secret_dir/MARKET_DATABASE_PASSWORD" \
  --from-file="$secret_dir/HERO_ASSOCIATION_ASSETS_MARKET_SERVICE_KEY" \
  --dry-run=client -o yaml | kubectl apply -f -
kubectl -n "$namespace" create secret generic hero-association-assets-credentials \
  --from-file="$secret_dir/ASSETS_DATABASE_PASSWORD" \
  --from-file="$secret_dir/HERO_ASSOCIATION_ASSETS_CORE_SERVICE_KEY" \
  --dry-run=client -o yaml | kubectl apply -f -
kubectl -n "$namespace" create secret generic hero-association-world-credentials \
  --from-file="$secret_dir/WORLD_DATABASE_PASSWORD" --from-file="$secret_dir/HERO_ASSOCIATION_WORLD_SERVICE_KEY" \
  --dry-run=client -o yaml | kubectl apply -f -
kubectl -n "$namespace" create secret generic hero-association-quest-credentials \
  --from-file="$secret_dir/QUEST_DATABASE_PASSWORD" --from-file="$secret_dir/HERO_ASSOCIATION_QUEST_CORE_SERVICE_KEY" \
  --from-file="$secret_dir/HERO_ASSOCIATION_QUEST_EXPEDITION_SERVICE_KEY" --from-file="$secret_dir/HERO_ASSOCIATION_ASSETS_QUEST_SERVICE_KEY" \
  --dry-run=client -o yaml | kubectl apply -f -
kubectl -n "$namespace" create configmap hero-association-k3d-realm \
  --from-file="$secret_dir/hero-association-realm.json" \
  --dry-run=client -o yaml | kubectl apply -f -
kubectl -n "$namespace" create configmap hero-association-k3d-theme \
  --from-file=theme.properties="$script_dir/../../backend/keycloak/theme/hero-association/login/theme.properties" \
  --from-file=hero-association.css="$script_dir/../../backend/keycloak/theme/hero-association/login/resources/css/hero-association.css" \
  --dry-run=client -o yaml | kubectl apply -f -

for dependency in postgres-core postgres-market postgres-assets postgres-world postgres-quest postgres-keycloak redis-bff; do
  kubectl apply -f "$script_dir/../k8s/backend/$dependency.yaml"
done
for dependency in postgres-core postgres-market postgres-assets postgres-world postgres-quest postgres-keycloak redis-bff; do
  kubectl -n "$namespace" rollout status "deployment/$dependency" --timeout=5m
done

core_schema_exists="$(kubectl -n "$namespace" exec deployment/postgres-core -c postgres -- \
  psql -U hero_association -d hero_association -At \
  -c "SELECT to_regclass('public.account') IS NOT NULL")"
if [[ "$core_schema_exists" != "t" && "$core_schema_exists" != "f" ]]; then
  printf 'Unexpected Core schema status: %s\n' "$core_schema_exists" >&2
  exit 1
fi

if [[ "$core_schema_exists" == t && "$reset_core_db" == --reset-core-db ]]; then
  printf 'Existing game data requires a verified eight-image archive. Use pipeline/deploy-k3d.mjs --reset-game-db ARCHIVE.\n' >&2
  exit 1
fi
core_bootstrapped=false
if [[ "$core_schema_exists" == f ]]; then
  for service in core assets market world quest; do
    database="hero_association_$service"
    if [[ "$service" == core ]]; then database=hero_association; fi
    empty="$(kubectl -n "$namespace" exec "deployment/postgres-$service" -c postgres -- \
      psql -U "$database" -d "$database" -At \
      -c "SELECT NOT EXISTS (SELECT 1 FROM information_schema.tables WHERE table_schema='public')")"
    if [[ "$empty" != t ]]; then
      printf 'Refusing fresh bootstrap over existing %s data. Use a verified coordinated reset.\n' "$service" >&2
      exit 1
    fi
  done
  for service in world assets quest core market; do
    job="$(kubectl create -f "$script_dir/../k8s/backend/$service-db-bootstrap.yaml" -o jsonpath='{.metadata.name}')"
    kubectl -n "$namespace" wait --for=condition=complete "job/$job" --timeout=5m
  done
  core_bootstrapped=true
else
  for entry in assets:asset_wallet market:market_order world:map_definition quest:quest_definition; do
    service="${entry%%:*}"; table="${entry#*:}"
    database="hero_association_$service"
    schema_exists="$(kubectl -n "$namespace" exec "deployment/postgres-$service" -c postgres -- \
      psql -U "$database" -d "$database" -At \
      -c "SELECT to_regclass('public.$table') IS NOT NULL")"
    if [[ "$schema_exists" != t ]]; then
      printf '%s extraction requires verified staging and coordinated archive promotion. See the pipeline guide.\n' "$service" >&2
      exit 1
    fi
  done
  printf 'Game schemas exist; skipping destructive bootstrap.\n'
fi

kubectl apply -f "$script_dir/../k8s/backend/keycloak.yaml"
kubectl -n "$namespace" rollout status deployment/keycloak --timeout=5m

kubectl apply -k "$script_dir/../k8s/backend"
if [[ "$core_bootstrapped" == true ]]; then
  kubectl -n "$namespace" scale deployment/core --replicas=2
fi
for service in world assets quest core market bff; do
  kubectl -n "$namespace" rollout status "deployment/$service" --timeout=5m
done

kubectl apply -k "$script_dir/../k8s/mesh"
