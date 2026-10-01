# Isolated k3d E2E lane

The daily app and candidate E2E stack share the k3d Envoy Gateway controller
and data plane, but must not share player data. Candidate tests use a temporary
namespace and the test-only browser hostnames `app.e2e.heroassociation.test`
and `auth.e2e.heroassociation.test`. Those names are resolved only inside the
test runner; do not add them to Windows or WSL hosts files. The normal app
keeps `heroassociation.test` and `auth.heroassociation.test`.

The Gateway has dedicated HTTPS listeners for the two E2E hostnames. Only a
namespace labeled `heroassociation.test/e2e-gateway=true` may attach routes to
those listeners. The E2E leaf certificate is signed by the existing ignored
local CA, so no second CA trust installation is needed. Private keys stay in
`tls/certs/`, not Git. The normal HTTPS listener still accepts only routes
from the daily `hero-association` namespace.

To prove route and TLS isolation without touching accounts or databases, run:

```bash
./deploy/k3d/install-gateway.sh
./deploy/k3d/test-isolated-gateway.sh
```

The probe creates one disposable namespace with a frontend Pod and route,
checks the E2E and daily frontends through Envoy using the trusted CA, then
deletes the probe namespace even if the test fails. It does not exercise the
full E2E suite or verify candidate image digests.

The `deploy/k8s/e2e` overlay supplies isolated application and infrastructure
resources, E2E routes, issuer settings, and no autoscalers. Run it through the
script, not with `kubectl apply -k`: the script generates private credentials,
bootstraps the test databases and RabbitMQ, and cleans up the namespace.

From `hero-association`, run either a smoke with the four images currently
deployed in the daily k3d namespace or an exact archived candidate check:

```bash
./deploy/k3d/test-isolated-stack.sh
./deploy/k3d/test-isolated-stack.sh pipeline/artifacts/<build-id>/all
```

Both modes require Java 25 and the backend Maven wrapper for an AMQP
permission probe: after broker setup, both the Expedition worker and Core
settlement user must receive RabbitMQ 403 when reading the other role's
queue. This uses a temporary loopback port-forward and no daily broker
credentials.
Both modes then check trusted HTTPS. Ten Playwright cases cover registration,
logout and login, access-token refresh, agency onboarding and recruitment,
gold transfers, permissions, personal market orders, the per-user Envoy
limit, and Map/Expedition WebSocket settlement. The disposable realm issues
30-second access tokens with a two-second BFF refresh skew. It also stops
only disposable Core Redis, reruns the Map journey, requires Core to stay
ready and log its PostgreSQL creature fallback, then restores that Redis.
The runner then checks the k6 market burst and sustained thresholds using
fresh sessions. Between these phases it restarts both disposable BFF Pods, confirms their old
Pod UIDs are gone, and reuses a saved browser session to verify the same
account through the replacement Pods. It then stops only the disposable BFF
Redis, confirms the saved session fails closed, restores Redis, and confirms
a fresh login works. It expires that new session token-state key in the
disposable Redis and checks that the saved cookie fails closed before k6.

Archive mode checks the archive checksum, imports all four images, bootstraps
with the archived Core image, and verifies all running application Pod image IDs
against the OCI archive. It records a pass in
`k3d-e2e-verification.json` only after namespace deletion. Manual and Jenkins
promotion require this exact record; the old Compose record is not accepted.
A failed or interrupted candidate stays pending; an existing
`hero-association-e2e` namespace is never overwritten or silently deleted.

Verified 2026-10-01: `localenv-core-20261001-a` was built from a dirty
worktree, assembled with the three current baseline images, and passed the
full disposable browser, session, outage, and k6 gate. Five running Pod
image IDs matched the four archived images. Cleanup completed before its
`k3d-e2e-verification.json` became `passed`; the daily Deployment image
references did not change and no Compose verification record was created.
A stricter daily-image rerun exposed HTTP 500 after disposable BFF token-state
expiry, instead of a 302/401/403 authentication response. The BFF-only
Quarkus 3.40.1 candidate `localenv-bff-3401-20261001-a` then passed that
strict check plus all ten browser cases, saved-session and BFF-restart checks,
Redis outage/recovery, and k6 thresholds. Five Pod image IDs matched the
archive, and cleanup completed before its k3d verification record passed.
A later full rerun passed the additional Core Redis outage/Map fallback phase.
The normal daily BFF image was not changed. A later full rerun passed both
RabbitMQ cross-role AMQP 403 checks, all ten browser cases, the cache and
session resilience phases, and k6 thresholds.

Core, BFF, and Expedition Maven component tests have a separate disposable
dependency lane: `./deploy/k3d/test-isolated-components.sh [core|bff|expedition|all]`.
It creates private PostgreSQL, Redis, and RabbitMQ resources, runs Maven over
loopback port-forwards with Dev Services disabled, and removes the namespace.
The two direct RabbitMQ Testcontainers transport assertions are represented by
k3d AMQP tests with the real service credentials and payloads. Backend
candidate builds invoke the matching component lane before packaging.

The active candidate and browser gate use the disposable full-stack k3d lane.
The old Compose/Traefik browser runner is retired. The deferred
Quest-borrowing case remains historical test source but is not an active MVP
gate. Core, BFF, and Expedition component integration is covered separately
by the disposable k3d component lane.
The no-archive smoke currently selects the older daily BFF image, so its
strict expired-token check will fail until that BFF is explicitly promoted. The shared Gateway
rate-limit Redis outage is still an opt-in disruptive test: stopping it
would affect daily traffic. This lane tests only the isolated BFF Redis
outage.
