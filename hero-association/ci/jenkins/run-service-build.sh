#!/usr/bin/env bash
set -euo pipefail

if [[ $# -ne 2 ]]; then
  printf 'Usage: %s <worktree|main> <core|bff|expedition|market|assets|world|quest|frontend>\n' "$0" >&2
  exit 2
fi
mode="$1"
component="$2"
[[ "$mode" == worktree || "$mode" == main ]] || exit 2
[[ "$component" == core || "$component" == bff || "$component" == expedition || "$component" == market || "$component" == assets || "$component" == world || "$component" == quest || "$component" == frontend ]] || exit 2
[[ -n "${HERO_ASSOCIATION_SOURCE_REPO:-}" && -n "${JENKINS_AGENT_WORKDIR:-}" ]] ||
  { printf 'Missing Jenkins agent configuration\n' >&2; exit 1; }
[[ "${BUILD_NUMBER:-}" =~ ^[0-9]+$ && -n "${WORKSPACE:-}" ]] ||
  { printf 'Missing Jenkins build workspace or number\n' >&2; exit 1; }

workspace="$(realpath -- "$WORKSPACE")"
agent_workdir="$(realpath -- "$JENKINS_AGENT_WORKDIR")"
case "$workspace/" in
  "$agent_workdir/workspace/"*) ;;
  *) printf 'Workspace is outside the Jenkins agent\n' >&2; exit 1 ;;
esac

source_repo="$(realpath -- "$HERO_ASSOCIATION_SOURCE_REPO")"
"$source_repo/hero-association/deploy/k3d/require-full-k3d.sh"

if [[ "$mode" == main ]]; then
  build_repo="$workspace"
  [[ -z "$(git -C "$build_repo" status --porcelain --untracked-files=normal)" ]] ||
    { printf 'Main checkout must be clean\n' >&2; exit 1; }
else
  run_dir="$(mktemp -d "$workspace/snapshot-${BUILD_NUMBER}-XXXXXX")"
  trap 'rm -rf -- "$run_dir"' EXIT
  build_repo="$run_dir/source"
  "$source_repo/hero-association/ci/jenkins/snapshot-worktree.test.sh"
  "$source_repo/hero-association/ci/jenkins/snapshot-worktree.sh" "$source_repo" "$build_repo"
fi

revision="$(git -C "$build_repo" rev-parse HEAD)"
build_id="${mode}-${component}-${BUILD_NUMBER}-${revision:0:8}-$(date -u +%Y%m%dT%H%M%S)"
project="$build_repo/hero-association"
artifact="$agent_workdir/artifacts/$build_id/all"
candidate="$project/pipeline/artifacts/$build_id/$component"
evidence="$workspace/evidence"
mkdir -p "$evidence"

node --test "$project/pipeline/rollback-k3d.test.mjs" "$project/e2e/archive-images.test.js"
"$project/pipeline/build-local.sh" "$component" "$build_id"
node "$project/pipeline/assemble-service-archive.mjs" "$component" "$candidate" "$artifact"
node "$project/pipeline/deploy-k3d.mjs" --verify-baseline "$artifact"
(
  cd "$project/e2e"
  npm ci
)
HERO_ASSOCIATION_LOCAL_CA_CERTIFICATE="${HERO_ASSOCIATION_LOCAL_CA_CERTIFICATE:-$source_repo/hero-association/tls/certs/local-ca.crt}" \
  "$project/deploy/k3d/test-isolated-stack.sh" "$artifact"
cp "$artifact/manifest.txt" "$artifact/k3d-e2e-verification.json" "$evidence/"
printf 'artifact_id=%s\nsource_mode=%s\nsource_revision=%s\ncomponent=%s\n' \
  "$build_id" "$mode" "$revision" "$component" > "$evidence/build.txt"
printf '%s\n' "$build_id" > "$workspace/artifact-id.txt"
printf 'Verified %s artifact %s; deployment is a separate job.\n' "$component" "$build_id"
