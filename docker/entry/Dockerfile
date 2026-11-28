# The public entry point of a hosted environment: Caddy, serving the frontend and the BFF on one origin
# (docker/entry/Caddyfile). Build from the repository root (docs/application/deployment.md):
#
#   docker build --platform linux/amd64 -f docker/entry/Dockerfile -t taldo-entry .
#
# Until the frontend is deployed it serves a placeholder page (docker/entry/placeholder/), which shows whether the
# browser is logged in, so the whole chain can be checked: entry point, BFF, Keycloak and taldo-api.

ARG CADDY_IMAGE=caddy:2.11.9-alpine
FROM ${CADDY_IMAGE}
# A user without a shell or home directory; Caddy only writes to its data and config directories.
RUN adduser -S -H -D -s /sbin/nologin taldo \
    && chown taldo /data /config
COPY docker/entry/Caddyfile /etc/caddy/Caddyfile
COPY docker/entry/placeholder/ /srv/
COPY --chmod=755 docker/entry/entrypoint.sh /usr/local/bin/entrypoint.sh
USER taldo
ENTRYPOINT ["/usr/local/bin/entrypoint.sh"]
