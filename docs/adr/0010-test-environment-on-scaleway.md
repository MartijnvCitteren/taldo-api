# ADR 0010 — The test environment runs on Scaleway Serverless Containers

- **Status:** Accepted
- **Date:** 2026-11-28
- **Revises:** ADR 0009, section 4 (the test hostnames, left open there).

## Context

taldo-api, taldo-bff and Keycloak have only run on a developer's machine. A test environment has to show the
application to others and catch what local runs miss (TLS, real hostnames, a hosted database). Constraints from
`.claude/CLAUDE.md`: a European provider, EU only; low recurring costs; the code stays cloud-agnostic (configuration
from the environment, a standard container image, no cloud SDK outside an adapter). ADR 0007 already chose PostgreSQL 17
with Scaleway's managed database in mind, and a Scaleway Container Registry exists.

## Decision

### 1. Scaleway Serverless Containers, region Paris

Every component is its own Serverless Container in one namespace, in a Scaleway Project of its own for test, so test
and production never share credentials, networks or databases.

| Container | Endpoint | Scaling |
|-----------|----------|---------|
| Entry point, `app.test.taldo.eu` | public (custom domain) | to zero |
| `taldo-bff` | private only | to zero |
| `taldo-api` | private only | to zero |
| Keycloak, `auth.test.taldo.eu` | public (custom domain) | to zero, at most 1 instance |

The entry point serves the frontend and sends `/api/v1/**`, `/login`, `/login/callback` and `/logout` to the BFF, so
the browser sees one origin (ADR 0009, section 3). Until the frontend is deployed, it serves a placeholder at `/`.

### 2. A Private Network for everything that is not public

taldo-api and taldo-bff have their public endpoint switched off: only containers on the Private Network reach them,
which is what ADR 0009 asks of taldo-api. Calls go over HTTPS to the private endpoint, whose certificate is signed by
Scaleway's private-endpoint CA; the applications trust it through `TRUSTED_CA_CERTS_BASE64`
(`docs/application/deployment.md`).

### 3. Managed PostgreSQL on the same Private Network

One DB-DEV-S node (2 vCPU, 2 GB), PostgreSQL 17, without a public endpoint, holding `taldo_db`, `keycloak_db` and
`taldo_bff_db`. Connections use TLS with `sslmode=verify-full` (ADR 0007), with the database's CA in
`TRUSTED_CA_CERTS_BASE64`.

### 4. Hostnames `app.test.taldo.eu` and `auth.test.taldo.eu`

A subzone of the production domain (maintainer decision), not the separate registrable domain ADR 0009 advised.

**Accepted risk.** For a browser, everything under `taldo.eu` is one site. A script that runs on the test
environment (e.g. through a bug already fixed in production) can set a cookie for `.taldo.eu`, which production then
receives, and its requests to production count as same-site, so `SameSite=Strict` does not stop them. The session
cookie is safe from such a cookie (`__Host-` prefix), but the CSRF cookie `XSRF-TOKEN` is not: an attacker who fixes
its value can send a state-changing request from test that production accepts. Accepted while the test environment
holds no customer data; reconsidered before it does, or before production goes live. `app.test.taldo.eu` was chosen
over `test.app.taldo.eu` so that test does not sit under production's host name.

### 5. Keycloak scales to zero, as a single instance

Keycloak instances form a cluster by talking to each other directly, which Serverless Containers do not allow; so at
most one instance, with a local cache. It scales to zero to keep costs near zero: a login after a quiet period waits
for Keycloak to start (tens of seconds). User sessions survive, because Keycloak 26 keeps them in its database.

### 6. One image per application, configured by the environment

The same image runs in test and later in production; profiles are not used to tell environments apart
(`docs/application/deployment.md`). Secrets are secret environment variables of the container. The application holds
no Scaleway-specific code or configuration: the CAs and addresses come in through generic variables.

### 7. Mail through Scaleway Transactional Email

Keycloak sends the verification and password-reset mails through Scaleway Transactional Email over plain SMTP with
STARTTLS (`KEYCLOAK_SMTP_*`, `docs/application/deployment.md`), from an address under `test.taldo.eu`, whose DNS gets the
records Scaleway asks for to verify the domain. SMTP keeps Keycloak free of any provider API: another mail service only
changes the variables. Billed per mail; a test environment sends a handful.

### 8. Created by hand, following a runbook

The resources (Project, Private Network, database, namespace, containers, domains, secrets) are created in the
Scaleway console from a step-by-step page in `docs/`. Fastest to a working environment; the cost is that changes are not
recorded in code. Infrastructure as code (OpenTofu) is reconsidered for production.

## Alternatives considered

- **One Instance (VM) running Docker Compose with a reverse proxy.** The cheapest always-on option (one VM), fully
  provider-neutral, and closest to local development. Rejected by the maintainer in favour of managed, scale-to-zero
  containers: no VM to patch, and almost no cost while nobody uses test.
- **Kapsule (managed Kubernetes).** The most flexible, but too heavy and too expensive for a test environment now.
- **PostgreSQL in a container next to the applications.** No extra cost, but Serverless Containers have no persistent
  disk, and the test environment should run the hosted database production will run.
- **Keycloak always on** (minimum one instance). Instant logins, at roughly €18–35 a month. Rejected for test; the
  right choice for production.
- **A separate domain for test** (e.g. `taldo-test.eu`), ADR 0009's advice. Removes the risk in section 4 for about €10
  a year. Not chosen for now.

## Consequences

- **Running cost** (list prices of 2026-11-28): the database is about €11.40 a month plus storage (€0.10 per GB) and
  backups. Containers are billed per second of use (€1.00 per 100k vCPU-seconds, €0.20 per 100k GB-seconds) after a
  monthly free tier of 200k vCPU-seconds and 400k GB-seconds; with occasional use of test that is a few euros at most.
- **Cold starts.** After a quiet period the first request starts the containers it needs: taldo-api started in about
  5 seconds in a local check of its image, Keycloak takes tens of seconds.
- **The BFF needs Keycloak when it starts**: it reads the issuer's discovery document at startup, with a 30-second
  connect and read timeout (Spring Security's `ClientRegistrations`). When Keycloak has scaled to zero, that request
  itself starts it; Keycloak's image started in about 11 seconds in a local check, plus the creation of its schema on
  the very first start. This holds only if the platform keeps a request waiting while it starts a container, which is
  checked when the environment is set up. If it does not, the BFF fails its start until Keycloak runs; the fallback is
  a minimum of one Keycloak instance (the alternative "Keycloak always on").
- **Work outside requests.** The scheduled clean-up of generations runs only while a taldo-api instance runs. A
  description generation runs after the request that started it; the frontend polls for its result, which keeps the
  instance busy until it is done.
- **Network.** taldo-api is not reachable from the internet. For debugging, a developer reaches it only through the
  BFF, like the browser does.
