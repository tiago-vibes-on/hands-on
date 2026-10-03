#!/usr/bin/env bash
set -euo pipefail

script_dir="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")" && pwd)"
project_dir="$script_dir/.."
map_enabled="${HERO_ASSOCIATION_FRONTEND_MAP_ENABLED:-true}"
if [[ "$map_enabled" != true && "$map_enabled" != false ]]; then
  printf 'HERO_ASSOCIATION_FRONTEND_MAP_ENABLED must be true or false.\n' >&2
  exit 2
fi

usage() {
  printf 'Usage: %s [all|core|bff|expedition|market|assets|world|quest|frontend] [build-id]\n' "$0" >&2
  exit 2
}

if [[ $# -gt 2 ]]; then
  usage
fi

component="${1:-all}"
case "$component" in
  all) services=(core bff expedition market assets world quest frontend) ;;
  core|bff|expedition|market|assets|world|quest|frontend) services=("$component") ;;
  *) usage ;;
esac

source_revision="$(git -C "$project_dir" rev-parse HEAD)"
build_id="${2:-local-$(date -u +%Y%m%dT%H%M%S)-${source_revision:0:8}}"
if [[ ! "$build_id" =~ ^[A-Za-z0-9_][A-Za-z0-9_.-]{0,127}$ ]]; then
  printf 'Invalid Docker image tag: %s\n' "$build_id" >&2
  usage
fi

artifact_dir="$script_dir/artifacts/$build_id/$component"
if [[ -e "$artifact_dir" ]]; then
  printf 'Build artifact already exists: %s\n' "$artifact_dir" >&2
  exit 1
fi

image_refs=()
for service in "${services[@]}"; do
  image_ref="hero-association-$service:$build_id"
  if docker image inspect "$image_ref" >/dev/null 2>&1; then
    printf 'Image tag already exists; choose a new build ID: %s\n' "$image_ref" >&2
    exit 1
  fi
  image_refs+=("$image_ref")
done

# Candidate images and component tests use disposable dependencies, not daily app Pods.
# Promotion still requires full k3d through its own preflight.
if [[ "$component" != frontend ]]; then
  "$project_dir/deploy/k3d/test-isolated-components.sh" "$component"
fi

for service in "${services[@]}"; do
  case "$service" in
    core)
      backend_dir="$project_dir/backend"
      (cd "$backend_dir" && ./mvnw --batch-mode -pl hero-association-core -am -DskipTests package)
      docker build --file "$backend_dir/hero-association-core/Dockerfile" --tag "hero-association-core:$build_id" "$backend_dir"
      ;;
    bff)
      module_dir="$project_dir/backend/hero-association-$service"
      (cd "$module_dir" && ./mvnw --batch-mode -DskipTests package)
      docker build --tag "hero-association-$service:$build_id" "$module_dir"
      ;;
    expedition|market|assets|world|quest)
      backend_dir="$project_dir/backend"
      (cd "$backend_dir" && ./mvnw --batch-mode -pl "hero-association-$service" -am -DskipTests package)
      docker build --file "$backend_dir/hero-association-$service/Dockerfile" --tag "hero-association-$service:$build_id" "$backend_dir"
      ;;
    frontend)
      (cd "$project_dir/frontend" && npm ci && npm run lint && VITE_EXPEDITION_ENABLED="$map_enabled" npm run build)
      docker build --build-arg "VITE_EXPEDITION_ENABLED=$map_enabled" \
        --tag "hero-association-frontend:$build_id" "$project_dir/frontend"
      ;;
  esac
done

mkdir -p "$artifact_dir"
docker save --output "$artifact_dir/images.tar" "${image_refs[@]}"
(
  cd "$artifact_dir"
  sha256sum images.tar > images.tar.sha256
)

if [[ -n "$(git -C "$project_dir" status --porcelain --untracked-files=normal)" ]]; then
  source_dirty=true
else
  source_dirty=false
fi

{
  printf 'build_id=%s\n' "$build_id"
  printf 'component=%s\n' "$component"
  printf 'source_revision=%s\n' "$source_revision"
  printf 'source_worktree_dirty=%s\n' "$source_dirty"
  for image_ref in "${image_refs[@]}"; do
    printf 'image=%s %s\n' "$image_ref" "$(docker image inspect --format '{{.Id}}' "$image_ref")"
  done
  printf 'archive_sha256=%s\n' "$(cut -d ' ' -f 1 "$artifact_dir/images.tar.sha256")"
} > "$artifact_dir/manifest.txt"

printf 'Build artifact: %s\n' "$artifact_dir"
