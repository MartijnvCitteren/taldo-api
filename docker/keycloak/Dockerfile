# Keycloak for a hosted environment (ADR 0010); local development keeps the stock image in development mode
# (docker-compose.yaml). The Taldo login theme is required: build it in taldo-web next to this repository
# (npm run build-keycloak-theme), then build from the repository root (docs/application/deployment.md):
#
#   docker build --platform linux/amd64 -f docker/keycloak/Dockerfile \
#     --build-context taldo-keycloak-theme=../taldo-web/dist_keycloak -t taldo-keycloak .
#
# The theme comes from that named build context. Without it the build fails: Docker then looks for an official image
# docker.io/library/taldo-keycloak-theme, which does not exist (only Docker publishes there). Not an empty default
# stage: BuildKit reused the cached copy of an earlier build with the theme for a build without it.

ARG KEYCLOAK_IMAGE=quay.io/keycloak/keycloak:26.8.1

FROM ${KEYCLOAK_IMAGE} AS build
# Build options are fixed in the optimized server. PostgreSQL (ADR 0007); health endpoints for the start script;
# a local cache, because Serverless Containers cannot reach each other to form a cluster: one instance at most.
ENV KC_DB=postgres \
    KC_HEALTH_ENABLED=true \
    KC_CACHE=local
COPY --from=taldo-keycloak-theme taldo-keycloak-theme.jar /opt/keycloak/providers/
RUN /opt/keycloak/bin/kc.sh build

FROM ${KEYCLOAK_IMAGE}
COPY --from=build /opt/keycloak/ /opt/keycloak/
COPY docker/keycloak/taldo-realm.json /opt/keycloak/data/import/taldo-realm.json
COPY --chmod=755 docker/keycloak/configure-realm.sh docker/keycloak/start-hosted.sh /opt/keycloak/
# TLS ends at the platform's gateway, which forwards plain HTTP and sets the X-Forwarded-* headers. The public URL
# comes from KC_HOSTNAME, set per environment, so tokens and links never depend on a request header.
# Password hashing as in docker-compose.yaml: RFC 9106's second recommended Argon2id option (ADR 0003, 0006).
ENV KC_HTTP_ENABLED=true \
    KC_PROXY_HEADERS=xforwarded \
    KC_SPI_PASSWORD_HASHING__ARGON2__MEMORY=65536 \
    KC_SPI_PASSWORD_HASHING__ARGON2__ITERATIONS=3 \
    KC_SPI_PASSWORD_HASHING__ARGON2__PARALLELISM=4
ENTRYPOINT ["/opt/keycloak/start-hosted.sh"]
