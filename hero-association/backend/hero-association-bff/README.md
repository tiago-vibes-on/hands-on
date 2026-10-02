# Hero Association BFF

The Backend for Frontend (BFF) is the only browser-facing backend service. It
uses Keycloak's confidential authorization-code flow with PKCE and proxies
authenticated `/api/...` calls to Game Core without changing their API
contract. It forwards its server-held Keycloak access token to Game Core.
Keycloak tokens stay in the BFF-owned Redis session store; the browser
receives only `HttpOnly`, `SameSite` cookies. In production, Redis must be
private, authenticated, TLS protected, and encrypted at rest.

## Run locally

Shared Keycloak, BFF Redis, Core, and Envoy Gateway stay in k3d. From
`hero-association/deploy/k3d`, run:

```bash
k3d cluster start hero-association
./hybrid.sh run bff
```

The command stops only the BFF k3d workload, forwards private dependencies,
and runs BFF in Quarkus dev mode with hot reload on port `17080`.
The browser stays at `https://heroassociation.test` through Envoy.
Press Ctrl-C to restore the original BFF replicas, Service route, and HPA.
The [local development guide](../../LOCAL_DEVELOPMENT.md) covers mixed-service
mode, ports, secrets, and interruption recovery.

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

The BFF does not rate-limit `POST /api/v1/market/orders`. In k3d, Envoy
Gateway limits placement to five attempts per second per authenticated
Keycloak subject across sessions and gateway replicas. It returns `429` with
`Retry-After: 1` on excess requests and fails closed if gateway rate-limit
state is unavailable. Full k3d and hybrid development both use the same
Envoy market limit.
BFF Redis continues to store OIDC session state. Market validates order data; Assets obtains current owner permissions from Core.

In the k3d lab, BFF exports OTLP traces, HTTP/JVM metrics, and structured
logs. Its Core proxy creates a client span and forwards W3C trace context
without putting the server-held access token in telemetry. Standalone
packaged Compose keeps telemetry disabled unless explicitly enabled; see
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

The BFF routes `/api/v1/market/**` to the independent Market service via
`HERO_ASSOCIATION_MARKET_BASE_URL` (development default port 17084). It forwards
the server-held player token and preserves pending `202` responses. The BFF
has no Market database credential or private Assets service key; Market alone
calls Assets. The BFF client token includes Core, Market and Assets audiences.

BFF routes `POST /api/v1/gold-transfers` directly to Assets through
`HERO_ASSOCIATION_ASSETS_BASE_URL` (development port 17085). It forwards only
the player token and trace context, and holds neither private Assets key. Core
equipment DELETE requests preserve the caller's `X-Operation-Key`; equipment
and Quest responses preserve pending `202` status and operation bodies.
