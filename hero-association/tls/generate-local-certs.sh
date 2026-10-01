#!/usr/bin/env bash
set -euo pipefail
umask 077

script_dir="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")" && pwd)"
cert_dir="$script_dir/certs"
mkdir -p "$cert_dir"

verify_leaf() {
  openssl verify -CAfile "$cert_dir/local-ca.crt" \
    -verify_hostname heroassociation.test "$cert_dir/local.crt"
  openssl verify -CAfile "$cert_dir/local-ca.crt" \
    -verify_hostname auth.heroassociation.test "$cert_dir/local.crt"
  openssl x509 -in "$cert_dir/local.crt" -checkend 86400 -noout
}

if [[ -f "$cert_dir/local-ca.crt" && -f "$cert_dir/local-ca.key" &&
      -f "$cert_dir/local.crt" && -f "$cert_dir/local.key" ]]; then
  verify_leaf
  exit 0
fi
if compgen -G "$cert_dir/*.crt" >/dev/null || compgen -G "$cert_dir/*.key" >/dev/null; then
  printf 'Incomplete certificate set in %s; inspect it before retrying.\n' "$cert_dir" >&2
  exit 1
fi

temp_dir="$(mktemp -d)"
trap 'rm -f -- "$temp_dir/local.csr" "$temp_dir/ca.srl"; rmdir "$temp_dir"' EXIT
openssl req -x509 -newkey rsa:3072 -sha256 -nodes -days 3650 \
  -subj '/CN=Hero Association local development CA' \
  -keyout "$cert_dir/local-ca.key" -out "$cert_dir/local-ca.crt" \
  -addext 'basicConstraints=critical,CA:TRUE' \
  -addext 'keyUsage=critical,keyCertSign,cRLSign'
openssl req -new -newkey rsa:3072 -sha256 -nodes \
  -subj '/CN=heroassociation.test' \
  -keyout "$cert_dir/local.key" -out "$temp_dir/local.csr"
openssl x509 -req -in "$temp_dir/local.csr" \
  -CA "$cert_dir/local-ca.crt" -CAkey "$cert_dir/local-ca.key" \
  -CAserial "$temp_dir/ca.srl" -CAcreateserial \
  -out "$cert_dir/local.crt" -days 825 -sha256 \
  -extfile <(printf '%s\n' \
    'subjectAltName=DNS:heroassociation.test,DNS:auth.heroassociation.test' \
    'extendedKeyUsage=serverAuth' \
    'keyUsage=critical,digitalSignature,keyEncipherment')
verify_leaf
