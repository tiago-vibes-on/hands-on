#!/usr/bin/env bash

set -euo pipefail

backend_dir="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$backend_dir"

"$backend_dir/../traefik/generate-local-certs.sh"

# Docker Desktop reaches a WSL host service through its WSL network address,
# not through host.docker.internal. Resolve it each time because WSL assigns it
# dynamically when the distribution starts. An explicit value supports other
# local environments and makes this script straightforward to test.
dev_host_address="${HERO_ASSOCIATION_DEV_HOST_ADDRESS:-$(hostname -I | awk '{print $1}')}"

if [[ -z "$dev_host_address" ]]; then
  echo "Could not determine the WSL host address. Set HERO_ASSOCIATION_DEV_HOST_ADDRESS and retry." >&2
  exit 1
fi

HERO_ASSOCIATION_DEV_HOST_ADDRESS="$dev_host_address" \
  docker compose -f compose.infra.yaml up --detach "$@"
