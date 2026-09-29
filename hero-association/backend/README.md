# Hero Association Backend Services

The backend is split into two independently buildable Quarkus services:

```text
browser -> hero-association-bff -> hero-association-core -> Core PostgreSQL
                     |
                     +-> Keycloak -> Keycloak PostgreSQL
                     |
                     +-> BFF session Redis
```

- `hero-association-bff` is the public Backend for Frontend. It listens on
  port `8080`, authenticates browser sessions with Keycloak, protects game
  routes with CSRF, and forwards its server-held Keycloak access token with the
  existing `/api/...` contract to Core.
- `hero-association-core` owns game rules, PostgreSQL state, and the internal
  API. It validates the `hero-association-core` bearer-token audience for every
  API call, listens on port `8081`, and is not published by Docker Compose.

The BFF keeps Keycloak token state in Redis. See
[`../AUTHENTICATION.md`](../AUTHENTICATION.md) for the implementation status
and remaining Account, Manager, and authorization work.

Market endpoints share `/api/v1/market/orders`, but Market still runs inside
Core. Orders can belong to the authenticated Manager or an agency led by that
Manager; each uses its own wallet and inventory. [ADR 0003](../adr/0003-market-service-boundary.md)
records the public owner contract and the reservation/settlement work required
before extracting Market as a separate service.

Normal Compose development keeps Traefik and has no market-order rate limit.
The isolated k3d deployment uses Envoy Gateway external authorization and a
Redis-backed global per-user limit for `POST /api/v1/market/orders`; see the
[k3d edge-auth runbook](../deploy/k3d/EDGE_AUTH.md). The BFF Redis still holds
OIDC session state, not market rate-limit state.
The [local Jenkins setup](../ci/jenkins/README.md) has separate Core, BFF, and
frontend worktree and `main` builds, plus a verified-artifact deploy job for
each service. Worktree deployment is manual; trusted `main` builds deploy
automatically after their gates pass.
Normal Quarkus dev mode remains independent.

## Local development

Copy the environment template and replace every placeholder. The local
Keycloak bootstrap credentials, database passwords, BFF client secret, OIDC
state secret, and CSRF signing key belong only in the ignored `backend/.env`
file. The three BFF security values must each be at least 32 characters.

```bash
cp .env.example .env
```

Local Compose and the isolated k3d lab both bind host ports 80 and 443.
Before starting local infrastructure, stop k3d if it is running with
`k3d cluster stop hero-association` from `../deploy/k3d`. The switch keeps
both environments' database volumes; see the
[k3d switching steps](../deploy/k3d/README.md#create-the-cluster).

Start the local infrastructure (Traefik, Keycloak, Game Core PostgreSQL, and Redis), then run the Quarkus services with hot reload:

```bash
# Terminal 1, from backend/. This runs docker compose -f compose.infra.yaml up --detach.
./scripts/start-infra.sh

# Terminal 2
cd hero-association-core
./mvnw quarkus:dev

# Terminal 3, from backend/
./scripts/run-bff-dev.sh
```

Always start the BFF with this script for local development. It loads
`backend/.env` and rejects missing or placeholder BFF secrets; running
`./mvnw quarkus:dev` directly without those variables can make Keycloak
reject the login callback. The BFF client secret must also match the value
stored in the imported Keycloak realm. Keycloak imports the realm only once,
so changing that secret in `.env` later requires updating the existing
Keycloak client or recreating the local realm.

Stop those dependencies with:

```bash
docker compose -f compose.infra.yaml down
```

The default local host-port allocation avoids commonly used application and
database ports:

| Service | Host port |
| --- | --- |
| Frontend (Vite) | `15172` |
| BFF | `17080` |
| Game Core | `17081` |
| Keycloak | `17180` |
| Game Core PostgreSQL | `15431` |
| BFF session Redis | `16379` |

Quarkus dev mode also assigns Core debugger port `15005` and BFF debugger port
`15006`, both bound to `localhost`.

For normal development, open `https://heroassociation.test`. Traefik proxies the
host-run Vite server and BFF under that one secure origin; Keycloak is available
at `https://auth.heroassociation.test`. Vite, BFF, and Keycloak also retain
their direct local addresses (`http://localhost:15172`,
`http://localhost:17080`, and `http://localhost:17180`) for development and
diagnostics. Change any local host port in `backend/.env` before starting
Compose; the BFF connects to Redis at `localhost:16379` by default; change
`HERO_ASSOCIATION_BFF_REDIS_HOST_PORT` in `backend/.env` when needed.

To start only Keycloak and its database, run this from `backend/`:

```bash
./scripts/start-infra.sh postgres-keycloak keycloak
```

The imported `hero-association` realm enables native registration and contains
its confidential `hero-association-bff` OpenID Connect client. Its access-token
mapper adds the `hero-association-core` audience required by Game Core. Email
verification is disabled locally because SMTP is not configured. Navigate to
the frontend and select **Sign in** for an existing account or **Create
account** to open Keycloak's native registration form. Registration does not
request first or last name; standard email and password inputs create the
account. Google login is a post-MVP task. The first signed-in visit provisions an
Account and asks for a unique Manager name. Onboarding also creates a personal
Level 1 Warrior, Mage, and Archer with Level 1 skills, zero personal gold, and
empty personal inventories. These assets belong to the Manager, not the agency.
A Manager with no membership can create one empty Level 1 agency as its leader;
invitations are a later task. Parties belong to individual Managers inside
their agency. A Manager can assign their own available heroes or available
agency-owned heroes to a prepared party. The agency leader sets each agency
hero's nonnegative per-quest borrowing fee (0 by default). Party assignment
is free; quest start submits the expected total and atomically transfers it
from the Manager's personal gold to the agency. Stale quotes and insufficient
funds reject the start without charging. Any
onboarded Manager can claim a globally available recruit into their personal
roster through `POST /api/v1/recruits/{recruitId}/claim`, even before creating
an agency. Agency leaders can explicitly claim a recruit for their agency
through `POST /api/v1/agencies/{agencyId}/recruits/{recruitId}/claim`; all
claims are exclusive across both ownership choices.
The local Keycloak login page uses the versioned Hero Association theme in
`keycloak/theme/hero-association`. It preserves Keycloak's standard login
layout while matching the frontend's dark, gold-accented visual style.
The development realm includes these established workflow accounts. Ten more
seeded `managerN@mail.com` / `managerN` accounts belong to three multi-Manager
agencies; see the complete [test-data map](../TEST_DATA.md):

| Email | Password | Keycloak profile | Seeded Manager | Agency role |
| --- | --- | --- | --- | --- |
| `user1@mail.com` | `user1` | User1 Last1 | User 1 | Dawnwatch Agency leader |
| `user2@mail.com` | `user2` | User2 Last2 | User 2 | Ironridge Exchange leader |
| `user3@mail.com` | `user3` | User3 Last3 | None | No agency (onboarding test user) |

These credentials exist only for local development and must never be used in
production.

Keycloak imports the versioned realm only when it does not already exist. This
is an early-stage project: reset and reseed local or pre-production data rather
than keeping compatibility with the current data. To recreate the realm during
local development, stop the infrastructure with `docker compose -f compose.infra.yaml down --volumes` and start it again.

## Local HTTPS gateway

Traefik is part of the normal development infrastructure. Vite and both
Quarkus services still run on the host for hot reload. Traefik serves
`https://heroassociation.test`, forwards `/api` and `/auth` to the BFF, and
serves Keycloak at `https://auth.heroassociation.test`.

Add these entries to your operating system's hosts file (names only, no
`https://` prefix):

```text
127.0.0.1 heroassociation.test
127.0.0.1 auth.heroassociation.test
```

On Windows the file is `C:\Windows\System32\drivers\etc\hosts` and requires
an Administrator editor. Start the infrastructure from `backend/`:

```bash
./scripts/start-infra.sh
```

The script generates a local CA and certificate in the ignored
`../traefik/certs/` directory, detects the current WSL address for host-run
Vite and BFF, and starts `compose.infra.yaml`. Run it again after WSL
restarts. On other systems, set `HERO_ASSOCIATION_DEV_HOST_ADDRESS` if
automatic detection is not appropriate. Do not commit the CA private key.

Trust `../traefik/certs/local-ca.crt` once in the operating system where your
browser runs. For Chrome or Edge on Windows with the project running in WSL,
import it into the Windows current-user root store from this directory:

```bash
WINDOWS_CERTIFICATE_PATH="$(wslpath -w ../traefik/certs/local-ca.crt)"
(cd /mnt/c && certutil.exe -user -addstore -f Root "$WINDOWS_CERTIFICATE_PATH")
```

Restart the browser after importing. On Linux systems using
`update-ca-certificates`, copy the CA certificate to
`/usr/local/share/ca-certificates/hero-association-local-ca.crt` and run
`sudo update-ca-certificates`. Do not import the private key.

Windows `curl.exe` may report `CRYPT_E_NO_REVOCATION_CHECK` for this offline
development CA. For a local CLI check, use `--ssl-revoke-best-effort`; do not
use `--insecure`, which would also disable certificate validation.

The Windows browser uses Docker's Windows port forwarding. If an unrelated
K3s installation is already listening on port 443 inside WSL, a WSL
`curl https://localhost` check may reach K3s instead of this gateway; test
through the Windows browser or `curl.exe` in that case. The development
certificate is for `.test` hostnames only, not production.

Stop the local infrastructure without deleting its data with
`docker compose -f compose.infra.yaml down`. Never add `--volumes` unless you
explicitly intend to reset the local Keycloak and Core databases.

## Browser end-to-end tests

The repository-level [`../e2e`](../e2e) Playwright project verifies browser
registration, logout, relogin, and other authentication flows through the
frontend, BFF, Keycloak, and Core. The k3d browser suite separately checks
the Envoy market-order limit across two BFF instances and separate sessions.
It starts an isolated Docker Compose project with its own ports and volumes,
so it does not share state with the development workflow above:

```bash
cd ../e2e
npm install
npm test
```

For a build-once candidate, first create the complete image archive from
[`../pipeline`](../pipeline/README.md). From `e2e/`, run:

`npm run test:archive -- ../pipeline/artifacts/<build-id>/all`

The regular `npm test` above remains the source-building development check.

See [`../e2e/README.md`](../e2e/README.md) for the ports, cleanup behavior,
current coverage, and the k6 check of the deployed k3d market-order limit.

## Containers

From this directory, run the complete JVM stack:

```bash
docker compose up --build
```

For a packaged HTTPS check, stop the normal development stack first because
both gateways use ports 80 and 443. Then run:

```bash
../traefik/generate-local-certs.sh
docker compose -f compose.yaml -f compose.traefik.yaml up --build
```

This builds the frontend into its own Nginx container and routes it, the BFF,
and Keycloak through Traefik. The same local CA is used; Core stays private.

Both packaged Compose stacks explicitly reset and reseed the disposable Core
database on every Core startup; do not use them with data you need to keep.

The JVM Compose workflow publishes the BFF at `http://localhost:17080` and
Keycloak at `http://localhost:17180`; Core remains on the private Compose
network. The native Compose workflow has separate default ports—BFF `19080`,
Keycloak `19180`, and BFF Redis `19679`—so it can run alongside the
hot-reload workflow. It runs a native Core behind the JVM BFF:

```bash
docker compose -f compose.native.yaml up --build
```

Build and test each service from its own directory. Core-specific workflows,
including native compilation, are documented in
[`hero-association-core/README.md`](hero-association-core/README.md).
For one reusable local archive containing the Core, BFF, and frontend images,
use the separate [`pipeline`](../pipeline/README.md) build stage. It runs
the service tests and frontend checks without deploying or changing this
development environment.

## Isolated k3d JVM deployment

Normal host-run Quarkus development above remains the default. For the
separate k3d lab, JVM image build, import, backend deployment, generated
credentials, and Gateway verification, see
[`../deploy/k3d/README.md`](../deploy/k3d/README.md#build-and-deploy-the-jvm-backend).
That workflow supports rebuilding and rolling only BFF when Core has not
changed, without resetting the lab database.

For build-once deployment of an E2E-verified Core/BFF/frontend archive, use
the [pipeline promotion command](../pipeline/README.md#promote-the-verified-archive-to-k3d).
Its default mode rolls the isolated k3d app Deployments without database
bootstrap. For an intentional schema/seed reset, the full verified archive
can be promoted with `--reset-core-db`; only the isolated k3d Core data is
recreated from the archived Core image. Neither mode changes this host-run
development workflow. The
[complete local pipeline](../pipeline/README.md#run-the-complete-k3d-pipeline)
also runs the build and both browser test gates in one command.

The `quarkus-smallrye-health` extension exposes `/q/health/started`,
`/q/health/ready`, and `/q/health/live` for Kubernetes probes in both services.
The k3d Core validates its schema on startup; a separate one-shot Job seeds a
new lab database. For an existing disposable k3d Core database after a
schema change, use `../pipeline/run-k3d-pipeline.sh --reset-core-db` to build,
verify, recreate Core data from the exact archived Core image, and deploy.
The older direct-build reset in `deploy/k3d/` uses the fixed `:k3d` image
and must not be mixed with an archive promotion. Core's scheduled jobs use
PostgreSQL advisory locks to avoid
overlapping across Pods. An isolated concurrency test exercises two, four,
and eight Core Pods, active combat and recovery, concurrent database reads,
and Pod restarts without duplicate quest resolution. Separate CPU HPAs keep
BFF and Core between two and eight Pods in k3d; normal host-run development
remains unchanged. See the
[autoscaling test](../deploy/k3d/README.md#autoscaling-and-sustained-validation)
for the repeatable 16-client run and BFF rollout check.
The [mixed-workload capacity lab](../deploy/k3d/CAPACITY.md) measures
authenticated reads and activity writes against a temporary Core database;
normal host-run development and its data are unchanged. The preliminary
read-load baseline is in
[`../deploy/k3d/README.md`](../deploy/k3d/README.md#sustained-read-load-baseline).

The k3d BFF and Core export OTLP traces, HTTP/JVM metrics, and structured
logs to the private collector. See the
[`k3d telemetry verification`](../deploy/k3d/README.md#verify-application-telemetry)
steps for Grafana. The k3d installer also collects Istio/Envoy signals and
Pod resource metrics, with 24-hour lab retention and Traffic/Scaling
dashboards. Normal host-run development and Docker Compose keep telemetry
off by default. To opt in for host-run development, provide an
OTLP collector (for example, port-forward the k3d collector's gRPC port
`4317` to a host port), then set `HERO_ASSOCIATION_OTEL_DISABLED=false`
and `HERO_ASSOCIATION_OTLP_ENDPOINT=http://127.0.0.1:<host-port>` before
starting each Quarkus process. The normal local infrastructure does not
require the k3d collector.
