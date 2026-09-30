#!/usr/bin/env bash
set -euo pipefail

script_dir="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")" && pwd)"
frontend_dir="$script_dir/../../frontend"
k3d_bin="${K3D_BIN:-k3d}"
map_enabled="${HERO_ASSOCIATION_K3D_MAP_ENABLED:-true}"
if [[ "$map_enabled" != true && "$map_enabled" != false ]]; then
  printf 'HERO_ASSOCIATION_K3D_MAP_ENABLED must be true or false.\n' >&2
  exit 2
fi

"$k3d_bin" cluster list hero-association >/dev/null

(cd "$frontend_dir" && npm ci && npm test && npm run lint && VITE_EXPEDITION_ENABLED="$map_enabled" npm run build)
docker build --build-arg "VITE_EXPEDITION_ENABLED=$map_enabled" \
  -t hero-association-frontend:k3d "$frontend_dir"
"$k3d_bin" image import -c hero-association hero-association-frontend:k3d
