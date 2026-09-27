#!/usr/bin/env bash
set -euo pipefail

script_dir="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")" && pwd)"
kubeconfig_path="$script_dir/.kubeconfig"
secret_dir="$script_dir/secrets"
namespace=hero-association

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
for key in CORE_DATABASE_PASSWORD KEYCLOAK_DATABASE_PASSWORD \
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
kubectl -n "$namespace" create configmap hero-association-k3d-realm \
  --from-file="$secret_dir/hero-association-realm.json" \
  --dry-run=client -o yaml | kubectl apply -f -
kubectl -n "$namespace" create configmap hero-association-k3d-theme \
  --from-file=theme.properties="$script_dir/../../backend/keycloak/theme/hero-association/login/theme.properties" \
  --from-file=hero-association.css="$script_dir/../../backend/keycloak/theme/hero-association/login/resources/css/hero-association.css" \
  --dry-run=client -o yaml | kubectl apply -f -

for dependency in postgres-core postgres-keycloak redis-bff; do
  kubectl apply -f "$script_dir/../k8s/backend/$dependency.yaml"
done
for dependency in postgres-core postgres-keycloak redis-bff; do
  kubectl -n "$namespace" rollout status "deployment/$dependency" --timeout=5m
done

kubectl apply -f "$script_dir/../k8s/backend/keycloak.yaml"
kubectl -n "$namespace" rollout status deployment/keycloak --timeout=5m

kubectl apply -k "$script_dir/../k8s/backend"
for service in core bff; do
  kubectl -n "$namespace" rollout status "deployment/$service" --timeout=5m
done

kubectl apply -k "$script_dir/../k8s/mesh"
