#!/usr/bin/env bash
set -euo pipefail

script_dir="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")" && pwd)"
project_dir="$script_dir/../.."
k3d_bin="${K3D_BIN:-k3d}"

"$k3d_bin" cluster list hero-association >/dev/null

for service in core bff; do
  module_dir="$project_dir/backend/hero-association-$service"
  (cd "$module_dir" && ./mvnw package)
  docker build -t "hero-association-$service:k3d" "$module_dir"
done

"$k3d_bin" image import -c hero-association \
  hero-association-core:k3d hero-association-bff:k3d
