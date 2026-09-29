# Local build and k3d pipeline

This local pipeline builds once, verifies the archived images, and can deploy
them to k3d or store them in Floci S3 for a later AWS-lab deployment. The normal
Vite/Quarkus development workflow remains separate.

## Run the complete k3d pipeline

With Docker, Java 25, Node.js 24/npm, `kubectl`, and the isolated k3d lab
already running, run from `hero-association/pipeline`:

```bash
./run-k3d-pipeline.sh
```

Optionally pass a unique build ID. The command checks the isolated cluster,
builds and tests Core, BFF, and frontend once, archives those images, runs the
archive-backed Playwright gate, then imports and deploys them to k3d and runs
the k3d browser and containerized market k6 suites. If any stage fails,
later stages do not run. The archive remains in ignored
`artifacts/<build-id>/all/` for inspection or a later deployment target. It does not reset databases or modify the normal
Compose development stack. On WSL, prefix the command with
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

## Promote the verified archive to k3d

From `hero-association/pipeline`, with the isolated k3d lab running:

```bash
node deploy-k3d.mjs artifacts/<build-id>/all
```

This command checks the archive and its passing E2E record before loading
any of its images into local Docker. It checks the isolated Kubernetes context
and healthy deployments, imports the same three image tags to k3d, and rolls
out Core, BFF, and frontend.
It resolves each platform image from the verified archive and checks that
every running application Pod reports a linked OCI image digest, not merely
the expected tag. It then runs the k3d Playwright and containerized market
k6 suites. A failed rollout or suite restores the previous deployment image
references. It does not build images, apply bootstrap manifests, reset Core
data, or touch normal Compose development.
After all gates pass, it writes ignored `k3d-promotion.json` beside the
archive with the archive checksum, image IDs, observed Pod image IDs, and
passed gate names. This is a point-in-time local result, not a live health
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
