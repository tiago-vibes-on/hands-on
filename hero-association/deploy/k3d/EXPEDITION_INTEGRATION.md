# Expedition and Map combat in k3d

This opt-in integration connects the real Keycloak → BFF → Expedition → Core
path. A Map-enabled frontend exposes Troll Field combat alongside the existing
Quest flow. Ordinary local development remains unchanged.

## Requirements and build

Start the isolated k3d lab per [README.md](README.md), including Istio, Envoy
Gateway, Keycloak, Core, and BFF. Stage the dedicated Redis, RabbitMQ, and
Expedition service first using [EXPEDITION.md](EXPEDITION.md). From
`hero-association/deploy/k3d`:

```bash
export PATH="$PWD/.tools:$PATH"
./build-backend-images.sh expedition
./stage-expedition.sh
./build-backend-images.sh
```

The last command tests, builds, and imports the current Core and BFF JVM
images. Core validates its schema on startup. If an existing disposable k3d
Core database lacks the `expedition_reservation` table, stop any active
Expedition and reset/reseed **only that k3d Core database** before enabling:

```bash
./deploy-backend.sh --reset-core-db
./enable-expedition-integration.sh
```

The reset stops Core, drops/recreates its schema, and reloads deterministic
test data. It does not reset Keycloak accounts or Expedition Redis/RabbitMQ.
If the matching Core schema is already deployed, use `./deploy-backend.sh`
without the reset flag, then run `./enable-expedition-integration.sh`.

The enable command refuses a different Kubernetes context or a missing Core
reservation table. It synchronizes only the Expedition client and BFF audience
mapper into an existing Keycloak realm, applies a narrow Istio policy for
Expedition's four internal Core admission operations, and patches the three
application Deployments without changing their image tags. The shared service
keys remain in ignored local files and Kubernetes Secrets. Core admission
requires the service key and authenticated player token; the browser commands
require the BFF session and CSRF token. Rerun the enable command after normal
backend deployment or private Expedition staging, whose base manifests leave
these integration flags disabled.

Build and deploy the Map-enabled frontend (the k3d build script defaults to
`HERO_ASSOCIATION_K3D_MAP_ENABLED=true`):

```bash
./build-frontend-image.sh
./deploy-frontend.sh
```

The deploy script restarts the frontend Deployment so a newly imported fixed
`:k3d` image tag is loaded by fresh Pods.

Open `https://heroassociation.test:8443`, sign in as User2, and choose
**Map → Troll Field** with the seeded three-Hero Main Party. Continue starts another fight only
after a win; Return waits for the current fight to finish before settling.
The standalone Dockerfile and ordinary Vite development default to Map off.
The six-image local pipeline and Jenkins artifacts build Map-enabled frontend
images by default and verify Expedition and Map before promotion. Rerun the
integration command after base backend deployment, whose manifests disable it.

## Validate

From `hero-association/e2e`, run the authenticated k3d tests:

```bash
npm run test:k3d:expedition
npm run test:k3d:map
```

The first signs in as seeded `manager4@mail.com`, creates or reuses a personal party,
enters Troll Field, checks WebSocket snapshots and reconnect, continues to a
second fight, returns with no viewer, and checks Core settlement. It changes
only that test Manager's Hero progress. The Playwright container joins the
k3d Docker network directly, so another WSL listener on port 443 cannot
intercept this test. If an interrupted run remains active, set
`HERO_ASSOCIATION_K3D_CLEANUP_EXPEDITION_ID` to that **exact** test run ID for
one retry; the test refuses to end a different run.

The second signs in as User2, requires the seeded three-Hero Main Party,
and drives the visible Map menu through live combat, reconnect, Continue,
Return, and Core settlement. Load tests are
deferred while we establish the player-facing combat flow; a browser journey
is not a capacity result.
