#!/usr/bin/env bash
set -euo pipefail

script_dir="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")" && pwd)"
project_dir="$script_dir/../.."
k3d_bin="${K3D_BIN:-k3d}"

if [[ $# -gt 1 ]]; then
  printf 'Usage: %s [core|bff]\n' "$0" >&2
  exit 2
fi
services=(core bff)
if [[ $# -eq 1 ]]; then
  case "$1" in
    core|bff) services=("$1") ;;
    *) printf 'Usage: %s [core|bff]\n' "$0" >&2; exit 2 ;;
  esac
fi

"$k3d_bin" cluster list hero-association >/dev/null

images=()
for service in "${services[@]}"; do
  module_dir="$project_dir/backend/hero-association-$service"
  (cd "$module_dir" && ./mvnw package)
  docker build -t "hero-association-$service:k3d" "$module_dir"
  images+=("hero-association-$service:k3d")
done

"$k3d_bin" image import -c hero-association "${images[@]}"
