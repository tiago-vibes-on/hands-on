#!/usr/bin/env bash
set -euo pipefail

script_dir="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")" && pwd)"
project_dir="$script_dir/.."
kubeconfig="${HERO_ASSOCIATION_K3D_KUBECONFIG:-$project_dir/deploy/k3d/.kubeconfig}"

reset_core_db=false
if [[ "${1:-}" == "--reset-core-db" || "${1:-}" == "--reset-game-db" ]]; then
  reset_core_db=true
  shift
fi

if [[ $# -gt 1 ]]; then
  printf 'Usage: %s [--reset-game-db] [build-id]\n' "$0" >&2
  exit 2
fi

source_revision="$(git -C "$project_dir" rev-parse --short=8 HEAD)"
build_id="${1:-local-$(date -u +%Y%m%dT%H%M%S)-${source_revision}-$$}"
if [[ ! "$build_id" =~ ^[A-Za-z0-9_][A-Za-z0-9_.-]{0,127}$ ]]; then
  printf 'Invalid build ID: %s\n' "$build_id" >&2
  exit 2
fi
artifact_dir="$script_dir/artifacts/$build_id/all"

stage='k3d preflight'
trap 'status=$?; printf "Pipeline stopped during %s (exit %s). Artifact: %s\n" "$stage" "$status" "$artifact_dir" >&2' ERR

if [[ ! -f "$kubeconfig" ]]; then
  printf 'Missing isolated k3d kubeconfig: %s\n' "$kubeconfig" >&2
  exit 1
fi
context="$(kubectl --kubeconfig "$kubeconfig" config current-context)"
if [[ "$context" != 'k3d-hero-association' ]]; then
  printf 'Refusing to use Kubernetes context: %s\n' "$context" >&2
  exit 1
fi
HERO_ASSOCIATION_K3D_KUBECONFIG="$kubeconfig" "$project_dir/deploy/k3d/require-full-k3d.sh"

stage='k3d rollback regression tests'
node --test "$script_dir/rollback-k3d.test.mjs" "$script_dir/core-bootstrap-job.test.mjs" "$script_dir/market-bootstrap-job.test.mjs" "$script_dir/assets-bootstrap-job.test.mjs"

stage='build and archive'
printf 'Building Core, BFF, Expedition, and frontend as %s\n' "$build_id"
"$script_dir/build-local.sh" all "$build_id"

stage='disposable k3d archive E2E'
(
  cd "$project_dir/e2e"
  npm ci
)
if [[ -z "${HERO_ASSOCIATION_LOCAL_CA_CERTIFICATE:-}" && -n "${HERO_ASSOCIATION_SOURCE_REPO:-}" ]]; then
  export HERO_ASSOCIATION_LOCAL_CA_CERTIFICATE="$HERO_ASSOCIATION_SOURCE_REPO/hero-association/tls/certs/local-ca.crt"
fi
"$project_dir/deploy/k3d/test-isolated-stack.sh" "$artifact_dir"

stage='k3d promotion, browser and k6 checks'
if [[ "$reset_core_db" == true ]]; then
  node "$script_dir/deploy-k3d.mjs" --reset-core-db "$artifact_dir"
else
  node "$script_dir/deploy-k3d.mjs" "$artifact_dir"
fi

printf 'Pipeline complete. Verified archive: %s\n' "$artifact_dir"
