#!/usr/bin/env bash
set -euo pipefail

if [[ $# -ne 2 ]]; then
  printf 'Usage: %s <worktree|main> <core|bff|frontend>\n' "$0" >&2
  exit 2
fi
mode="$1"
component="$2"
[[ "$mode" == worktree || "$mode" == main ]] || exit 2
[[ "$component" == core || "$component" == bff || "$component" == frontend ]] || exit 2
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
if [[ "$mode" == main && "${GIT_PREVIOUS_SUCCESSFUL_COMMIT:-}" =~ ^[a-f0-9]{40}$ ]] &&
   git -C "$build_repo" cat-file -e "$GIT_PREVIOUS_SUCCESSFUL_COMMIT^{commit}" 2>/dev/null; then
  relevant_change=false
  while IFS= read -r -d '' changed_file; do
    case "$changed_file" in
      .gitattributes|hero-association/ci/jenkins/*|hero-association/pipeline/*|hero-association/e2e/*|hero-association/deploy/k3d/*)
        relevant_change=true ;;
      hero-association/backend/hero-association-core/*)
        if [[ "$component" == core ]]; then relevant_change=true; fi ;;
      hero-association/backend/hero-association-bff/*)
        if [[ "$component" == bff ]]; then relevant_change=true; fi ;;
      hero-association/frontend/*)
        if [[ "$component" == frontend ]]; then relevant_change=true; fi ;;
      hero-association/backend/*)
        relevant_change=true ;;
    esac
  done < <(git -C "$build_repo" diff --name-only -z "$GIT_PREVIOUS_SUCCESSFUL_COMMIT" "$revision")
  if [[ "$relevant_change" == false ]]; then
    printf 'No %s or shared build changes since %s; no artifact or deployment.\n' \
      "$component" "$GIT_PREVIOUS_SUCCESSFUL_COMMIT"
    exit 0
  fi
fi
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
  npm run test:archive -- "$artifact"
)
cp "$artifact/manifest.txt" "$artifact/e2e-verification.json" "$evidence/"
printf 'artifact_id=%s\nsource_mode=%s\nsource_revision=%s\ncomponent=%s\n' \
  "$build_id" "$mode" "$revision" "$component" > "$evidence/build.txt"
printf '%s\n' "$build_id" > "$workspace/artifact-id.txt"
printf 'Verified %s artifact %s; deployment is a separate job.\n' "$component" "$build_id"
