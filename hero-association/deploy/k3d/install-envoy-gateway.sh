#!/usr/bin/env bash
set -euo pipefail

script_dir="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")" && pwd)"
kubeconfig_path="$script_dir/.kubeconfig"
if [[ ! -f "$kubeconfig_path" ]]; then
  printf 'Missing %s. Create the k3d cluster and its isolated kubeconfig first.\n' "$kubeconfig_path" >&2
  exit 1
fi

export KUBECONFIG="$kubeconfig_path"
if [[ "$(kubectl config current-context)" != 'k3d-hero-association' ]]; then
  printf 'Refusing to install Envoy Gateway outside the hero-association k3d context.\n' >&2
  exit 1
fi

manifest_path="$(mktemp)"
trap 'rm -f -- "$manifest_path"' EXIT
curl --fail --location --silent --show-error \
  'https://github.com/envoyproxy/gateway/releases/download/v1.9.1/install.yaml' \
  --output "$manifest_path"
printf '%s  %s\n' \
  '72b3971364f172eb0b9636c7142cc84ff695467bc065897958bde85a3c06cfd5' \
  "$manifest_path" | sha256sum --check --status

kubectl apply --server-side --field-manager=hero-association-envoy-gateway \
  -f "$manifest_path"
kubectl -n envoy-gateway-system rollout status deployment/envoy-gateway --timeout=180s

kubectl apply -f "$script_dir/../k8s/local/redis-gateway.yaml"
kubectl -n envoy-gateway-system rollout status deployment/redis-gateway --timeout=180s
kubectl apply -f "$script_dir/../k8s/local/envoy-gateway-config.yaml"
kubectl -n envoy-gateway-system rollout restart deployment/envoy-gateway
kubectl -n envoy-gateway-system rollout status deployment/envoy-gateway --timeout=180s
