# Hero Association BFF

The Backend for Frontend (BFF) is the only browser-facing backend service. It
uses Keycloak's confidential authorization-code flow with PKCE and proxies
authenticated `/api/...` calls to Game Core without changing their API
contract. It forwards its server-held Keycloak access token to Game Core.
Keycloak tokens stay in the BFF-owned PostgreSQL session store; the browser
receives only `HttpOnly`, `SameSite` cookies.

## Run locally

docker compose up --detach postgres-keycloak keycloak

From `backend/`, copy `.env.example` to the ignored `.env` file and replace all
placeholders. Start Keycloak, BFF PostgreSQL, and Game Core as described in the
parent README. Then load the environment and run the BFF on port `8080`:

```bash
set -a
source ../.env
set +a
./mvnw quarkus:dev
```

The BFF forwards requests to `http://localhost:8081` by default. Its local
session database defaults to `localhost:5433`. Set
`HERO_ASSOCIATION_CORE_BASE_URL`, `HERO_ASSOCIATION_BFF_DATABASE_URL`, or
`HERO_ASSOCIATION_OIDC_AUTH_SERVER_URL` to use other addresses.

`GET /api/v1/session` is public and returns the signed-in Keycloak identity
summary plus a CSRF token. `GET /auth/login` redirects to Keycloak, and
`GET /auth/logout` logs out of both the BFF and Keycloak, then returns the
browser to the frontend. Proxied game endpoints require an authenticated BFF
session and forward its access token to Core. Core checks the
token's `hero-association-core` audience. `GET /api/v1/account` and
`POST /api/v1/account/manager` are forwarded to Core for Account provisioning
and Manager onboarding. The Account response lists authorized agency
memberships. Agency creation and invitations are intentionally not part of this
stage.

## Test

```bash
./mvnw test
```

Tests start a temporary PostgreSQL Testcontainer for the BFF session store and
a local Game Core stub. Docker must be available.
