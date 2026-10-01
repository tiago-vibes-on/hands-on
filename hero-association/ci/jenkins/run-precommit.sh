#!/usr/bin/env bash
set -euo pipefail

[[ -n "${HERO_ASSOCIATION_SOURCE_REPO:-}" ]] ||
  { printf 'The Jenkins agent is missing HERO_ASSOCIATION_SOURCE_REPO\n' >&2; exit 1; }
[[ -n "${WORKSPACE:-}" && -d "$WORKSPACE" ]] ||
  { printf 'This job requires a Jenkins workspace\n' >&2; exit 1; }
[[ "${BUILD_NUMBER:-}" =~ ^[0-9]+$ ]] ||
  { printf 'This job requires a numeric Jenkins build number\n' >&2; exit 1; }

source_repo="$(realpath -- "$HERO_ASSOCIATION_SOURCE_REPO")"
workspace="$(realpath -- "$WORKSPACE")"
agent_workdir="$(realpath -- "$JENKINS_AGENT_WORKDIR")"
case "$workspace/" in
  "$agent_workdir/workspace/"*) ;;
  *) printf 'Refusing a workspace outside the Jenkins agent: %s\n' "$workspace" >&2; exit 1 ;;
esac

source_revision="$(git -C "$source_repo" rev-parse HEAD)"
build_id="precommit-${BUILD_NUMBER}-${source_revision:0:8}"
run_dir="$(mktemp -d "$workspace/snapshot-${BUILD_NUMBER}-XXXXXX")"
trap 'rm -rf -- "$run_dir"' EXIT
snapshot_repo="$run_dir/source"
evidence_dir="$workspace/evidence-${BUILD_NUMBER}"
[[ ! -e "$evidence_dir" ]] ||
  { printf 'Evidence directory already exists: %s\n' "$evidence_dir" >&2; exit 1; }
mkdir -p "$evidence_dir"

"$source_repo/hero-association/ci/jenkins/snapshot-worktree.test.sh"
"$source_repo/hero-association/ci/jenkins/snapshot-worktree.sh" "$source_repo" "$snapshot_repo"
[[ "$(git -C "$snapshot_repo" rev-parse HEAD)" == "$source_revision" ]] ||
  { printf 'Source HEAD changed during snapshot\n' >&2; exit 1; }

if [[ -n "$(git -C "$snapshot_repo" status --porcelain --untracked-files=normal)" ]]; then
  source_dirty=true
else
  source_dirty=false
fi
{
  printf 'build_id=%s\n' "$build_id"
  printf 'source_revision=%s\n' "$source_revision"
  printf 'source_worktree_dirty=%s\n' "$source_dirty"
  printf 'snapshot_type=tracked_and_nonignored_untracked_files\n'
  printf 'deployment=none\n'
} > "$evidence_dir/snapshot.txt"
printf 'Pre-commit snapshot: %s (dirty=%s)\n' "$source_revision" "$source_dirty"

node --test "$snapshot_repo/hero-association/pipeline/rollback-k3d.test.mjs"
"$snapshot_repo/hero-association/pipeline/build-local.sh" all "$build_id"

artifact_dir="$snapshot_repo/hero-association/pipeline/artifacts/$build_id/all"
cp "$artifact_dir/manifest.txt" "$evidence_dir/manifest.txt"
(
  cd "$snapshot_repo/hero-association/e2e"
  npm ci
)
HERO_ASSOCIATION_LOCAL_CA_CERTIFICATE="${HERO_ASSOCIATION_LOCAL_CA_CERTIFICATE:-$source_repo/hero-association/tls/certs/local-ca.crt}" \
  "$snapshot_repo/hero-association/deploy/k3d/test-isolated-stack.sh" "$artifact_dir"
cp "$artifact_dir/k3d-e2e-verification.json" "$evidence_dir/k3d-e2e-verification.json"
printf 'Pre-commit build and disposable k3d E2E passed; nothing was deployed.\n'
