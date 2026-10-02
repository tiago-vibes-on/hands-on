#!/usr/bin/env bash
set -euo pipefail

script_dir="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")" && pwd)"
kubeconfig="${HERO_ASSOCIATION_K3D_KUBECONFIG:-$script_dir/.kubeconfig}"
[[ -f "$kubeconfig" ]] || { printf 'Missing isolated k3d kubeconfig: %s\n' "$kubeconfig" >&2; exit 1; }

kube=(kubectl --kubeconfig "$kubeconfig")
[[ "$("${kube[@]}" config current-context)" == k3d-hero-association ]] ||
  { printf 'Refusing a Kubernetes context other than k3d-hero-association.\n' >&2; exit 1; }

for service in core bff expedition market assets frontend; do
  replicas="$("${kube[@]}" -n hero-association get "deployment/$service" -o jsonpath='{.spec.replicas}')"
  ready="$("${kube[@]}" -n hero-association get "deployment/$service" -o jsonpath='{.status.readyReplicas}')"
  selector="$("${kube[@]}" -n hero-association get "service/$service" -o jsonpath='{.spec.selector.hybrid}')"
  if [[ ! "$replicas" =~ ^[1-9][0-9]*$ || "$ready" != "$replicas" || -n "$selector" ]]; then
    printf '%s is not in full k3d mode (replicas=%s, ready=%s, hybrid=%s). Restore it before a candidate build or deploy.\n' \
      "$service" "$replicas" "${ready:-0}" "${selector:-none}" >&2
    exit 1
  fi
done

printf 'All six application services are in full k3d mode.\n'
