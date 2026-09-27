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

## Local development

Copy the environment template and replace every placeholder. The local
Keycloak bootstrap credentials, database passwords, BFF client secret, OIDC
state secret, and CSRF signing key belong only in the ignored `backend/.env`
file. The three BFF security values must each be at least 32 characters.

```bash
cp .env.example .env
```

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
Account and asks for a unique Manager name. A Manager with no membership can
create one empty Level 1 agency as its leader; invitations are a later task.
The local Keycloak login page uses the versioned Hero Association theme in
`keycloak/theme/hero-association`. It preserves Keycloak's standard login
layout while matching the frontend's dark, gold-accented visual style.
The development realm includes these test accounts:

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
certutil.exe -user -addstore -f Root "$WINDOWS_CERTIFICATE_PATH"
```

Restart the browser after importing. On Linux systems using
`update-ca-certificates`, copy the CA certificate to
`/usr/local/share/ca-certificates/hero-association-local-ca.crt` and run
`sudo update-ca-certificates`. Do not import the private key.

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
frontend, BFF, Keycloak, and Core.
It starts an isolated Docker Compose project with its own ports and volumes,
so it does not share state with the development workflow above:

```bash
cd ../e2e
npm install
npm test
```

See [`../e2e/README.md`](../e2e/README.md) for the ports, cleanup behavior,
and current coverage.

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

## Isolated k3d JVM deployment

Normal host-run Quarkus development above remains the default. For the
separate k3d lab, JVM image build, import, backend deployment, generated
credentials, and Gateway verification, see
[`../deploy/k3d/README.md`](../deploy/k3d/README.md#build-and-deploy-the-jvm-backend).
The `quarkus-smallrye-health` extension exposes `/q/health/started`,
`/q/health/ready`, and `/q/health/live` for Kubernetes probes in both services.
The k3d Core remains one replica because startup still recreates its schema.
The lab's preliminary read-load command and sampled resource usage are in
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
