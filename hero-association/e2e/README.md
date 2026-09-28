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

`npm test` runs the local Traefik suite and excludes k3d-only tests.
`npm run test:auth` runs only `tests/authentication.spec.js` (currently the
full local suite). The runner uses Playwright's official Chromium Docker image at
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
agency onboarding, recruitment, cross-agency authorization, and a market
order-placement limit shared across sessions and BFF instances. It uses the
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
account identity, repeated authenticated agency-state reads, and rejection
of a cross-agency read. It also checks the shared market-order limit with two
BFF Pods, two sessions for one user, and a second user's separate budget.
Unlike `npm test`, it does not start Compose, flush Redis, or delete volumes.
The Playwright container uses Docker host networking and maps both k3d
hostnames to `127.0.0.1`, reaching the cluster's HTTPS port `443`. It ignores
local certificate errors only inside this test browser; configure CA trust
separately for a normal browser.

## Measure the market order rate limit with k6

With the isolated k3d lab running and at least two BFF Pods ready, run:

```bash
npm run test:market:k6
```

The runner needs Docker, `kubectl`, Node.js 24, and `npm ci` completed in this
directory. It uses the pinned Playwright browser image to sign in two separate
sessions for `user1@mail.com` and one for `user2@mail.com`, then runs the pinned
`grafana/k6:2.3.0` image against the k3d HTTPS gateway. A six-request burst
must yield exactly five requests forwarded to Core (HTTP 400 for intentionally
incomplete orders) and one HTTP 429 with `Retry-After: 1`. User 2 must still
reach Core independently. A second scenario sends 20 order attempts per second
for ten seconds; it expects both forwarded and limited responses, no unexpected
status, no dropped iterations, and at most 55 forwarded attempts. The threshold
allows for the boundaries of the ten-second sliding-window observation.

These requests cannot create market orders because they omit required order
fields. The runner uses the existing k3d data, does not reset Redis or the
database, and removes its temporary session file after k6 exits. It does not
print session cookies or CSRF tokens. Run it without other tests using user 1's
market-order endpoint, since that traffic shares the same per-user budget.

## Measure k3d read load

With the lab already deployed, run the read-only baseline from this directory:

```bash
npm run load:k3d
```

It signs in as local-only `user1@mail.com` once, then uses four concurrent
clients for 60 seconds to GET the agency-state API through Envoy Gateway,
BFF, and Core. It reports HTTP status counts, transport errors, throughput,
and p50/p95/p99 latency as `K3D_LOAD_RESULT`. A failed response includes up
to five bounded status/body/timing samples for diagnosis. Redirects are not
followed, and any non-200 response or transport error fails the test. It does
not reset databases or change game state.
To change the load, set `HERO_ASSOCIATION_K3D_LOAD_SECONDS` (1–600) and
`HERO_ASSOCIATION_K3D_LOAD_CLIENTS` (1–32). While it runs, sample BFF, Core,
and their Istio sidecars with `kubectl top pods --containers` using the
isolated k3d kubeconfig. For a full HPA scale-out, BFF rolling-restart,
scale-in, and post-load browser check, run
`./test-autoscaling.sh` from `deploy/k3d`. This is a read-only
workload, not a mixed-action capacity test.

## Measure k3d mixed API load

Use `../deploy/k3d/test-mixed-capacity.sh` after deploying the k3d JVM
backend and frontend. It creates an isolated temporary Core database and
test-only BFF/Core stack before calling `npm run load:mixed:k3d`; do not run
that npm command by itself. The Playwright test verifies that a header-marked
route reaches the isolated stack while an ordinary request still reaches the
live BFF, then measures about 90% authenticated agency-state reads and 10%
CSRF-protected hero-activity writes. It reports per-operation status counts,
transport errors, throughput, and p50/p95/p99 latency as
`K3D_MIXED_RESULT`. See [the capacity lab](../deploy/k3d/CAPACITY.md) for
the staging options, measured results, cleanup behavior, and limitations.
