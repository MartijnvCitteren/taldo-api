# Deployment

How taldo-api and taldo-bff are packaged and configured outside a developer's machine. Where they run is decided in
ADR 0010 (the test environment on Scaleway); this page covers what the code provides for any host.

## One image per application, the same in every environment

`docker/java/Dockerfile` builds the image of both applications from their jar (`--build-arg MODULE=taldo-api` or
`taldo-bff`). Test and production run the same image; everything that differs per environment comes from
environment variables, never from a profile or a file in the image.

```
./mvnw package
docker build --platform linux/amd64 -f docker/java/Dockerfile --build-arg MODULE=taldo-api -t taldo-api .
docker build --platform linux/amd64 -f docker/java/Dockerfile --build-arg MODULE=taldo-bff -t taldo-bff .
```

- **Runnable jar**: the root `pom.xml` binds Spring Boot's `repackage` goal (the reactor does not use
  `spring-boot-starter-parent`, which would). Without it the jar has no launcher and the image does not build.
- **Base image** `eclipse-temurin` JRE 25 on Ubuntu, pinned to an exact version in the Dockerfile. Dependabot does not
  watch it; bump it by hand, like the images in `docker-compose.yaml`.
- **Layers**: the jar is extracted with Spring Boot's `jarmode=tools` into dependencies, loader, snapshot dependencies
  and application, so a code change only replaces the last layer.
- **Platform**: `linux/amd64`, what the hosting runs on. The extract step runs on the build machine's own platform,
  so building on Apple Silicon only emulates the final image.
- **User**: the system user `taldo`, without a shell; the application files belong to root.
- **Memory**: the heap may take 75% of the container's memory (`-XX:MaxRAMPercentage=75.0`). Extra JVM flags go in
  `JAVA_TOOL_OPTIONS`.
- `.dockerignore` sends only the jars and `docker/java/` to the Docker daemon, so sources and a local `.env` never
  end up in the build context.

## Extra certificate authorities

`TRUSTED_CA_CERTS_BASE64` holds certificate authorities to trust next to the JDK's own: a base64-encoded PEM file with
one or more certificates. At startup `docker/java/entrypoint.sh` copies the JDK's truststore, adds them and starts the
JVM with that truststore. Inside a private network TLS is signed by the provider's own CA, which no public trust store
holds: the managed database and the private endpoints of other containers. Without the variable the JDK's truststore
is used as it is; a value without a PEM certificate stops the container at startup.

```
cat database-ca.pem private-endpoint-ca.pem | base64
```

## Configuration

Running without a profile is the hosted configuration: the base `application.yml` of each application holds the
settings for a hosted environment and reads the rest from the environment. The `local` profile only adds developer
defaults (Docker Compose addresses, SQL logging, `DEBUG` logs).

**Database (both applications).** `SPRING_DATASOURCE_URL`, `SPRING_DATASOURCE_USERNAME`, `SPRING_DATASOURCE_PASSWORD`.
Against a hosted database the URL verifies the server's certificate and host name (ADR 0007) with the truststore
above:

```
jdbc:postgresql://<host>:5432/taldo_db?sslmode=verify-full&sslfactory=org.postgresql.ssl.DefaultJavaSSLFactory
```

`DefaultJavaSSLFactory` makes the driver use the JVM's truststore; its default reads a CA file from the user's home
directory, which the image does not have. Verified with a PostgreSQL that accepts TLS only, with a certificate from a
private CA: the application starts with that CA in `TRUSTED_CA_CERTS_BASE64` and fails with `PKIX path building
failed` without it.

**taldo-api**: listens on 8090 under `/api/v1`. The other variables (`MISTRAL_API_KEY`, `KEYCLOAK_*`,
`CORS_ALLOWED_ORIGINS`) are in [overview](overview.md#configuration-and-running-locally). Application logs at `INFO`;
`LOGGING_LEVEL_APP` changes that.

**taldo-bff**: listens on `SERVER_PORT` (8081); its variables are in [bff](bff.md#configuration). `TALDO_API_BASE_URL`
points at taldo-api's private address.

## Running the images locally

`docker compose --profile apps up --build` builds both images from the jars in `target/` and runs them against the
local stack, on the same ports as when they run from the IDE. It checks an image before it is pushed: the
applications start, reach PostgreSQL and Keycloak, and the BFF relays to taldo-api. A browser login does not work this
way, because the BFF reaches Keycloak as `keycloak:8080`, which the browser cannot open; log in through the Vite dev
server with the applications on the `local` profile.

## Known gaps

- Images are built and pushed by hand; no CI workflow builds them yet.
- Keycloak has no image for a hosted environment yet: the Docker Compose service runs `start-dev` with the local
  realm (a public client and `localhost` redirect URIs).
- There is no entry point yet that serves the frontend and the BFF on one origin (ADR 0009, section 3).
- No HTTP health endpoint: the platform sees an application as ready once its port accepts connections, which Spring
  Boot opens only after the context has started.
