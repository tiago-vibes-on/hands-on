#!/usr/bin/env bash
set -euo pipefail
umask 077

script_dir="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")" && pwd)"
cert_dir="$script_dir/certs"
"$script_dir/generate-local-certs.sh" >/dev/null

verify_leaf() {
  openssl verify -CAfile "$cert_dir/local-ca.crt" \
    -verify_hostname app.e2e.heroassociation.test "$cert_dir/e2e.crt"
  openssl verify -CAfile "$cert_dir/local-ca.crt" \
    -verify_hostname auth.e2e.heroassociation.test "$cert_dir/e2e.crt"
  openssl x509 -in "$cert_dir/e2e.crt" -checkend 86400 -noout
}

if [[ -f "$cert_dir/e2e.crt" && -f "$cert_dir/e2e.key" ]]; then
  verify_leaf
  exit 0
fi
if [[ -e "$cert_dir/e2e.crt" || -e "$cert_dir/e2e.key" ]]; then
  printf 'Incomplete E2E certificate pair in %s; inspect it before retrying.\n' "$cert_dir" >&2
  exit 1
fi

temp_dir="$(mktemp -d)"
trap 'rm -f -- "$temp_dir/e2e.csr" "$temp_dir/ca.srl"; rmdir "$temp_dir"' EXIT
openssl req -new -newkey rsa:3072 -sha256 -nodes \
  -subj '/CN=app.e2e.heroassociation.test' \
  -keyout "$cert_dir/e2e.key" -out "$temp_dir/e2e.csr"
openssl x509 -req -in "$temp_dir/e2e.csr" \
  -CA "$cert_dir/local-ca.crt" -CAkey "$cert_dir/local-ca.key" \
  -CAserial "$temp_dir/ca.srl" -CAcreateserial \
  -out "$cert_dir/e2e.crt" -days 825 -sha256 \
  -extfile <(printf '%s\n' \
    'subjectAltName=DNS:app.e2e.heroassociation.test,DNS:auth.e2e.heroassociation.test' \
    'extendedKeyUsage=serverAuth' \
    'keyUsage=critical,digitalSignature,keyEncipherment')
verify_leaf
