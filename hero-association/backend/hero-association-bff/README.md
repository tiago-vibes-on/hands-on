# Hero Association BFF

The Backend for Frontend (BFF) is the only browser-facing backend service. It
uses Keycloak's confidential authorization-code flow with PKCE and proxies
authenticated `/api/...` calls to Game Core without changing their API
contract. It forwards its server-held Keycloak access token to Game Core.
Keycloak tokens stay in the BFF-owned Redis session store; the browser
receives only `HttpOnly`, `SameSite` cookies. In production, Redis must be
private, authenticated, TLS protected, and encrypted at rest.

## Run locally

Before the first run, create `backend/.env` from `.env.example` and replace all
placeholders. Start the shared local infrastructure from `backend/`:

```bash
./scripts/start-infra.sh
```

Then, from `backend/`, run the BFF on port `17080`:

```bash
./scripts/run-bff-dev.sh
```

The BFF forwards requests to `http://localhost:17081` by default. Its local
session Redis defaults to `localhost:16379`. Set
`HERO_ASSOCIATION_BFF_REDIS_HOST_PORT` in `backend/.env` to use another
address.

`GET /api/v1/session` is public and returns the signed-in Keycloak identity
summary plus a CSRF token. `GET /auth/login` redirects to Keycloak, and
`GET /auth/logout` logs out of both the BFF and Keycloak, then returns the
browser to the frontend. An already signed-out visit to `/auth/logout` also
returns to the frontend. Proxied game endpoints require an authenticated BFF
session and forward its access token to Core. Core checks the
token's `hero-association-core` audience. `GET /api/v1/account` and
`POST /api/v1/account/manager` are forwarded to Core for Account provisioning
and Manager onboarding. The Account response lists authorized agency
memberships. Agency creation and invitations are intentionally not part of this
stage.

The BFF limits `POST /api/v1/market/orders` to five attempts
per authenticated Keycloak user in a rolling second. BUY and SELL, all
agencies, sessions, and BFF replicas share that user's Redis-backed budget.
Excess requests return `429` with `Retry-After: 1`; a Redis failure returns
`503` and does not forward the order. Market reads and cancellations are not
limited by this rule. Core still validates order data and permissions.

In the k3d lab, BFF exports OTLP traces, HTTP/JVM metrics, and structured
logs. Its Core proxy creates a client span and forwards W3C trace context
without putting the server-held access token in telemetry. Normal dev mode
and normal Docker Compose keep telemetry disabled unless explicitly
enabled; see
[`../README.md`](../README.md#isolated-k3d-jvm-deployment).

## Test

```bash
./mvnw test
```

On WSL with Docker Desktop, run
`TESTCONTAINERS_HOST_OVERRIDE=host.docker.internal ./mvnw test` if temporary
container ports are not reachable through `localhost`. Tests disable OIDC,
use a local Game Core stub, and start an isolated Redis container. The
Playwright suite validates real sessions and cross-replica market limiting.
