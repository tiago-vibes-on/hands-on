# Local build and k3d pipeline

This local pipeline builds once, verifies the archived images, and can deploy
them to k3d or store them in Floci S3 for a later AWS-lab deployment. The normal
Vite/Quarkus development workflow remains separate.

The [local Jenkins setup](../ci/jenkins/README.md) has independent Core, BFF,
and frontend worktree builds and trusted-`main` builds, plus a deploy-local job
for each service. The older complete-stack command below remains available
manually. Worktree builds need a separate manual deployment; successful `main`
builds trigger their service deploy job. A Jenkins lock serializes whole
`main` build-and-deploy pairs; all run on WSL, not GitHub runners.

## Run the complete k3d pipeline

With Docker, Java 25, Node.js 24/npm, `kubectl`, and the isolated k3d lab
already running, run from `hero-association/pipeline`:

```bash
./run-k3d-pipeline.sh
```

Optionally pass a unique build ID. The command checks the isolated cluster
and runs rollback and Core-reset target tests before building and testing Core, BFF, and
frontend once. It archives those images, runs the archive-backed Playwright
gate, then imports and deploys them to k3d and runs the k3d browser and
containerized market k6 suites. If any stage fails, later stages do not run.
The archive remains in ignored `artifacts/<build-id>/all/` for inspection or
a later deployment target. It does not reset databases or modify the normal
Compose development stack. For an intentional schema/seed change in the
disposable k3d Core database, run `./run-k3d-pipeline.sh --reset-core-db`
instead. That flag is destructive **only to k3d Core game data**: after the
archive-backed browser gate, the pipeline uses the exact verified Core image
to recreate Core's schema and deterministic seed. Keycloak, Redis, and
ordinary Compose development data are not reset. A reset promotion cannot
safely roll back to an older Core image if a later gate fails; inspect and
repair the cluster before retrying. On WSL, prefix either command with
`TESTCONTAINERS_HOST_OVERRIDE=host.docker.internal` if Testcontainers needs it.

## Build and verify in separate stages

From `hero-association/pipeline`, with Docker, Java 25, Node.js 24, and npm
available, build both JVM services and the frontend image in one run:

```bash
./build-local.sh all
```

The command runs Core and BFF Maven tests, frontend lint and build, then builds
the three Docker images. It writes a single `images.tar`, SHA-256 checksum,
and manifest under ignored `artifacts/<build-id>/all/`. The manifest records
the Git revision, whether the source worktree was dirty, and each local image
ID. The archive is the candidate to promote to multiple deployment targets;
the older direct-build k3d scripts still use fixed `:k3d` tags.

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
./build-local.sh bff demo-001
./build-local.sh frontend demo-001
```

Those commands create separate archives under `artifacts/demo-001/`. Use a
new build ID for every source revision or rebuild; existing image tags and
artifact paths are deliberately not overwritten. Local dirty changes are
included in the images but cannot be recreated from the recorded Git commit
alone. Do not publish these archives as a release without a clean source
revision and a reviewed release process.

On WSL with Docker Desktop, if Testcontainers cannot reach published ports,
run with `TESTCONTAINERS_HOST_OVERRIDE=host.docker.internal` before the command.
The archived images must pass the isolated browser E2E gate before they are
eligible for local deployment:

```bash
cd ../e2e
npm ci
npm run test:archive -- ../pipeline/artifacts/<build-id>/all
```

Use the build ID printed by `build-local.sh`. This command checks the archive
SHA-256 and manifest image IDs, loads the images, forbids app image builds or
pulls, checks the running container IDs, and runs Playwright. The ignored
`e2e-verification.json` beside the archive records `result: passed` only
after tests and cleanup succeed. `npm test` remains the source-building
development suite.

## Jenkins service artifacts

For a single-service promotion, Jenkins first runs `build-local.sh` for that
component. It then uses `assemble-service-archive.mjs` to combine the candidate
with the two live k3d images in a checksummed, three-image archive. The manifest
includes `promote_component`. The archive browser gate tests exactly this
combination, not an unrelated three-service rebuild. Build and deploy jobs
share the retained artifact under the WSL agent work directory at
`artifacts/<build-id>/all`; the Jenkins build record stores its ID and small
evidence. A build does not change k3d.

`deploy-k3d.mjs --verify-baseline` checks the two baseline Deployment
references and running Pod image digests before the browser gate. The deploy
job repeats this drift check and requires the exact archive's passing E2E record.
It imports and rolls only the promoted component. It still verifies all three
Pods and runs the k3d browser and market k6 gates. After k6, the final Pod
check briefly retries while autoscaling settles, but a persistent digest or
readiness mismatch fails promotion. A failure after rollout
restores only that component's previous reference. If either baseline changed
since the build, rebuild against the current cluster. The latest successful
per-service deployment wins.

This local mechanism needs a healthy k3d cluster even for a build, because
the other two images are read from it. It does not create a reproducible
release artifact for other environments. Floci publish, fetch, and frontend
preview reject these k3d-specific composite archives; use a full
three-image build for the separate Floci lab. Main builds use a clean GitHub
checkout; worktree builds snapshot staged, unstaged, and non-ignored new
files. Do not delete retained archives while a deploy job may still use them.

## Promote the verified archive to k3d

From `hero-association/pipeline`, with the isolated k3d lab running:

```bash
node deploy-k3d.mjs artifacts/<build-id>/all
# Only when deliberately replacing disposable k3d Core data:
node deploy-k3d.mjs --reset-core-db artifacts/<build-id>/all
```

This command checks the archive and its passing E2E record before loading
any of its images into local Docker. It checks the isolated Kubernetes context
and healthy deployments, imports the same three image tags to k3d, and rolls
out Core, BFF, and frontend.
For a clean automation checkout, set `HERO_ASSOCIATION_K3D_KUBECONFIG` to the
absolute path of the original ignored `deploy/k3d/.kubeconfig` and `K3D_BIN`
to the local k3d binary. The default manual workflow still uses the
checkout's ignored kubeconfig.
It resolves each platform image from the verified archive and checks that
every running application Pod reports a linked OCI image digest, not merely
the expected tag. It then runs the k3d Playwright and containerized market
k6 suites. A failed rollout or suite restores the previous Deployment image
references, verifies them, and reports concurrent changes without overwriting
them. Run `node --test rollback-k3d.test.mjs` to test this failure path without
changing the cluster. Promotion does not build images, apply bootstrap
manifests, reset Core data, or touch normal Compose development. The
`--reset-core-db` variant requires a complete three-service archive. After
checking the k3d context, exact Core database identity, archive, and passing
archive E2E record, it imports those images, stops Core and its HPA, and
creates a one-shot bootstrap Job with **the archived Core image**, not the
fixed `:k3d` tag. It then starts Core, restores the HPA, promotes BFF and
frontend, and runs the same Pod-image, browser, and k6 gates. If bootstrap
fails, Core remains stopped for inspection. If a later gate fails, the
reset-aware command leaves deployment images in place instead of attempting
an unsafe rollback against a newly recreated schema.
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
