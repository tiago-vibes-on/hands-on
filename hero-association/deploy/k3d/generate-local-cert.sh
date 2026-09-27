#!/usr/bin/env bash
set -euo pipefail
umask 077

script_dir="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")" && pwd)"
ca_dir="$script_dir/../../traefik/certs"
cert_dir="$script_dir/certs"
mkdir -p "$cert_dir"

if [[ ! -f "$ca_dir/local-ca.crt" || ! -f "$ca_dir/local-ca.key" ]]; then
  printf 'Missing local CA. Run ../../traefik/generate-local-certs.sh first.\n' >&2
  exit 1
fi
verify_leaf() {
  openssl verify -CAfile "$ca_dir/local-ca.crt" \
    -verify_hostname k3d.heroassociation.test "$cert_dir/k3d.crt"
  openssl verify -CAfile "$ca_dir/local-ca.crt" \
    -verify_hostname auth.k3d.heroassociation.test "$cert_dir/k3d.crt"
  openssl x509 -in "$cert_dir/k3d.crt" -checkend 86400 -noout
}


if [[ -f "$cert_dir/k3d.crt" && -f "$cert_dir/k3d.key" ]]; then
  verify_leaf
  exit 0
fi
if [[ -e "$cert_dir/k3d.crt" || -e "$cert_dir/k3d.key" ]]; then
  printf 'Incomplete k3d certificate set in %s; inspect it before retrying.\n' "$cert_dir" >&2
  exit 1
fi

temp_dir="$(mktemp -d)"
trap 'rm -rf -- "$temp_dir"' EXIT
openssl req -new -newkey rsa:3072 -sha256 -nodes \
  -subj '/CN=k3d.heroassociation.test' \
  -keyout "$cert_dir/k3d.key" -out "$temp_dir/k3d.csr"
openssl x509 -req -in "$temp_dir/k3d.csr" \
  -CA "$ca_dir/local-ca.crt" -CAkey "$ca_dir/local-ca.key" \
  -CAserial "$temp_dir/ca.srl" -CAcreateserial \
  -out "$cert_dir/k3d.crt" -days 825 -sha256 \
  -extfile <(printf '%s\n' \
    'subjectAltName=DNS:k3d.heroassociation.test,DNS:auth.k3d.heroassociation.test' \
    'extendedKeyUsage=serverAuth' \
    'keyUsage=critical,digitalSignature,keyEncipherment')
verify_leaf
