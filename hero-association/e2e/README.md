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
`npm run test:auth` runs only `tests/authentication.spec.js`.
The runner uses Playwright's official Chromium Docker image at
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
agency onboarding, recruitment, cross-agency authorization, and market-order
proxying through both BFF instances without a local rate limit. It uses the
versioned local Keycloak users `user1@mail.com` / `user1`,
`user2@mail.com` / `user2`, and the initially unprovisioned
`user3@mail.com` / `user3`. Access tokens last eight seconds only in this
isolated realm.

Failure screenshots and traces are written to ignored `test-results/` and
`playwright-report/` directories.

## Test a build archive without rebuilding

Build one complete archive, then pass its printed path to the separate
archive-backed browser lane:

```bash
cd ../pipeline
./build-local.sh all
cd ../e2e
npm ci
npm run test:archive -- ../pipeline/artifacts/<build-id>/all
```

The runner verifies the archive checksum and all three manifest image IDs
before changing the isolated E2E stack. It loads those images, disables
Compose builds and pulls for Core, BFF, and frontend, and confirms the
running containers use the recorded IDs. Keycloak, databases, Redis, and
Traefik remain pinned Compose dependencies outside the application archive.
Only a successful browser run followed by cleanup records `result: passed`
in the ignored archive's `e2e-verification.json`. An invalid archive is
rejected before setup; after validation, an interrupted or failed test run
leaves `result: pending`. The regular `npm test` still builds from source.

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
of a cross-agency read. It also checks the Envoy market-order limit with two
BFF Pods, two sessions for one user, and a second user with a separate budget.
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
from user 1 must produce exactly five requests not rate-limited (HTTP 400 from
Core) and one HTTP 429 from Envoy with `Retry-After: 1` and the
Envoy layer header. User 2 must have one request not rate-limited,
independently of user 1's two sessions.

The sustained scenario targets 200 attempts at 20 per second for ten
seconds. Its arrival scheduler and the one-second gateway window boundaries
make exact iteration and 400/429 counts timing-dependent. Thresholds require
198–202 completed attempts, at most 55 not rate-limited, at least 143
rate-limited, no unexpected responses, and no dropped iterations. At least one
request must not be rate-limited.

k6's native `THRESHOLDS` section prints the expected expression and observed
count for each metric and sets a nonzero exit status if a threshold fails.
HTTP 400 is only the marker for an intentionally incomplete request that was
not rate-limited by Envoy. Core order validation is not under test.
Because 400 and 429 are designated expected HTTP statuses here, a 0%
`http_req_failed` value does not mean any market order was created.

These requests cannot create market orders because they omit required order
fields. The runner uses the existing k3d data, does not reset Redis or the
database, and removes its temporary session file after k6 exits. It does not
print session cookies or CSRF tokens. Run it without other tests using user 1's
market-order endpoint, since that traffic shares the same per-user budget.

For the opt-in Redis-outage and two-Envoy-proxy check, run
`../deploy/k3d/test-market-edge-resilience.sh`. It runs a browser
outage check, then this k6 test across two Envoy proxies. It verifies Envoy
responses and restores the lab replica counts on failure.

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
