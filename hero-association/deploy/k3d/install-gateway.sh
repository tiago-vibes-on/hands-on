#!/usr/bin/env bash
set -euo pipefail

script_dir="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")" && pwd)"
kubeconfig_path="$script_dir/.kubeconfig"
if [[ ! -f "$kubeconfig_path" ]]; then
  printf 'Missing %s. Create the k3d cluster first.\n' "$kubeconfig_path" >&2
  exit 1
fi

export KUBECONFIG="$kubeconfig_path"
if [[ "$(kubectl config current-context)" != 'k3d-hero-association' ]]; then
  printf 'Refusing to install the Gateway outside the hero-association k3d context.\n' >&2
  exit 1
fi

"$script_dir/../../tls/generate-local-certs.sh"
"$script_dir/../../tls/generate-e2e-certs.sh"
kubectl apply -f "$script_dir/../k8s/base/namespace.yaml"
kubectl -n hero-association create secret tls hero-association-k3d-tls \
  --cert="$script_dir/../../tls/certs/local.crt" --key="$script_dir/../../tls/certs/local.key" \
  --dry-run=client -o yaml | kubectl apply -f -
kubectl -n hero-association create secret tls hero-association-k3d-e2e-tls \
  --cert="$script_dir/../../tls/certs/e2e.crt" --key="$script_dir/../../tls/certs/e2e.key" \
  --dry-run=client -o yaml | kubectl apply -f -
kubectl apply -k "$script_dir/../k8s/local"
