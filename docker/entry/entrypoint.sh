#!/bin/sh
# Starts the entry point (docker/entry/Dockerfile).
#
# TRUSTED_CA_CERTS_BASE64, when set, adds certificate authorities to trust next to the system's own, as in the other
# images: a base64-encoded PEM file. The entry point needs the CA of the BFF's private endpoint. Caddy is written in Go,
# which reads extra root certificates from the directories in SSL_CERT_DIR.
set -eu

if [ -n "${TRUSTED_CA_CERTS_BASE64:-}" ]; then
  trusted_dir=$(mktemp -d)
  printf '%s' "$TRUSTED_CA_CERTS_BASE64" | base64 -d > "$trusted_dir/trusted-cas.pem"
  if ! grep -q -- '-----BEGIN CERTIFICATE-----' "$trusted_dir/trusted-cas.pem"; then
    echo "TRUSTED_CA_CERTS_BASE64 holds no PEM certificate" >&2
    exit 1
  fi
  export SSL_CERT_DIR="$trusted_dir:/etc/ssl/certs"
fi

: "${TALDO_BFF_URL:?Set TALDO_BFF_URL to the BFF's address, e.g. its private endpoint}"
exec caddy run --config /etc/caddy/Caddyfile --adapter caddyfile
