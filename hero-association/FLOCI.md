# Local AWS exploration with Floci

Floci is an optional local AWS emulator. It does not replace the normal
development environment in `backend/compose.infra.yaml`, and the application
does not connect to it yet. It can store the exact E2E-verified image archive
from the local build pipeline in its emulated S3; this is artifact promotion,
not an application deployment. Run these commands from `hero-association/` in WSL;
Docker Desktop runs the container and exposes it to Windows at
`http://localhost:4566`.

```bash
docker compose -f compose.floci.yaml up --detach
docker compose -f compose.floci.yaml ps
curl --fail http://localhost:4566/_localstack/health
```

After `pipeline/build-local.sh all` and the archive-backed E2E gate have
passed, publish the same archive to Floci S3 without building again:

```bash
node pipeline/publish-floci.mjs pipeline/artifacts/<build-id>/all
```

The script accepts only the loopback Floci endpoint, checks the archive and
its passing E2E record, uploads `images.tar`, checksum, manifest, and E2E
evidence under `s3://hero-association-builds/builds/<build-id>/all/`, then
downloads each object to verify its SHA-256. Re-running it verifies identical
existing objects; it refuses to overwrite a different object at the same key.
It needs Node.js 24, Docker, and the running Floci container, but not the AWS
CLI or access to a real AWS account. Keep the local archive until the future
application-deployment stage consumes it. The S3 bucket is persistent in the
Floci volume and is not removed by `docker compose down`.

To consume the stored artifact independently of the original build directory:

```bash
node pipeline/fetch-floci.mjs <build-id>
```

It creates a new directory under ignored `pipeline/artifacts/`, verifies the
downloaded checksum, image IDs, and passing E2E record, and loads the images.

To explore S3 with an installed AWS CLI, use a terminal with **dummy**
credentials and an explicit local endpoint:

```bash
export AWS_ACCESS_KEY_ID=test
export AWS_SECRET_ACCESS_KEY=test
export AWS_DEFAULT_REGION=us-east-1
aws --endpoint-url=http://localhost:4566 s3 mb s3://hero-association-lab
aws --endpoint-url=http://localhost:4566 s3 ls
```

Keep `--endpoint-url` on every command so it cannot accidentally target a real
AWS account. The AWS CLI is optional and is not installed by this Compose file.

Without the AWS CLI, a quick S3 smoke test is possible with `curl`:

```bash
curl --fail --request PUT http://localhost:4566/hero-association-lab
curl --fail --request PUT --data-binary 'Hello from Hero Association' \
  http://localhost:4566/hero-association-lab/hello.txt
curl --fail http://localhost:4566/hero-association-lab/hello.txt
```

## ECS frontend preview (opt-in)

The base Compose file deliberately does not mount the Docker daemon socket.
For a real ECS task, start Floci with the separate override from WSL:

```bash
docker compose -f compose.floci.yaml -f compose.floci.ecs.yaml up --detach
node pipeline/fetch-floci.mjs <build-id>
node pipeline/deploy-floci-frontend.mjs <directory-printed-by-fetch>
```

The override gives Floci **broad control of the shared Docker daemon** and
runs it as root so it can access the socket. Use it only for this local lab.
The deploy command checks the archive and E2E record, registers one ECS EC2
task using the exact frontend image, and checks both the running Docker image
ID and internal HTTP response. It reuses a matching running task and refuses
to replace a different build implicitly. No task port is published on the
host. Frontend API and login flows are not functional until BFF, Core,
Keycloak, and their independent data services are deployed. This is not yet
the full application at `aws.heroassociation.test`.

## Core and Keycloak databases (opt-in)

With the ECS/RDS override running, provision or verify separate private
PostgreSQL 18.6 instances for Core and Keycloak:

```bash
node pipeline/provision-floci-core-db.mjs
node pipeline/provision-floci-keycloak-db.mjs
```

Each first run generates a different random password in an ignored, owner-only
credential file: `pipeline/artifacts/floci-core-db.json` or
`pipeline/artifacts/floci-keycloak-db.json`. Keep both files while their RDS
instances exist; later ECS tasks will read the appropriate one. Core uses
`hero-association-core-lab`, database `hero_association`, at `floci:7001`;
Keycloak uses `hero-association-keycloak-lab`, database `keycloak`, at
`floci:7002`. Both are reachable only by containers on the Floci Docker
network. Neither RDS proxy nor PostgreSQL container publishes a host port.
Both scripts verify a real SQL connection, not just RDS's `available` metadata.

If an instance was created manually before the credential file existed,
provide its password once as `FLOCI_CORE_DB_PASSWORD` or
`FLOCI_KEYCLOAK_DB_PASSWORD` when running the corresponding script.
The pinned Floci `2.1.0` image rejects `StopDBInstance`; do not use that
operation for this lab. A Floci restart with the opt-in override preserves
both RDS resources and their database connections.

## BFF session cache (opt-in)

With the same container-backed override, provision or verify the isolated
single-node ElastiCache replication group for the BFF:

```bash
node pipeline/provision-floci-bff-cache.mjs
```

Floci runs a Redis-compatible Valkey container behind `redis://floci:6379`.
Only containers on the Floci Docker network can reach this endpoint; port
`6379` is not published on the host. The script verifies PING and a temporary
SET/GET/DEL round trip. This local learning cache has no authentication,
transport encryption, or replica, and its contents are lost when Floci
restarts. It is not a production cache configuration. The future BFF ECS task
will use this endpoint; normal development keeps its separate Redis container.

With the pinned Floci `2.1.0` image, a restart can leave the replication group
marked `available` without restarting its Valkey container. The provisioner
checks the actual data plane and fails in that case. To explicitly delete and
recreate only this disposable lab cache (invalidating any BFF sessions), run:

```bash
node pipeline/provision-floci-bff-cache.mjs --recreate-empty
```

When finished with container-backed services, return to socket-free S3-only mode:

```bash
node pipeline/stop-floci-frontend.mjs
docker compose -f compose.floci.yaml up --detach
```

The RDS and cache data planes are unavailable in S3-only mode even if their
control-plane records say `available`; restart with the opt-in override and
run both DB provisioners and the BFF cache provisioner to verify connections.
If the cache data plane did not restore, use its explicit `--recreate-empty`
recovery command above.

To stop Floci entirely, run `docker compose -f compose.floci.yaml down`.

The named Floci volume persists across `down`. Floci publishes only its API
port `4566` on `127.0.0.1`; the ECS preview task listens on port `80` inside
its container but publishes no host port. The planned `aws.heroassociation.test`
and `auth.aws.heroassociation.test` hostnames remain reserved for the later
complete AWS-lab application.
