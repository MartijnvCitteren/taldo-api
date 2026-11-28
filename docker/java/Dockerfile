# The image of both Spring Boot applications, taldo-api and taldo-bff. Build the jar first, then build from the
# repository root (docs/application/deployment.md):
#
#   ./mvnw -pl taldo-api -am package
#   docker build --platform linux/amd64 -f docker/java/Dockerfile --build-arg MODULE=taldo-api -t taldo-api .
#
# One image runs in every environment: everything that differs per environment comes from environment variables.

ARG JAVA_IMAGE=eclipse-temurin:25.0.5.1_2-jre-noble

# Unpacks the jar on the build machine's own platform, so a build for another platform does not emulate this step.
FROM --platform=$BUILDPLATFORM ${JAVA_IMAGE} AS extract
ARG MODULE
RUN test -n "${MODULE}" || { echo "Pass the application as --build-arg MODULE=taldo-api or taldo-bff" >&2; exit 1; }
WORKDIR /build
COPY ${MODULE}/target/${MODULE}-*.jar application.jar
RUN java -Djarmode=tools -jar application.jar extract --layers --destination extracted

FROM ${JAVA_IMAGE}
# A user without a shell or home directory; the application files stay owned by root, so it cannot change them.
RUN useradd --system --no-create-home --shell /usr/sbin/nologin taldo
WORKDIR /application
# From least to most often changed, so a code change only replaces the last layer.
COPY --from=extract /build/extracted/dependencies/ ./
COPY --from=extract /build/extracted/spring-boot-loader/ ./
COPY --from=extract /build/extracted/snapshot-dependencies/ ./
COPY --from=extract /build/extracted/application/ ./
COPY --chmod=755 docker/java/entrypoint.sh /usr/local/bin/entrypoint.sh
USER taldo
ENTRYPOINT ["/usr/local/bin/entrypoint.sh"]
