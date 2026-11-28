# Runbook: the test environment on Scaleway

Sets up the test environment of ADR 0010 by hand, in the Scaleway console: `app.test.taldo.eu` (entry point and BFF)
and `auth.test.taldo.eu` (Keycloak), with taldo-api and the BFF on a Private Network only. The images and their
variables are described in [deployment](../application/deployment.md); this page is the order of work. Steps marked
**Check** are things nobody has seen on Scaleway yet: note the outcome in this runbook or ADR 0010.

Everything lives in region **Paris (`fr-par`)**, in one Scaleway Project of its own.

## What you need

- Owner rights in the Scaleway Organization, and access to the DNS of `taldo.eu`.
- Docker, the JDK 25 build of this repository, and taldo-web next to it (for the Keycloak theme).
- A Mistral API key for the test environment.
- Secrets generated once and kept in a password manager (never in a file in the repository):

  ```
  openssl rand -base64 36   # one each: TALDO_WEB_CLIENT_SECRET, TALDO_BACKEND_CLIENT_SECRET, Keycloak admin password
  ```

## 1. Project and API keys

1. Create the Project `taldo-test`. Every resource below goes in it.
2. IAM: create an application `taldo-test-deploy` with access to Container Registry and Serverless Containers in this
   Project only, and an API key for it. You push images with this key.
3. IAM: create an application `taldo-test-smtp` with Transactional Email sending rights in this Project only, and an
   API key for it. Its secret key is Keycloak's SMTP password.

## 2. Private Network

VPC → create a Private Network `taldo-test` (no dots, no top-level domain as name). The default CIDR is plenty: each
running container instance takes an address, twice during a redeploy.

## 3. Managed PostgreSQL

1. Create a Database Instance: PostgreSQL 17, node `DB-DEV-S`, 10 GB Block Storage, no High Availability. Attach it to
   the Private Network `taldo-test` and remove the public endpoint, so it is reachable from the Private Network only.
2. Databases: `taldo_db`, `keycloak_db`, `taldo_bff_db`.
3. Users, each with permission **All** on its own database only: `taldo_api` → `taldo_db`, `keycloak` → `keycloak_db`,
   `taldo_bff` → `taldo_bff_db`. Hibernate (`ddl-auto: update`), Keycloak and Spring Session create their tables at
   startup, so the user must be allowed to create tables in the `public` schema.
4. Renew the TLS certificate (after the Private Network was attached, so it covers that endpoint), then download it as
   `db-ca.pem`.
5. **Check** which host names the certificate holds, and use one of those in every JDBC URL (`sslmode=verify-full`
   compares the host in the URL with the certificate):

   ```
   openssl x509 -in db-ca.pem -noout -ext subjectAltName
   ```

   The database's name on the Private Network is `<instance-name>.taldo-test.internal`; its private IP is on the
   instance's Private Network tab.

## 4. Mail: Transactional Email

1. Transactional Email → add the domain `test.taldo.eu` and create at your DNS provider the records Scaleway lists
   (SPF, DKIM, and the recommended MX and DMARC). Wait until the domain is verified.
2. Keycloak sends from `no-reply@test.taldo.eu` through `smtp.tem.scaleway.com:587` with STARTTLS; the username is the
   Project ID, the password the secret key of `taldo-test-smtp`. Serverless Containers may only reach SMTP ports of
   Transactional Email.

## 5. Images

Container Registry → create a **private** namespace `taldo-test`. Then, from the repository root on a clean, merged
`main`:

```
REGISTRY=rg.fr-par.scw.cloud/taldo-test
TAG=$(git rev-parse --short HEAD)
echo "$SCW_SECRET_KEY" | docker login "$REGISTRY" -u nologin --password-stdin   # key of taldo-test-deploy

./mvnw package
(cd ../taldo-web && npm run build-keycloak-theme)
docker build --platform linux/amd64 -f docker/java/Dockerfile --build-arg MODULE=taldo-api -t "$REGISTRY/taldo-api:$TAG" .
docker build --platform linux/amd64 -f docker/java/Dockerfile --build-arg MODULE=taldo-bff -t "$REGISTRY/taldo-bff:$TAG" .
docker build --platform linux/amd64 -f docker/keycloak/Dockerfile \
  --build-context taldo-keycloak-theme=../taldo-web/dist_keycloak -t "$REGISTRY/taldo-keycloak:$TAG" .
docker build --platform linux/amd64 -f docker/entry/Dockerfile -t "$REGISTRY/taldo-entry:$TAG" .
for image in taldo-api taldo-bff taldo-keycloak taldo-entry; do docker push "$REGISTRY/$image:$TAG"; done
```

## 6. Containers

Serverless → Containers → create a namespace `taldo-test`. Create the containers in this order: each needs an address
of the one before. Put every password, secret and key under **secrets**, the rest under environment variables.

Every container is attached to the Private Network `taldo-test`. Download the **private endpoint CA** once (a
container's Network & Domains tab, or the Serverless Containers API) as `private-endpoint-ca.pem`. Base64 values are
made with `base64 < file | tr -d '\n'`.

### 6.1 Keycloak

Image `taldo-keycloak`, port 8080, 1 vCPU, 2 GB, scaling min 0 / **max 1** (no cluster, ADR 0010), public endpoint on.

| Variable | Value |
|----------|-------|
| `KC_HOSTNAME` | `https://auth.test.taldo.eu` |
| `KC_DB_URL` | `jdbc:postgresql://<db host from step 3>:5432/keycloak_db?sslmode=verify-full&sslfactory=org.postgresql.ssl.DefaultJavaSSLFactory` |
| `KC_DB_USERNAME` / `KC_DB_PASSWORD` (secret) | `keycloak` / its password |
| `TRUSTED_CA_CERTS_BASE64` | base64 of `db-ca.pem` |
| `KC_BOOTSTRAP_ADMIN_USERNAME` / `KC_BOOTSTRAP_ADMIN_PASSWORD` (secret) | an admin name / the generated password; keep this user, the start script logs in with it |
| `TALDO_WEB_CLIENT_SECRET`, `TALDO_BACKEND_CLIENT_SECRET` (secrets) | the generated client secrets |
| `TALDO_WEB_BASE_URL` | `https://app.test.taldo.eu` |
| `KEYCLOAK_SMTP_HOST` / `_PORT` / `_STARTTLS` / `_AUTH` | `smtp.tem.scaleway.com` / `587` / `true` / `true` |
| `KEYCLOAK_SMTP_USER` / `KEYCLOAK_SMTP_PASSWORD` (secret) | the Project ID / the secret key of `taldo-test-smtp` |
| `KEYCLOAK_SMTP_FROM` | `no-reply@test.taldo.eu` |
| `KEYCLOAK_ACCESS_TOKEN_LIFESPAN_SECONDS` / `KEYCLOAK_SSO_SESSION_IDLE_SECONDS` / `KEYCLOAK_SSO_SESSION_MAX_SECONDS` | `600` / `86400` / `129600` |

Custom domain: create a CNAME `auth.test.taldo.eu` → the container's default endpoint at your DNS provider first, then
add the custom domain on the container; Scaleway gets the certificate once the CNAME resolves. The first start creates
Keycloak's schema and takes longer than later ones; the log ends with `Realm taldo configured`. **Check** that the
platform does not give up on that first start; if it does, raise the container's startup health-check timeout.

### 6.2 taldo-api

Image `taldo-api`, port 8090, 1 vCPU, 1 GB, scaling min 0 / max 1, **private endpoint on, public endpoint off**.

| Variable | Value |
|----------|-------|
| `SPRING_DATASOURCE_URL` | `jdbc:postgresql://<db host>:5432/taldo_db?sslmode=verify-full&sslfactory=org.postgresql.ssl.DefaultJavaSSLFactory` |
| `SPRING_DATASOURCE_USERNAME` / `SPRING_DATASOURCE_PASSWORD` (secret) | `taldo_api` / its password |
| `TRUSTED_CA_CERTS_BASE64` | base64 of `db-ca.pem` |
| `KEYCLOAK_BASE_URL` | `https://auth.test.taldo.eu` |
| `KEYCLOAK_CLIENT_SECRET` (secret) | `TALDO_BACKEND_CLIENT_SECRET` |
| `MISTRAL_API_KEY` (secret) | the test environment's key |

Note its private endpoint, `https://<container-id>.<private-network-id>.internal`.

### 6.3 taldo-bff

Image `taldo-bff`, port 8081, 1 vCPU, 1 GB, scaling min 0 / max 1, **private endpoint on, public endpoint off**.

| Variable | Value |
|----------|-------|
| `SPRING_DATASOURCE_URL` | `jdbc:postgresql://<db host>:5432/taldo_bff_db?sslmode=verify-full&sslfactory=org.postgresql.ssl.DefaultJavaSSLFactory` |
| `SPRING_DATASOURCE_USERNAME` / `SPRING_DATASOURCE_PASSWORD` (secret) | `taldo_bff` / its password |
| `TRUSTED_CA_CERTS_BASE64` | base64 of `db-ca.pem` and `private-endpoint-ca.pem` together (`cat db-ca.pem private-endpoint-ca.pem \| base64 \| tr -d '\n'`) |
| `KEYCLOAK_ISSUER_URI` | `https://auth.test.taldo.eu/realms/taldo` |
| `KEYCLOAK_CLIENT_SECRET` (secret) | `TALDO_WEB_CLIENT_SECRET` |
| `TALDO_API_BASE_URL` | taldo-api's private endpoint |

The BFF reads Keycloak's discovery document when it starts, so Keycloak must be reachable on its custom domain first.

### 6.4 Entry point

Image `taldo-entry`, port 8080, the smallest size (e.g. 0.25 vCPU, 256 MB), scaling min 0 / max 1, public endpoint on.

| Variable | Value |
|----------|-------|
| `TALDO_BFF_URL` | the BFF's private endpoint |
| `TRUSTED_CA_CERTS_BASE64` | base64 of `private-endpoint-ca.pem` |

Custom domain `app.test.taldo.eu`, the same way as Keycloak's.

## 7. Smoke test

1. `https://app.test.taldo.eu` shows the placeholder page: "You are not logged in".
2. **Log in** → Keycloak's login page in the Taldo theme on `auth.test.taldo.eu`. Register with a work e-mail address;
   the verification mail arrives (Transactional Email). After verifying you are back on the placeholder page: "You are
   logged in … Account status: ONBOARDING_REQUIRED" — the request went through the entry point, the BFF and taldo-api.
3. **Log out** → Keycloak's logout, back to "You are not logged in".
4. taldo-api and the BFF are not reachable from the internet: their default public endpoints do not answer.
5. **Check** the cold start (ADR 0010, consequences): leave the environment alone until every container has scaled to
   zero, then log in again. If the BFF fails to start because Keycloak was not up within 30 seconds, set Keycloak's
   minimum scale to 1 and record it in ADR 0010.

Logs are in Cockpit, per container.

## Deploying a new version

Build and push the images with the new commit's tag (step 5), then change the image tag of the containers that
changed and deploy them. Keycloak imports the realm on its first start only: a later change to
`docker/keycloak/taldo-realm.json` or to its placeholder variables (redirect URIs, SMTP) is made in the admin console
as well. Settings of `configure-realm.sh` (lifetimes, required actions, theme) are applied on every start.

## Costs

The database is the fixed cost (about €11.40 a month plus storage and backups); containers that scale to zero cost
little more than the free tier (ADR 0010). Deleting the database and the containers stops both; the registry only
bills its storage.
