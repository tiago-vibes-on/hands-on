#!/usr/bin/env bash
set -euo pipefail

if [[ $# -ne 2 ]]; then
  printf 'Usage: %s <core|bff|expedition|market|assets|frontend> <artifact-id>\n' "$0" >&2
  exit 2
fi
component="$1"
artifact_id="$2"
[[ "$component" == core || "$component" == bff || "$component" == expedition || "$component" == market || "$component" == assets || "$component" == frontend ]] || exit 2
[[ "$artifact_id" =~ ^(worktree|main)-${component}-[A-Za-z0-9_.-]+$ ]] ||
  { printf 'Invalid %s artifact ID: %s\n' "$component" "$artifact_id" >&2; exit 1; }
[[ -n "${JENKINS_AGENT_WORKDIR:-}" && -n "${HERO_ASSOCIATION_SOURCE_REPO:-}" && -n "${WORKSPACE:-}" ]] ||
  { printf 'Missing Jenkins agent configuration\n' >&2; exit 1; }

agent_workdir="$(realpath -- "$JENKINS_AGENT_WORKDIR")"
artifact="$agent_workdir/artifacts/$artifact_id/all"
[[ -d "$artifact" ]] || { printf 'Missing verified artifact: %s\n' "$artifact" >&2; exit 1; }
[[ "$(realpath -- "$artifact")" == "$artifact" ]] ||
  { printf 'Artifact path must not be a symlink\n' >&2; exit 1; }
grep -qx "promote_component=${component}$" "$artifact/manifest.txt" ||
  { printf 'Artifact targets another service\n' >&2; exit 1; }

project="$(realpath -- "$HERO_ASSOCIATION_SOURCE_REPO")/hero-association"
node "$project/pipeline/deploy-k3d.mjs" "$artifact"
mkdir -p "$WORKSPACE/evidence"
cp "$artifact/manifest.txt" "$artifact/k3d-e2e-verification.json" "$artifact/k3d-promotion.json" "$WORKSPACE/evidence/"
printf 'Deployed %s artifact %s to k3d.\n' "$component" "$artifact_id"
