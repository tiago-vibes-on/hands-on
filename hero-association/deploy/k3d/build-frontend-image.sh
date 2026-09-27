#!/usr/bin/env bash
set -euo pipefail

script_dir="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")" && pwd)"
frontend_dir="$script_dir/../../frontend"
k3d_bin="${K3D_BIN:-k3d}"

"$k3d_bin" cluster list hero-association >/dev/null

(cd "$frontend_dir" && npm ci && npm run lint && npm run build)
docker build -t hero-association-frontend:k3d "$frontend_dir"
"$k3d_bin" image import -c hero-association hero-association-frontend:k3d
