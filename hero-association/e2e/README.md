# Hero Association E2E tests

The active browser and archive gate runs through the shared k3d Envoy Gateway.
Backend candidate builds first test Core, BFF, or Expedition against private,
disposable k3d dependencies. The archive gate then creates an independent
full-stack namespace with its own databases, Keycloak, Redis, and RabbitMQ.
See [K3D_ISOLATION.md](K3D_ISOLATION.md). The old Compose/Traefik runner has
been retired; standalone JVM/native Compose packaging remains available.

## Verify a candidate archive without deploying it

With Docker, Java 25, Node.js 24, k3d, and the isolated kubeconfig available,
run from `hero-association/`:

```bash
./pipeline/build-local.sh all
(cd e2e && npm ci)
./deploy/k3d/test-isolated-stack.sh pipeline/artifacts/<build-id>/all
```

Or from `e2e/`, run `npm run test:isolated -- ../pipeline/artifacts/<build-id>/all`.
The candidate build requires all four daily application Deployments restored
to full k3d mode, but tests and identity data stay in disposable namespaces.
The runner checks the archive checksum and all five running application Pod
image IDs before and after BFF replacement. It runs ten browser cases,
Core-cache-off Map replay, BFF session/outage and expiry checks, RabbitMQ
cross-role denial, and market k6 thresholds. It marks
`k3d-e2e-verification.json` passed only after the namespace is deleted.
That exact passing record is required for manual promotion; neither command
deploys the candidate. Failure screenshots and traces are written to ignored
`test-results/` and `playwright-report/` directories.

The previous Compose-only `authentication.spec.js` and
`market-proxy.spec.js` remain as historical test source. The active isolated
suite reuses the gold-transfer, personal-market, and Expedition journeys.
The unrestricted Compose market-proxy case is superseded by the Envoy limit
test. The borrowing-quest case is deferred with Quest; it is not an active
MVP gate.

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
of a cross-agency read. It uses `manager4@mail.com` / `manager4` to place and
cancel a personal market order, and checks the Envoy market-order limit with
two BFF Pods, two sessions for one user, and a second user with a separate
budget. Manager 4 must exist in the k3d Keycloak realm with the UUIDv7 subject
from the versioned [realm file](../backend/keycloak/realm/hero-association-realm.json).
Keycloak does not reimport clients or users into an existing realm when Core is
reset. The [private Expedition integration](../deploy/k3d/EXPEDITION_INTEGRATION.md)
synchronizes its client and audience mapper without resetting Keycloak.
This k3d smoke suite does not start Compose, flush Redis, or delete
volumes. `npm test` now runs this same k3d smoke configuration. The Playwright container joins the isolated k3d Docker network and
maps both hostnames to its load balancer, bypassing unrelated WSL port-443
listeners. It ignores local certificate errors only inside the test browser.

After enabling the k3d integration and deploying the Map-enabled frontend, run:

```bash
npm run test:k3d:expedition
npm run test:k3d:map
```

The API test exercises seeded Manager 4. The browser test exercises User2's
three-Hero Main Party and the visible Map menu in Chromium, including its
Phaser canvas and WebSocket reconnect. It first returns any old User2 run, so
it can be repeated. Both tests change Hero progress in the disposable lab.

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
198–202 completed attempts, at most 65 not rate-limited, at least 133
rate-limited, no unexpected responses, and no dropped iterations. At least one
request must not be rate-limited. The ten-second sustained window is a coarse
throughput check: its exact 400/429 split varies with request scheduling and
gateway accounting boundaries. The six-request burst is the precise check of
the five-request per-user budget.

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
