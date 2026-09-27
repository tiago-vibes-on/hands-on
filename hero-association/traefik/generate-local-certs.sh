#!/usr/bin/env bash

set -euo pipefail
umask 077

cert_dir="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")" && pwd)/certs"
mkdir -p "$cert_dir"

if [[ -f "$cert_dir/local-ca.crt" && -f "$cert_dir/local-ca.key" && -f "$cert_dir/local.crt" && -f "$cert_dir/local.key" ]]; then
  exit 0
fi

if compgen -G "$cert_dir/*.crt" > /dev/null || compgen -G "$cert_dir/*.key" > /dev/null; then
  echo "Incomplete local certificate set in $cert_dir; inspect it before retrying." >&2
  exit 1
fi

openssl req -x509 -newkey rsa:3072 -sha256 -nodes -days 3650 \
  -subj '/CN=Hero Association local development CA' \
  -keyout "$cert_dir/local-ca.key" -out "$cert_dir/local-ca.crt" \
  -addext 'basicConstraints=critical,CA:TRUE' \
  -addext 'keyUsage=critical,keyCertSign,cRLSign'

openssl req -new -newkey rsa:3072 -sha256 -nodes \
  -subj '/CN=heroassociation.test' \
  -keyout "$cert_dir/local.key" -out "$cert_dir/local.csr"

openssl x509 -req -in "$cert_dir/local.csr" \
  -CA "$cert_dir/local-ca.crt" -CAkey "$cert_dir/local-ca.key" \
  -CAcreateserial -out "$cert_dir/local.crt" -days 825 -sha256 \
  -extfile <(printf '%s\n' \
    'subjectAltName=DNS:heroassociation.test,DNS:auth.heroassociation.test' \
    'extendedKeyUsage=serverAuth' \
    'keyUsage=critical,digitalSignature,keyEncipherment')

rm "$cert_dir/local.csr" "$cert_dir/local-ca.srl"
echo "Created local HTTPS certificate and CA in $cert_dir"
echo "Trust $cert_dir/local-ca.crt in your browser/OS to remove the certificate warning."
