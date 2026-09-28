#!/usr/bin/env bash
set -euo pipefail

script_dir="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")" && pwd)"
e2e_dir="$script_dir/../../e2e"
export KUBECONFIG="$script_dir/.kubeconfig"
export HERO_ASSOCIATION_K3D_LOAD_SECONDS="${HERO_ASSOCIATION_K3D_LOAD_SECONDS:-90}"
export HERO_ASSOCIATION_K3D_LOAD_CLIENTS="${HERO_ASSOCIATION_K3D_LOAD_CLIENTS:-16}"
namespace=hero-association
load_pid=

if [[ $# -ne 0 ]]; then
  printf 'Usage: %s\n' "$0" >&2
  exit 2
fi
if [[ ! -f "$KUBECONFIG" || "$(kubectl config current-context)" != k3d-hero-association ]]; then
  printf 'The isolated hero-association k3d kubeconfig is required.\n' >&2
  exit 1
fi
if [[ ! "$HERO_ASSOCIATION_K3D_LOAD_SECONDS" =~ ^[0-9]+$ ]] ||
   (( HERO_ASSOCIATION_K3D_LOAD_SECONDS < 60 || HERO_ASSOCIATION_K3D_LOAD_SECONDS > 600 )); then
  printf 'HERO_ASSOCIATION_K3D_LOAD_SECONDS must be between 60 and 600.\n' >&2
  exit 2
fi

cleanup() {
  if [[ -n "$load_pid" ]] && kill -0 "$load_pid" 2>/dev/null; then
    kill "$load_pid" 2>/dev/null || true
    wait "$load_pid" 2>/dev/null || true
  fi
}
trap cleanup EXIT

replicas() {
  kubectl -n "$namespace" get hpa "$1" -o jsonpath='{.status.currentReplicas}'
}

printf 'Waiting for both HPAs and their two-Ready-Pod baseline.\n'
baseline_ready=false
for attempt in {1..30}; do
  bff_replicas="$(replicas bff)"
  core_replicas="$(replicas core)"
  bff_ready="$(kubectl -n "$namespace" get deployment bff -o jsonpath='{.status.readyReplicas}')"
  core_ready="$(kubectl -n "$namespace" get deployment core -o jsonpath='{.status.readyReplicas}')"
  if [[ "$bff_replicas" == 2 && "$core_replicas" == 2 && "$bff_ready" == 2 && "$core_ready" == 2 ]]; then
    baseline_ready=true
    break
  fi
  sleep 10
done
if [[ "$baseline_ready" != true ]]; then
  printf 'Both services must settle at two Ready Pods before this test.\n' >&2
  exit 1
fi

printf 'Running %s authenticated clients for %s seconds.\n' \
  "$HERO_ASSOCIATION_K3D_LOAD_CLIENTS" "$HERO_ASSOCIATION_K3D_LOAD_SECONDS"
(cd "$e2e_dir" && npm run load:k3d) &
load_pid=$!
peak_bff=2
peak_core=2
restarted=false
for sample in {1..65}; do
  sleep 10
  bff_replicas="$(replicas bff)"
  core_replicas="$(replicas core)"
  if (( bff_replicas > peak_bff )); then
    peak_bff="$bff_replicas"
  fi
  if (( core_replicas > peak_core )); then
    peak_core="$core_replicas"
  fi
  printf 'Load sample %02d: BFF=%s Core=%s\n' "$sample" "$bff_replicas" "$core_replicas"
  if [[ "$sample" -eq 3 ]] && kill -0 "$load_pid" 2>/dev/null; then
    printf 'Rolling BFF while the authenticated requests continue.\n'
    kubectl -n "$namespace" rollout restart deployment/bff
    restarted=true
  fi
  if ! kill -0 "$load_pid" 2>/dev/null; then
    break
  fi
done

load_passed=true
if ! wait "$load_pid"; then
  load_passed=false
fi
load_pid=
kubectl -n "$namespace" rollout status deployment/bff --timeout=5m
printf 'Peak sampled during load: BFF=%s Core=%s.\n' "$peak_bff" "$peak_core"
if [[ "$load_passed" != true || "$restarted" != true || "$peak_bff" -le 2 || "$peak_core" -le 2 ]]; then
  printf 'Load, rollout, or scale-out did not meet the test criteria.\n' >&2
  exit 1
fi

printf 'Waiting for both HPAs to scale back to two Pods.\n'
scaled_in=false
for attempt in {1..36}; do
  bff_replicas="$(replicas bff)"
  core_replicas="$(replicas core)"
  bff_ready="$(kubectl -n "$namespace" get deployment bff -o jsonpath='{.status.readyReplicas}')"
  core_ready="$(kubectl -n "$namespace" get deployment core -o jsonpath='{.status.readyReplicas}')"
  if (( bff_replicas > peak_bff )); then
    peak_bff="$bff_replicas"
  fi
  if (( core_replicas > peak_core )); then
    peak_core="$core_replicas"
  fi
  if [[ "$bff_replicas" == 2 && "$core_replicas" == 2 && "$bff_ready" == 2 && "$core_ready" == 2 ]]; then
    scaled_in=true
    break
  fi
  if (( attempt % 3 == 0 )); then
    printf 'Scale-in sample: BFF=%s Core=%s, Ready=%s/%s.\n' \
      "$bff_replicas" "$core_replicas" "$bff_ready" "$core_ready"
  fi
  sleep 10
done
if [[ "$scaled_in" != true ]]; then
  printf 'The HPAs did not return to two Ready Pods within six minutes.\n' >&2
  exit 1
fi
printf 'Overall peak replicas: BFF=%s Core=%s.\n' "$peak_bff" "$peak_core"

(cd "$e2e_dir" && npm run test:k3d)
printf 'PASS: BFF and Core scaled out and in; authenticated requests survived a BFF rollout; browser tests passed.\n'
