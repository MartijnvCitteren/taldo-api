# Backend-for-frontend (`taldo-bff`)

The browser's login and session (ADR 0009). The BFF logs the browser in with Keycloak as a confidential client and
keeps the tokens in a server-side session; the browser only holds an opaque session cookie. It has no business logic
and no data of its own. taldo-api does not know it exists: it keeps checking bearer tokens (ADR 0006).

This page covers login, the session, CSRF, logout and the relay of API calls to taldo-api.

## Endpoints

The frontend and the BFF share one origin (ADR 0009, section 3); the paths name neither the protocol nor the identity
provider (section 2). In a hosted environment the entry point (`docker/entry/`, see [deployment](deployment.md#entry-point))
sends exactly these paths to the BFF; locally the Vite dev server does.

| Path | What happens |
|------|--------------|
| `GET /login` | Starts authorization code + PKCE with Keycloak (`LoginRequestResolver`), client `taldo-web`, scope `openid`. A browser navigation, not a `fetch`. |
| `GET /login/callback` | Keycloak returns here; the code is exchanged with the client secret and the PKCE verifier. Success: `302` to `/` with a new session id. Failure (e.g. a `state` that belongs to no started login): `302` to `/?login=failed`, logged at `warn`. |
| `POST /logout` | Needs the CSRF header. Ends the session and answers `200 {"logoutUrl": "…"}`: Keycloak's end-session URL with `id_token_hint` and `post_logout_redirect_uri` `{baseUrl}/`. The frontend navigates there, so Keycloak's session ends too (`LogoutUrlResponder`). |
| `/api/v1/**` (any method) | Relayed to taldo-api with the user's access token (see "Relay"). |
| anything else | Needs a session; without one `401`, never a redirect. The frontend decides when to go to `/login`. |

**Why logout answers a URL instead of redirecting:** the frontend calls it with `fetch` (it needs the CSRF header), and
a redirect would be followed inside `fetch`, not in the address bar. The BFF session is already gone when the answer
arrives, so a skipped navigation leaves no usable session here; only Keycloak's own SSO session lives on until it
expires.

## Relay

`ApiRelayController` passes `/api/v1/**` on to taldo-api (`TALDO_API_BASE_URL`, without the context path) on the same
path with the raw query, and passes taldo-api's answer back as it is: status, body and only `Content-Type`, `Accept`
(the `415` answer names the accepted types) and `Retry-After`. It adds no rules of its own.

- **Requests** carry only `Content-Type`, `Accept`, `Accept-Language`, the body and `Authorization: Bearer` with the
  user's access token. The browser's cookies and the CSRF header never reach taldo-api; the token never reaches the
  browser.
- **`Location`** pointing into taldo-api becomes a path on the browser's origin (`/api/v1/vacancy/…`), because the
  browser cannot reach taldo-api's address.
- **Token renewal** (`AccessTokens`, `DefaultOAuth2AuthorizedClientManager`): an access token that has expired or
  expires within a minute is renewed with the refresh token before the call. The tokens are kept in the session
  (`HttpSessionOAuth2AuthorizedClientRepository`); Spring Boot's default would keep them in memory, which a restart
  loses and another instance cannot see.
- **Failures** tell a retry that can help from one that cannot:

  | Situation | Answer |
  |-----------|--------|
  | Keycloak refuses the refresh token (`invalid_grant`: its session ended, idle or 36-hour maximum) | `401`; the BFF session is invalidated, so the next call is `401` without asking Keycloak. Log in again. |
  | Keycloak cannot be asked (down, `5xx`) | `503` problem; the session stays, a retry may succeed. Logged at `warn`. |
  | taldo-api cannot be reached or times out (5 s connect, 15 s read) | `502` problem. Logged at `warn`. |
  | Any answer from taldo-api, `4xx` and `5xx` included | Passed on unchanged. |

## Session and cookies

- **Spring Session JDBC** on PostgreSQL (`taldo_bff_db`; tables created at startup), so every instance sees every
  session and a restart keeps users logged in. The tokens (`OAuth2AuthorizedClient`) are stored in the session,
  Java-serialized: the session table holds refresh tokens and must be protected like a secret store.
- **Idle timeout** `TALDO_SESSION_IDLE_TIMEOUT` (default `24h`): set it to the same value as Keycloak's SSO session
  idle (ADR 0006, revision 2026-11-21, point 6). There is no absolute limit in the BFF: Keycloak's 36-hour maximum ends
  the refresh token, which the relay turns into a `401`.
- **Session cookie** `__Host-session` (`TALDO_SESSION_COOKIE_NAME`): `Secure`, `HttpOnly`, `SameSite=Strict`, `Path=/`,
  no `Domain`. `SameSite=Strict` only works because Keycloak is on the same site as the app (`auth.taldo.eu` and
  `app.taldo.eu`, or `localhost` with another port): the callback from Keycloak is then a same-site navigation and
  carries the cookie that holds the started login. Keycloak on another site would break login.
- **CSRF**: Spring Security's single-page-app setup (`csrf.spa()`): the token is in the readable `XSRF-TOKEN` cookie
  and goes back in the `X-XSRF-TOKEN` header on every state-changing request.
- `server.forward-headers-strategy: framework`: behind the reverse proxy or the Vite dev server, the callback URL and
  the logout URL use the browser's host.
- `server.tomcat.servlet.use-relative-redirects: true`: our own redirects (`/` after login) are a relative `Location`,
  which the browser resolves against the host it is on.

## Configuration

| Variable | Default | Notes |
|----------|---------|-------|
| `KEYCLOAK_ISSUER_URI` | – (`local`: `http://localhost:8080/realms/taldo`) | Read at startup (discovery document, including `end_session_endpoint`): the BFF does not start while Keycloak is unreachable. |
| `KEYCLOAK_CLIENT_ID` | `taldo-web` | |
| `KEYCLOAK_CLIENT_SECRET` | – (`local`: the Docker Compose value) | Rotate on suspicion. |
| `SPRING_DATASOURCE_URL`, `_USERNAME`, `_PASSWORD` | – (`local`: `taldo_bff_db` in Docker Compose) | Sessions. |
| `TALDO_SESSION_IDLE_TIMEOUT` | `24h` | Keep equal to Keycloak's SSO session idle. |
| `TALDO_SESSION_COOKIE_NAME` | `__Host-session` | |
| `TALDO_API_BASE_URL` | – (`local`: `http://localhost:8090`) | taldo-api without its context path; private in production. |
| `SERVER_PORT` | `8081` | |

Locally: `docker compose up`, then run `taldo-bff` with the `local` profile. The frontend's Vite dev server forwards
`/api`, `/login` and `/logout` to `localhost:8081`, without rewriting the `Host` header, so the callback URL stays
`http://localhost:5173/login/callback`, the only redirect URI the realm allows for `taldo-web`.

## Keycloak clients

- `taldo-web` is confidential since the BFF (secret `TALDO_WEB_CLIENT_SECRET` in Docker Compose), with the lightweight
  access token, the `sub` mapper and the `taldo-api` audience. Keycloak refuses a lightweight token at its UserInfo
  endpoint, so the BFF builds the user from the ID token alone and never calls UserInfo (`SecurityConfig`): it needs
  only the subject.
- `taldo-test` is a public client in the **local** realm only, with the settings `taldo-web` had before: the current
  frontend logs in with it (`keycloak-js`, `clientId: taldo-test`) until it switches to the BFF, and developers use it to
  get a token for Postman. It is not in the realm file every environment imports: `keycloak-config` creates it from
  `docker/keycloak/taldo-test-client.json`, which only Docker Compose provides. `KeycloakRealmAccessTokenTest` checks
  both clients' tokens and that the imported realm holds no public client.

## Testing

`LoginIntegrationTest` runs the whole login over HTTP against a WireMock Keycloak (`KeycloakStub`: discovery, keys,
token endpoint with Keycloak's default scopes, and a UserInfo endpoint that refuses the token as Keycloak does; the ID
token carries the nonce of the login it belongs to): the authorization request (client, PKCE,
callback, cookie attributes), the code exchange (client secret and PKCE verifier), a callback with a foreign `state`,
`401` without a session, logout with and without the CSRF token. `ApiRelayIntegrationTest` replaces taldo-api with
WireMock too: what taldo-api receives (path, query, bearer token, body; no cookies or CSRF header), the relative
`Location`, `403` without the CSRF token, problems passed on, renewal of an expired token, a refused renewal (`401`,
session gone), Keycloak unavailable (`503`, session kept) and taldo-api unreachable (`502`). Both share one context
(`BffIntegrationTest`) and the login steps (`BrowserLogin`). Sessions run on H2.

## Known gaps

- No ArchUnit rules in this module (taldo-api's `ArchitectureTest` covers taldo-api only); Error Prone, NullAway (every
  package `@NullMarked`), SpotBugs, Spotless and the Enforcer do run.
- The compiler configuration (Error Prone, NullAway) is a copy of taldo-api's; moving it to the parent POM would keep
  both in step.
- No automated end-to-end test against a real Keycloak: `KeycloakStub` must mirror Keycloak's behaviour by hand.
  Checked by hand in Chrome through the Vite dev server (login, relay, logout); Chrome accepts the `__Host-` cookie
  over `http://localhost`. Other browsers are untested; if one refuses it, set `TALDO_SESSION_COOKIE_NAME` locally.
- Refresh-token rotation is off in the realm (ADR 0009, section 5). Two parallel calls that both renew the token use
  the same refresh token; with rotation on, the second would fail and end the session.
- taldo-api's CORS configuration is still there; it can go once no browser calls taldo-api directly.
