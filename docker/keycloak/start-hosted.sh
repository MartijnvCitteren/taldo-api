#!/bin/bash
# Starts Keycloak in a hosted environment (docker/keycloak/Dockerfile) and applies configure-realm.sh once it serves the
# realm. A hosted platform has no one-shot containers, so the realm settings that the import cannot express are applied
# here, on every start, like the keycloak-config service does locally. When they fail, Keycloak is stopped: a realm
# without them lets users edit their verified name (see configure-realm.sh).
#
# Keycloak replaces this script as the container's main process (exec), so it receives the platform's stop signal
# itself and shuts down gracefully; the configuration runs next to it in the background.
#
# TRUSTED_CA_CERTS_BASE64, when set, holds certificate authorities to trust next to the JDK's own, as in the
# application images: a base64-encoded PEM file. Keycloak needs it for the managed database (sslmode=verify-full).
set -euo pipefail

if [[ -n "${TRUSTED_CA_CERTS_BASE64:-}" ]]; then
  printf '%s' "$TRUSTED_CA_CERTS_BASE64" | base64 -d > /tmp/trusted-cas.pem
  if ! grep -q -- '-----BEGIN CERTIFICATE-----' /tmp/trusted-cas.pem; then
    echo "TRUSTED_CA_CERTS_BASE64 holds no PEM certificate" >&2
    exit 1
  fi
  export KC_TRUSTSTORE_PATHS=/tmp/trusted-cas.pem
fi

# /health/ready reports UP before the realm import has finished; the realm's own endpoint answers 200 only after it.
serves_realm() {
  exec 3<>/dev/tcp/localhost/8080 \
    && printf 'GET /realms/taldo HTTP/1.1\r\nHost: localhost\r\nConnection: close\r\n\r\n' >&3 \
    && head -n 1 <&3 | grep -q ' 200 '
}

configure_when_ready() {
  local keycloak_pid=$1
  until serves_realm 2>/dev/null; do
    sleep 2
  done
  if ! KEYCLOAK_ADMIN_URL=http://localhost:8080 /opt/keycloak/configure-realm.sh; then
    echo "Configuring the realm failed; stopping Keycloak" >&2
    kill -TERM "$keycloak_pid"
  fi
}

# exec keeps this process's id, so the background job can stop Keycloak by it.
configure_when_ready $$ &
exec /opt/keycloak/bin/kc.sh start --optimized --import-realm
