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

Run Keycloak and all three PostgreSQL databases in Docker, then run the Quarkus
services with hot reload:

```bash
# Terminal 1, from backend/
docker compose up --detach postgres-core postgres-keycloak postgres-bff keycloak

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

The frontend calls the BFF at `http://localhost:8080`. Core connects to the
Compose PostgreSQL database at `localhost:5432`; BFF session storage is at
`localhost:5433`. Keycloak is available at `http://localhost:8180`, including
its admin console at
`http://localhost:8180/admin/`.

If port `5433` is already used, set
`HERO_ASSOCIATION_BFF_DATABASE_HOST_PORT` before the Compose command and set
`HERO_ASSOCIATION_BFF_DATABASE_URL` to the matching `postgresql://localhost`
address before starting the BFF.

The imported `hero-association` realm enables native registration and contains
the confidential `hero-association-bff` OpenID Connect client. Its access-token
mapper adds the `hero-association-core` audience required by Game Core. Email
verification is disabled locally because SMTP is not configured. Navigate to
the frontend and select **Sign in** to create or use a local account. Google
login is a later task. The first signed-in visit provisions an Account and asks
for a unique Manager name. Access to the seeded prototype requires an
`AgencyMember` record; creating an agency and invitations are later tasks.
The local Keycloak login page uses the versioned Hero Association theme in
`keycloak/theme/hero-association`. It preserves Keycloak's standard login
layout while matching the frontend's dark, gold-accented visual style.
The development realm includes these test accounts:

| Email | Password | Seeded Manager | Agency role |
| --- | --- | --- |
| `user1@mail.com` | `user1` | Tiago | Dawnwatch Agency leader |
| `user2@mail.com` | `user2` | Mara | Ironridge Exchange leader |

These credentials exist only for local development and must never be used in
production.

Keycloak imports the versioned realm only when it does not already exist. To
recreate it during local development, stop the stack with `docker compose down
--volumes` and start it again.

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
