# Hero Association E2E tests

The Playwright suite verifies React, Traefik, the BFF, Keycloak, Game Core,
PostgreSQL, and Redis together through the same HTTPS hostnames used for local
development.

## Run

From this directory, with Docker and Node.js 24 available:

```bash
npm install
npm test
```

`npm run test:auth` runs only `tests/authentication.spec.js` (currently the
full suite). The runner uses Playwright's official Chromium Docker image at
the version pinned in `package-lock.json`, so no host browser installation is
required. The frontend image runs `npm ci` during its Docker build; frontend
`node_modules` on the host are not needed.

The setup creates an isolated Compose project named `hero-association-e2e`.
Traefik serves the frontend and BFF at `https://heroassociation.test` and
Keycloak at `https://auth.heroassociation.test` inside the browser container's
Docker network. The browser trusts the test-only local certificate for this
run; it never routes through the normal development gateway. The second BFF
instance is reached through the E2E-only same-origin
`/__e2e-secondary/api/...` route, so the browser sends the same session
cookie to both instances.

The stack publishes diagnostic host ports `15432` (Core PostgreSQL), `16380`
(Redis), `18080` and `18082` (BFF instances), `18081` (Core), `18180`
(Keycloak), and `18443` (Traefik HTTPS). It has its own databases and Redis
state. Setup and teardown remove only the E2E Compose project and its volumes,
including after a failed setup. The normal development data is never reset.

The suite covers registration, sign-out and sign-in again, token refresh,
Redis session expiry, session sharing across BFF instances, Manager and
agency onboarding, recruitment, and cross-agency authorization. It uses the
versioned local Keycloak users `user1@mail.com` / `user1`,
`user2@mail.com` / `user2`, and the initially unprovisioned
`user3@mail.com` / `user3`. Access tokens last eight seconds only in this
isolated realm.

Failure screenshots and traces are written to ignored `test-results/` and
`playwright-report/` directories.

## Verify the running k3d lab

The separate k3d suite reuses an already deployed frontend, BFF, Core,
Keycloak, PostgreSQL, and Redis. From this directory, run:

```bash
npm ci
npm run test:k3d
```

It uses the seeded local-only `user1@mail.com` / `user1` and
`user2@mail.com` / `user2` accounts to verify login, logout, login again,
account identity, and an authorized agency-state API read. Unlike `npm test`,
this command does not start Compose, flush Redis, or delete database volumes.
The Playwright container uses Docker host networking and maps both k3d
hostnames to `127.0.0.1`, reaching the cluster's HTTPS port `19443`. It ignores
local certificate errors only inside this test browser; configure CA trust
separately for a normal browser.

## Measure k3d read load

With the lab already deployed, run the read-only baseline from this directory:

```bash
npm run load:k3d
```

It signs in as local-only `user1@mail.com` once, then uses four concurrent
clients for 60 seconds to GET the agency-state API through Envoy Gateway,
BFF, and Core. It reports HTTP status counts, transport errors, throughput,
and p50/p95/p99 latency as `K3D_LOAD_RESULT`. It fails on any non-200
response or transport error. It does not reset databases or change game state.
To change the load, set `HERO_ASSOCIATION_K3D_LOAD_SECONDS` (1–600) and
`HERO_ASSOCIATION_K3D_LOAD_CLIENTS` (1–32). While it runs, sample BFF, Core,
and their Istio sidecars with `kubectl top pods --containers` using the
isolated k3d kubeconfig. This is a preliminary read workload, not a
mixed-action capacity test.
