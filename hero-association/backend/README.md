# Hero Association Backend Services

The backend is split into two independently buildable Quarkus services:

```text
browser -> hero-association-bff -> hero-association-core -> Core PostgreSQL
                     |
                     +-> Keycloak -> Keycloak PostgreSQL
                     |
                     +-> BFF session PostgreSQL
```

- `hero-association-bff` is the public Backend for Frontend. It listens on
  port `8080`, authenticates browser sessions with Keycloak, protects game
  routes with CSRF, and forwards its server-held Keycloak access token with the
  existing `/api/...` contract to Core.
- `hero-association-core` owns game rules, PostgreSQL state, and the internal
  API. It validates the `hero-association-core` bearer-token audience for every
  API call, listens on port `8081`, and is not published by Docker Compose.

The BFF keeps Keycloak token state in its own PostgreSQL database. See
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

Start the local infrastructure (Keycloak and all three PostgreSQL databases),
then run the Quarkus services with hot reload:

```bash
# Terminal 1, from backend/
docker compose -f compose.infra.yaml up --detach

# Terminal 2
cd hero-association-core
./mvnw quarkus:dev

# Terminal 3
cd hero-association-bff
set -a
source ../.env
set +a
./mvnw quarkus:dev
```

Stop those dependencies with:

```bash
docker compose -f compose.infra.yaml down
```

The frontend calls the BFF at `http://localhost:8080`. Core connects to the
Compose PostgreSQL database at `localhost:5432`; BFF session storage is at
`localhost:5433`. Keycloak is available at `http://localhost:8180`, including
its admin console at
`http://localhost:8180/admin/`.

To start only Keycloak and its database, run this from `backend/`:

```bash
docker compose up --detach postgres-keycloak keycloak
```

If port `5433` is already used, set
`HERO_ASSOCIATION_BFF_DATABASE_HOST_PORT` before the Compose command and set
`HERO_ASSOCIATION_BFF_DATABASE_URL` to the matching `postgresql://localhost`
address before starting the BFF.

The imported `hero-association` realm enables native registration and contains
the confidential `hero-association-bff` OpenID Connect client. Its access-token
mapper adds the `hero-association-core` audience required by Game Core. Email
verification is disabled locally because SMTP is not configured. Navigate to
the frontend and select **Sign in** for an existing account or **Create
account** to open Keycloak's native registration form. Google login is a later
task. The first signed-in visit provisions an Account and asks for a unique
Manager name. Access to the seeded prototype requires an
`AgencyMember` record; creating an agency and invitations are later tasks.
The local Keycloak login page uses the versioned Hero Association theme in
`keycloak/theme/hero-association`. It preserves Keycloak's standard login
layout while matching the frontend's dark, gold-accented visual style.
The development realm includes these test accounts:

| Email | Password | Seeded Manager | Agency role |
| --- | --- | --- |
| `user1@mail.com` | `user1` | User 1 | Dawnwatch Agency leader |
| `user2@mail.com` | `user2` | User 2 | Ironridge Exchange leader |

These credentials exist only for local development and must never be used in
production.

Keycloak imports the versioned realm only when it does not already exist. To
recreate it during local development, stop the stack with `docker compose down
--volumes` and start it again.

## Local HTTPS gateway

The default development workflow above deliberately keeps Vite and both
Quarkus services on the host for hot reload. The optional Caddy workflow runs
the complete application in containers instead: Caddy serves the built React
application, proxies same-origin `/api` and `/auth` requests to the BFF, and
proxies Keycloak through a separate local hostname. It is useful for checking
the real HTTPS and OIDC deployment topology, not for day-to-day frontend or
backend editing.

Before starting it, add these hostname-only entries to your operating system's
hosts file. Do not include `https://` in that file; the scheme belongs only in
the browser URL.

```text
127.0.0.1 heroassociation.test
127.0.0.1 auth.heroassociation.test
```

On Linux and macOS, the hosts file is `/etc/hosts`. On Windows, it is normally
`C:\Windows\System32\drivers\etc\hosts` and requires an Administrator editor.
The local gateway requires host ports `80` and `443` to be available.

Start the isolated Caddy Compose project from `backend/`:

```bash
docker compose -p hero-association-caddy -f compose.yaml -f compose.caddy.yaml up --build --detach
```

Caddy creates a local development certificate authority for the `.test`
domains. Export and trust its root certificate once so the browser accepts the
local HTTPS certificates:

```bash
docker compose -p hero-association-caddy -f compose.yaml -f compose.caddy.yaml cp caddy:/data/caddy/pki/authorities/local/root.crt ../caddy/hero-association-local-root.crt

# Linux systems that use update-ca-certificates
sudo cp ../caddy/hero-association-local-root.crt /usr/local/share/ca-certificates/
sudo update-ca-certificates
```

The exported certificate is ignored by Git. Use your operating system's trust
store tools for macOS or Windows. When the services run in WSL but Chrome or
Edge runs on Windows, trust the certificate in the Windows current-user store;
the Windows browser and its Windows hosts file are the relevant client-side
environment. From this `backend/` directory, no Administrator permission is
required:

```bash
WINDOWS_CERTIFICATE_PATH="$(wslpath -w ../caddy/hero-association-local-root.crt)"
certutil.exe -user -addstore -f Root "$WINDOWS_CERTIFICATE_PATH"
```

Chromium-based browsers on Linux commonly use an NSS certificate database in
addition to the system certificate bundle.
If Chrome or Chromium still displays a privacy warning after the system import,
install the NSS tools and import the same root for the current user:

```bash
sudo apt-get install --yes libnss3-tools
mkdir -p "$HOME/.pki/nssdb"
[ -f "$HOME/.pki/nssdb/cert9.db" ] || certutil -d sql:"$HOME/.pki/nssdb" -N --empty-password
certutil -d sql:"$HOME/.pki/nssdb" -A -t "C,," \
  -n "Hero Association Caddy Local CA" \
  -i ../caddy/hero-association-local-root.crt
```

Fully quit and reopen the browser after either trust-store change. This was
verified with Chromium: it accepts both local Caddy HTTPS endpoints without
certificate-error bypasses after the NSS import. Then open
`https://heroassociation.test`; the Keycloak login and admin console are at
`https://auth.heroassociation.test`. Caddy is the only public service in this
workflow: BFF, Game Core, and every PostgreSQL database stay on the private
Compose network.

The Keycloak realm import contains the Caddy callback and post-logout URLs. If
the local Keycloak realm already exists, reset this isolated project before
starting it so Keycloak imports the updated realm:

```bash
docker compose -p hero-association-caddy -f compose.yaml -f compose.caddy.yaml down --volumes
```

This removes only the `hero-association-caddy` project's local containers and
volumes. It also creates a new local Caddy certificate authority on the next
start, so export and trust the new root certificate again. Do not use
`--volumes` if you want to retain the current local data and trusted Caddy
certificate authority.

## Browser end-to-end tests

The repository-level [`../e2e`](../e2e) Playwright project verifies the real
browser authentication flow through the frontend, BFF, Keycloak, and Core.
It starts an isolated Docker Compose project with its own ports and volumes,
so it does not share state with the development workflow above:

```bash
cd ../e2e
npm --prefix ../frontend install
npm install
npm run test:auth
```

See [`../e2e/README.md`](../e2e/README.md) for the ports, cleanup behavior,
and current coverage.

## Containers

From this directory, run the complete JVM stack:

```bash
docker compose up --build
```

Only the BFF is published at `http://localhost:8080`; Core remains on the
private Compose network. Keycloak is separately published at
`http://localhost:8180`. The native Compose workflow runs a native Core behind
the JVM BFF:

```bash
docker compose -f compose.native.yaml up --build
```

Build and test each service from its own directory. Core-specific workflows,
including native compilation, are documented in
[`hero-association-core/README.md`](hero-association-core/README.md).
