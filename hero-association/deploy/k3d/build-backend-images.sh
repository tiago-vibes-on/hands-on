#!/usr/bin/env bash
set -euo pipefail

script_dir="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")" && pwd)"
project_dir="$script_dir/../.."
k3d_bin="${K3D_BIN:-k3d}"

if [[ $# -gt 1 ]]; then
  printf 'Usage: %s [core|bff|expedition]\n' "$0" >&2
  exit 2
fi
services=(core bff)
if [[ $# -eq 1 ]]; then
  case "$1" in
    core|bff|expedition) services=("$1") ;;
    *) printf 'Usage: %s [core|bff|expedition]\n' "$0" >&2; exit 2 ;;
  esac
fi

"$k3d_bin" cluster list hero-association >/dev/null

images=()
for service in "${services[@]}"; do
  if [[ "$service" == core || "$service" == expedition ]]; then
    backend_dir="$project_dir/backend"
    (cd "$backend_dir" && ./mvnw -pl "hero-association-$service" -am package)
    docker build -f "$backend_dir/hero-association-$service/Dockerfile" -t "hero-association-$service:k3d" "$backend_dir"
  else
    module_dir="$project_dir/backend/hero-association-$service"
    (cd "$module_dir" && ./mvnw package)
    docker build -t "hero-association-$service:k3d" "$module_dir"
  fi
  images+=("hero-association-$service:k3d")
done

"$k3d_bin" image import -c hero-association "${images[@]}"
