# Local build pipeline

This is the build stage for a future build-once, deploy-to-k3d-and-AWS flow.
It does not deploy or modify either environment. The normal Vite/Quarkus dev
workflow remains separate.

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
the current k3d scripts still build and import their own fixed `:k3d` tags.

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
The isolated browser E2E suite remains a separate gate:

```bash
cd ../e2e
npm test
```

The E2E stack currently rebuilds images from source; making it consume the
exact archived images and making k3d import them are the next delivery tasks.
