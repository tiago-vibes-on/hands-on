#!/usr/bin/env bash
set -euo pipefail
umask 077

script_dir="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")" && pwd)"
kubeconfig_path="$script_dir/.kubeconfig"
if [[ ! -f "$kubeconfig_path" ]]; then
  printf 'Missing %s. Create the k3d cluster first.\n' "$kubeconfig_path" >&2
  exit 1
fi

export KUBECONFIG="$kubeconfig_path"
if [[ "$(kubectl config current-context)" != 'k3d-hero-association' ]]; then
  printf 'Refusing to install observability outside the hero-association k3d context.\n' >&2
  exit 1
fi

password_path="$script_dir/certs/grafana-admin-password"
mkdir -p "$script_dir/certs"
if [[ ! -s "$password_path" ]]; then
  openssl rand -hex 20 > "$password_path"
fi

kubectl apply -f "$script_dir/../k8s/observability/namespace.yaml"
kubectl -n hero-association-observability create secret generic grafana-admin \
  --from-file=admin-password="$password_path" --dry-run=client -o yaml | \
  kubectl apply -f -
agent_already_exists=false
if kubectl -n hero-association-observability get daemonset/observability-agent >/dev/null 2>&1; then
  agent_already_exists=true
fi
kubectl apply -k "$script_dir/../k8s/observability"
if [[ "$agent_already_exists" == "true" ]]; then
  kubectl -n hero-association-observability rollout restart daemonset/observability-agent
fi
kubectl -n hero-association-observability rollout status \
  deployment/otel-lgtm --timeout=300s
kubectl -n hero-association-observability rollout status \
  daemonset/observability-agent --timeout=300s

printf 'Grafana password is stored in the ignored file %s\n' "$password_path"
