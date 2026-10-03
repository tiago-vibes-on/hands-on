# Local build and k3d pipeline

This local pipeline builds once, verifies the archived images, and can deploy
them to k3d or store them in Floci S3 for a later AWS-lab deployment. The
daily Vite/Quarkus hot-reload workflow shares this cluster. Build-only
candidates can run while a daily service is in hybrid mode because component
tests use disposable dependencies; promotion still requires all eight original
k3d application Pods restored.

The [local Jenkins setup](../ci/jenkins/README.md) has independent Core, BFF,
Expedition, Market, Assets, World, Quest and frontend worktree builds and trusted-`main` builds, plus a deploy-local job
for each service. The older complete-stack command below remains available
manually. Both worktree and trusted-`main` builds require a separate manual
deployment of their verified artifact. A Jenkins lock serializes trusted-`main`
builds; all jobs run on WSL, not GitHub runners.

## Run the complete k3d pipeline

With Docker, Java 25, Node.js 24/npm, `kubectl`, and the isolated k3d lab
already running, run from `hero-association/pipeline`:

```bash
./run-k3d-pipeline.sh
```

Optionally pass a unique build ID. The command checks the isolated cluster
and runs rollback and Core-reset target tests before building and testing Core,
BFF, Expedition, Market, Assets, World, Quest and frontend once. It archives those images, runs the
disposable k3d archive E2E gate, then deploys them to k3d and runs the
normal-namespace browser, Expedition, Map, Quest dungeon, and market k6 suites. If any stage
fails, later stages do not run.
The archive remains in ignored `artifacts/<build-id>/all/` for inspection or
a later deployment target. The candidate gate uses disposable k3d data and
does not reset the daily databases. For an intentional schema/seed change in
the daily k3d game databases, run `./run-k3d-pipeline.sh --reset-game-db`
instead. After the archive-backed k3d gate, this command refuses unfinished
Expeditions, stops all application services and audits unfinished workflows, and uses the exact verified Core, Assets, Market, World and Quest
images to recreate their schemas and matching deterministic seeds. Keycloak
and Redis are retained. `--reset-core-db` remains an alias. A reset promotion
cannot safely roll back to older images if a later gate fails; inspect and
repair the cluster before retrying. Backend candidate tests use disposable k3d PostgreSQL, Redis, and RabbitMQ; no
Testcontainers host override is needed for this pipeline.

## Build and verify in separate stages

From `hero-association/pipeline`, with Docker, Java 25, Node.js 24, npm,
`kubectl`, and the k3d cluster available for disposable component tests,
build the seven JVM services and the frontend image in one run:

```bash
./build-local.sh all
```

The command first runs Core, BFF, Expedition, Market, Assets, World and Quest component tests against
private, disposable k3d PostgreSQL, Redis, and RabbitMQ. After that namespace
is removed, Maven packages the JVM services with `-DskipTests` to avoid a
second container-backed test run. Frontend lint and build remain local, then
the command builds eight Docker images. It writes a single `images.tar`, SHA-256 checksum,
and manifest under ignored `artifacts/<build-id>/all/`. The manifest records
the Git revision, whether the source worktree was dirty, and each local image
ID. The archive is the candidate to promote to multiple deployment targets;
the older direct-build k3d scripts still use fixed `:k3d` tags.

Pipeline frontend images and the hybrid Vite command both enable Map by default.

Verify and load a completed archive without rebuilding it:

```bash
build_id="YOUR_BUILD_ID"
cd "artifacts/$build_id/all"
sha256sum --check images.tar.sha256
docker load --input images.tar
```

Replace `YOUR_BUILD_ID` with the ID printed by the build command.

Each component can also run as its own build lane, with a shared explicit ID:

```bash
./build-local.sh core demo-001
./build-local.sh expedition demo-001
./build-local.sh bff demo-001
./build-local.sh frontend demo-001
```

Those commands create separate archives under `artifacts/demo-001/`. Use a
new build ID for every source revision or rebuild; existing image tags and
artifact paths are deliberately not overwritten. Local dirty changes are
included in the images but cannot be recreated from the recorded Git commit
alone. Do not publish these archives as a release without a clean source
revision and a reviewed release process.

For a single-service build, only that backend service runs its component suite;
frontend-only builds run lint and build without the component lane. Direct
`./mvnw package` remains independent and may still use local Testcontainers.
The archived images must pass the disposable k3d gate before they are
eligible for local deployment. From `hero-association`, using the build ID
printed by `build-local.sh`:

```bash
(cd e2e && npm ci)
./deploy/k3d/test-isolated-stack.sh pipeline/artifacts/<build-id>/all
```

The runner checks the archive checksum and all eight services’ running Pod image IDs,
including the candidate built from uncommitted worktree changes. It runs seventeen
browser/API cases, three Assets outage/restart phases, four World/Quest outage
and reward-recovery phases, BFF
restart/session continuity, isolated BFF Redis outage and expired-token
checks, and k6 market thresholds. Only after namespace cleanup
does `k3d-e2e-verification.json` record `result: passed`. The retired Compose
verification format does not authorize deployment. This gate never changes
the daily application Deployments.

## Jenkins service artifacts

For a single-service promotion, Jenkins first runs `build-local.sh` for that
component, including its disposable k3d component gate. It then uses `assemble-service-archive.mjs` to combine the candidate
with the other seven live k3d images in a checksummed, eight-image archive. The manifest
includes `promote_component`. The archive browser gate tests exactly this
combination, not an unrelated eight-service rebuild. Build and deploy jobs
share the retained artifact under the WSL agent work directory at
`artifacts/<build-id>/all`; the Jenkins build record stores its ID and small
evidence. A build does not change k3d.

`deploy-k3d.mjs --verify-baseline` checks the seven baseline Deployment
references and running Pod image digests before the browser gate. The deploy
job repeats this drift check and requires the exact archive's passing k3d E2E record.
It imports and rolls only the promoted component. It verifies all eight
application Deployments and runs browser, Expedition, Map, Quest dungeon, and market k6 gates. The final Pod
check briefly retries while autoscaling settles, but a persistent digest or
readiness mismatch fails promotion. A failure after rollout
restores only that component's previous reference. If either baseline changed
since the build, rebuild against the current cluster. The latest successful
per-service deployment wins.

This local mechanism needs full k3d mode even for a build, because the other
seven image baselines are read from running application Pods. The preflight
refuses a hybrid Service route or stopped application Deployment before the
expensive build starts. It does not create a reproducible
release artifact for other environments. Floci publish, fetch, and frontend
preview reject these k3d-specific composite archives; use a full
eight-image build for the separate Floci lab. Main builds use a clean GitHub
checkout; worktree builds snapshot staged, unstaged, and non-ignored new
files. Do not delete retained archives while a deploy job may still use them.

## Promote the verified archive to k3d

From `hero-association/pipeline`, with the isolated k3d lab running:

```bash
node deploy-k3d.mjs artifacts/<build-id>/all
# Only when deliberately replacing disposable k3d Core, Assets, Market, World and Quest data:
node deploy-k3d.mjs --reset-game-db artifacts/<build-id>/all
```

This command checks the archive and its passing E2E record before loading
any of its images into local Docker. It checks the isolated Kubernetes context
and healthy deployments, imports the same eight image tags to k3d, and rolls
out Core, BFF, Expedition, Market, Assets, World, Quest, and frontend.
For a clean automation checkout, set `HERO_ASSOCIATION_K3D_KUBECONFIG` to the
absolute path of the original ignored `deploy/k3d/.kubeconfig`, `K3D_BIN`
to the local k3d binary, and `HERO_ASSOCIATION_LOCAL_CA_CERTIFICATE` to the
original ignored `tls/certs/local-ca.crt`. Do not copy credentials or keys
into the checkout. The default manual workflow still uses the
checkout's ignored kubeconfig.
It resolves each platform image from the verified archive and checks that
every running application Pod reports a linked OCI image digest, not merely
the expected tag. It then runs the k3d browser, Expedition, Map, Quest dungeon, and
containerized market k6 suites. A failed rollout or suite restores the previous Deployment image
references, verifies them, and reports concurrent changes without overwriting
them. Run `node --test rollback-k3d.test.mjs` to test this failure path without
changing the cluster. Promotion does not build images, apply bootstrap
manifests or reset Core data. The
`--reset-game-db` variant requires a complete eight-service archive (`--reset-core-db`
remains an alias). It validates all five database identities and exact archived
bootstrap Jobs, stops application traffic and writers, then checks Core admissions,
the Expedition active index and unresolved Core/Market/Quest workflows. A failed audit
restores the previous replicas and HPAs without resetting data. After the audit,
it recreates the validated public schemas (removing retired ORM tables),
bootstraps Core, Assets, Market, World and Quest with their archived images, sets all eight
application images while stopped, restores replicas and HPAs, and runs the same
Pod-image, browser and k6 gates. A reset or later gate failure leaves the images
in place; rollback against the recreated schema requires manual investigation.
The rollback path was also exercised in the disposable k3d lab on 2026-09-28:
an older verified archive reached the browser gate, an intentionally missing
Playwright config failed that gate, and all three previous images were restored.
The read-only audit, six browser tests, and market k6 suite passed afterward.
After all gates pass, it writes ignored `k3d-promotion.json` beside the
archive with the archive checksum, image IDs, observed Pod image IDs, passed gate
names, and whether a Core reset/bootstrapping Job was used. This is a point-in-time local result, not a live health
check or part of the Floci archive upload. A failed promotion does not write
a new passing result; use `--verify-only` to check the current Pods.
Keep the archive directory: its checksum, image IDs, and E2E record are the
provenance for this local deployment. See the [k3d runbook](../deploy/k3d/README.md#promote-a-verified-archive).

To audit a running deployment against that archive later, without promoting
or rerunning the suites:

```bash
node deploy-k3d.mjs --verify-only artifacts/<build-id>/all
```

The audit checks the archive checksum and passing E2E record, healthy
Deployments, their image references, and every running application Pod's OCI
image digest. It only reads the archive and Kubernetes state: it does not load
Docker images, import to k3d, restart Pods, or write an E2E record.

## Store the verified archive in the isolated Floci lab

With Floci running as described in [`FLOCI.md`](../FLOCI.md), run from this
directory:

```bash
node publish-floci.mjs artifacts/<build-id>/all
```

This uploads the same verified archive and evidence to emulated S3 and checks
the downloaded bytes. It does not rebuild the images, deploy the application,
mount the Docker socket into Floci, or affect k3d and normal development.

To prove a deployment can consume the stored artifact without the original
build directory, download and validate it into a new ignored directory:

```bash
node fetch-floci.mjs <build-id>
```

The command prints the verified directory, loads only the archive's images,
and never contacts a real AWS endpoint.

## Preview the verified frontend as a real Floci ECS task

First start the opt-in ECS override and fetch the artifact as described in
[`FLOCI.md`](../FLOCI.md#ecs-frontend-preview). Then run:

```bash
node deploy-floci-frontend.mjs <directory-printed-by-fetch>
```

The command uses the verified image and checks its Docker image ID and
internal HTTP response without publishing a host port. It does not deploy
BFF, Core, Keycloak, or databases. Stop this disposable task with
`node stop-floci-frontend.mjs` before stopping Floci.

## Provision the Floci Core and Keycloak databases

With the opt-in ECS/RDS override running, use:

```bash
node provision-floci-core-db.mjs
node provision-floci-keycloak-db.mjs
```

These commands create or verify independent private PostgreSQL 18.6 RDS
instances on the Floci Docker network: Core at `floci:7001/hero_association`
and Keycloak at `floci:7002/keycloak`. Each gets a different generated password
in an owner-only, ignored credential file under `artifacts/`. No database
port is published on the host. The full application deployment remains
pending; see [`FLOCI.md`](../FLOCI.md#core-and-keycloak-databases-opt-in).

## Provision the Floci BFF session cache

With the same opt-in ECS/RDS/ElastiCache override running, use:

```bash
node provision-floci-bff-cache.mjs
```

This creates or verifies a private, single-node Redis-compatible cache at
`redis://floci:6379` on the Floci Docker network. It checks PING and a
short-lived SET/GET/DEL round trip. No cache port is published on the host.
Cache contents are disposable and do not survive a Floci restart. If the
pinned emulator reports the group as available but its data plane is down,
run `node provision-floci-bff-cache.mjs --recreate-empty` to explicitly
recreate it and invalidate any sessions; see
[`FLOCI.md`](../FLOCI.md#bff-session-cache-opt-in).

World and Quest each use separate PostgreSQL accounts and databases. To add
these owners to the existing Assets lab, build and verify a full eight-image
archive, run `deploy/k3d/stage-world-quest.sh ARCHIVE` from `hero-association`,
restore full k3d mode, then promote that same archive with
`node pipeline/deploy-k3d.mjs --reset-game-db ARCHIVE`. Staging adds the two
dependencies, Keycloak audiences and service identities. The coupled reset
switches callers and all five matching seeds. It removes the retired Core cache
Redis after the eight application rollouts. Keycloak users and BFF/Expedition
Redis data are retained. Return active Expeditions and finish unresolved
Core/Market/Quest work first. Restore frontend hot reload after all daily gates.
