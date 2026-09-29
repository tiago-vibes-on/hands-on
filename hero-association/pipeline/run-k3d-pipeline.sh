#!/usr/bin/env bash
set -euo pipefail

script_dir="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")" && pwd)"
project_dir="$script_dir/.."
kubeconfig="$project_dir/deploy/k3d/.kubeconfig"

if [[ $# -gt 1 ]]; then
  printf 'Usage: %s [build-id]\n' "$0" >&2
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
kubectl --kubeconfig "$kubeconfig" -n hero-association get deployment core bff frontend >/dev/null

stage='build and archive'
printf 'Building Core, BFF, and frontend as %s\n' "$build_id"
"$script_dir/build-local.sh" all "$build_id"

stage='archive-backed browser E2E'
(
  cd "$project_dir/e2e"
  npm ci
  npm run test:archive -- "$artifact_dir"
)

stage='k3d promotion, browser and k6 checks'
node "$script_dir/deploy-k3d.mjs" "$artifact_dir"

printf 'Pipeline complete. Verified archive: %s\n' "$artifact_dir"
