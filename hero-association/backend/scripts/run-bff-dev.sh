#!/usr/bin/env bash

set -euo pipefail

backend_dir="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")/.." && pwd)"
environment_file="$backend_dir/.env"

if [[ ! -f "$environment_file" ]]; then
  echo "Missing backend/.env. Copy backend/.env.example and set the local secrets first." >&2
  exit 1
fi

set -a
source "$environment_file"
set +a

for variable in \
  HERO_ASSOCIATION_BFF_OIDC_CLIENT_SECRET \
  HERO_ASSOCIATION_BFF_OIDC_STATE_SECRET \
  HERO_ASSOCIATION_BFF_CSRF_TOKEN_SIGNATURE_KEY; do
  if [[ -z "${!variable:-}" || "${!variable}" == replace-with-* ]]; then
    echo "Set $variable in backend/.env before starting the BFF." >&2
    exit 1
  fi
done

cd "$backend_dir/hero-association-bff"
exec ./mvnw quarkus:dev "$@"
