#!/bin/sh
# Starts the application in the image (docker/java/Dockerfile).
#
# TRUSTED_CA_CERTS_BASE64, when set, adds certificate authorities to trust next to the JDK's own: a base64-encoded
# PEM file with one or more certificates. A hosted environment needs them for TLS inside its private network, which
# public CAs do not sign: the managed database (sslmode=verify-full) and the private endpoints of other containers.
# Base64, because not every platform keeps the line breaks of an environment variable. The certificates come from the
# environment, not the image, so the same image runs everywhere.
#
# The heap may use 75% of the container's memory; the JVM's default of 25% wastes most of a small container. Extra
# JVM flags go in JAVA_TOOL_OPTIONS, which the JVM reads itself.
set -eu

if [ -n "${TRUSTED_CA_CERTS_BASE64:-}" ]; then
  work_dir=$(mktemp -d)
  truststore="$work_dir/truststore.p12"
  cp "$JAVA_HOME/lib/security/cacerts" "$truststore"
  printf '%s' "$TRUSTED_CA_CERTS_BASE64" | base64 -d \
    | awk -v dir="$work_dir" '/-----BEGIN CERTIFICATE-----/ { count++ } count { print > (dir "/ca-" count ".pem") }'
  for certificate in "$work_dir"/ca-*.pem; do
    if [ ! -f "$certificate" ]; then
      echo "TRUSTED_CA_CERTS_BASE64 holds no PEM certificate" >&2
      exit 1
    fi
    keytool -importcert -noprompt -keystore "$truststore" -storepass changeit \
      -alias "trusted-$(basename "$certificate" .pem)" -file "$certificate" >/dev/null
  done
  echo "Trusting $(find "$work_dir" -name 'ca-*.pem' | wc -l) extra certificate authorities"
  exec java -XX:MaxRAMPercentage=75.0 \
    -Djavax.net.ssl.trustStore="$truststore" -Djavax.net.ssl.trustStorePassword=changeit \
    -jar /application/application.jar
fi

exec java -XX:MaxRAMPercentage=75.0 -jar /application/application.jar
