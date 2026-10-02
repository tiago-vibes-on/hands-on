# Hero Association Backend Services

Core and BFF run in full k3d and hybrid mode. Expedition powers the Map in
both modes:

```text
browser -> hero-association-bff -> hero-association-core -> Core PostgreSQL
                     |                    |
                     |                    +-> Core Creature cache Redis
                     +-> Keycloak -> Keycloak PostgreSQL
                     |
                     +-> BFF session Redis
                     +-> Expedition -> Expedition Redis
                              +-> Core admission and settlement
```

- `hero-association-bff` is the public Backend for Frontend. It listens on
  port `8080`, authenticates browser sessions with Keycloak, protects game
  routes with CSRF, and forwards its server-held Keycloak access token with the
  existing `/api/...` contract to Core.
- `hero-association-core` owns game rules, PostgreSQL state, and the internal
  API. It validates the `hero-association-core` bearer-token audience for every
  API call, listens on port `8081`, and is not published by Docker Compose.

Core persists rune equipment on every equip or unequip command. The authenticated
Manager may choose their personal or their agency's rune inventory; any agency
member may use agency runes, but agency gold permissions are unchanged.
Personal and agency Heroes can equip only while at the agency.

`hero-association-expedition` is an opt-in Quarkus service
with a Redis-backed encounter loop. Core admission supplies a pinned Hero
baseline, and Redis fences orphan release against delayed Start commands.
Expedition validates a dedicated bearer-token audience for owner-scoped
Start, Get, Continue, Return, and active-run HTTP operations; BFF routes
that API prefix to Expedition. The player API and BFF WebSocket are disabled
by default. The socket checks the BFF session, exact Origin, and run ownership
before sending a current snapshot on connect or reconnect at
`/ws/v1/expeditions/{expeditionId}`. A BFF scheduler pushes changed fight
visuals only to local subscribers, reading the private Redis-only Expedition
visual API at most once per subscribed run per second, without per-frame Core reads.
Expedition plans each non-interactive fight once and projects five-second event
windows from its Redis timeline; see [the timeline design](../COMBAT_TIMELINE.md).
Each Troll Field encounter has three seeded Trolls (2,000 HP and 4 attack
damage each). The Map shows equipped rune slots
and both Mage spell slots (locked until the required Magic Level). An
accessible panel names the runes and their effects and explains spell
requirements, mana cost, and cooldown; narrow layouts can scroll the battle
horizontally. A separate panel shows the current authoritative Hero totals
and carried assets. Hero changes settle only on return, and creature loot is
not yet awarded in the first Troll Field. The
auto-continue toggle is off by default; when enabled, the open Map page sends
the normal Continue command 1.5 seconds after a victory. Leaving the page,
a wipe, or a return request does not continue the run. User 2's disposable
seeded party has a Magic Level 15 Mage and equipped runes to demonstrate these
visuals; other starter skills still begin at Level 1.
For integration work,
set `HERO_ASSOCIATION_EXPEDITION_WEBSOCKET_ENABLED=true` in BFF,
`HERO_ASSOCIATION_EXPEDITION_VISUAL_API_ENABLED=true` in Expedition, and the
same uncommitted 32-character-or-longer
`HERO_ASSOCIATION_EXPEDITION_BFF_SERVICE_KEY` in both services; enable the
Expedition player API deliberately as well. An opt-in frontend Map page
now uses these commands and the socket. The Map journey passed in full k3d
and hybrid mode; Core Quest combat remains live.
Agency-state Hero responses include cumulative `experience`; a successful
fight can increase XP without immediately increasing a Hero level.
The private settlement handoff is disabled by default. See the [Expedition README](hero-association-expedition/README.md)
and [settlement handoff](../EXPEDITION_SETTLEMENT.md). To exercise internal
admission, set the same uncommitted
`HERO_ASSOCIATION_EXPEDITION_CORE_SERVICE_KEY` in Core and Expedition.

The authenticated Expedition browser journey runs in the disposable k3d
full-stack gate; see the [E2E runbook](../e2e/README.md). It verifies Map,
WebSocket reconnect, and settlement without changing daily game data.

Redis and RabbitMQ for daily Expedition development stay in k3d. The setup
Job owns durable RabbitMQ topology; Core and Expedition use distinct
least-privilege service accounts. See the
[Expedition README](hero-association-expedition/README.md) for credentials.

The [k3d Expedition runbook](../deploy/k3d/EXPEDITION.md) covers private staging,
and the [integration runbook](../deploy/k3d/EXPEDITION_INTEGRATION.md) covers
the opt-in authenticated k3d path. Normal backend deployment does not
enable Expedition; rerun the integration command after it. The k3d frontend
build enables Map by default.

For an explicitly requested four-service build and k3d promotion, run
`../pipeline/run-k3d-pipeline.sh` as described in the
[pipeline README](../pipeline/README.md). It archives Core, BFF, Expedition,
and a Map-enabled frontend, then verifies browser, Map, and market paths.

The pure combat rules and snapshots live in `hero-association-lib/combat-engine`,
shared by Core and Expedition. The library has no Quarkus or
infrastructure dependencies. From `backend/`, a clean build runs
`./mvnw -pl hero-association-core -am package` or
`./mvnw -pl hero-association-expedition -am package`. For direct module commands
(including `quarkus:dev`), first install the library with
`./mvnw -pl hero-association-lib/combat-engine -am install`. See the
[library README](hero-association-lib/README.md). The private Assets
reservation and settlement tests use disposable PostgreSQL and Redis via
Testcontainers. From `backend/`, run
`./mvnw -pl hero-association-core -am -Dtest=AssetsServiceTest -Dsurefire.failIfNoSpecifiedTests=false test`.
Core test PostgreSQL Dev Services are removed after the test JVM exits, even if
`~/.testcontainers.properties` enables global container reuse. This does not
affect the k3d Core or Keycloak databases.

Existing local databases may retain the retired `combat_battle_registration`
and `combat_progression_inbox` tables. They are not used by the new code;
an explicit disposable Core schema reset after promoting the updated image
removes them. Do not drop these tables under an older Core Pod.

The BFF keeps Keycloak token state in Redis. The BFF pins Quarkus 3.40.1
to reject missing or expired token-state keys as unauthenticated; older
3.33.3.2 returned HTTP 500 for that case. Redis outages remain server errors. See
[`../AUTHENTICATION.md`](../AUTHENTICATION.md) for the implementation status
and remaining Account, Manager, and authorization work.

Market endpoints share `/api/v1/market/orders`, but Market still runs inside
Core. Orders can belong to the authenticated Manager or an agency led by that
Manager; each uses its own wallet and inventory. [ADR 0003](../adr/0003-market-service-boundary.md)
records the public owner contract and the reservation/settlement work required
before extracting Market as a separate service. Core now has an internal
[Assets contract](../ASSETS_CONTRACT.md) for idempotent Manager/agency gold
and item reservations, releases, and trade settlement. It is not yet the
public Market write path or an HTTP endpoint; ordinary orders still use the
existing single-Core-transaction flow.

Gold transfers use `POST /api/v1/gold-transfers` through the BFF. On the
Agency page, any authenticated Manager can send personal gold to any agency
by its exact name; only that agency's leader can send treasury gold to any
Manager by display name, including themselves. Names are case-insensitive.
Use `direction: MANAGER_TO_AGENCY` with `agencyName` and `amountGold`, or
`direction: AGENCY_TO_MANAGER` with those fields plus `managerName`.
These are atomic moves of existing gold, without a market fee or agency
earnings share. The endpoint returns both new balances. It is not yet
idempotent, so clients must not automatically retry an ambiguous response.

Full k3d and hybrid development use Envoy Gateway external authorization and a
Redis-backed global per-user limit for `POST /api/v1/market/orders`; see the
[k3d edge-auth runbook](../deploy/k3d/EDGE_AUTH.md). The BFF Redis still holds
OIDC session state, not market rate-limit state.
The [local Jenkins setup](../ci/jenkins/README.md) has separate Core, BFF,
Expedition, and frontend worktree and trusted-`main` builds, plus a
verified-artifact deploy job for each service. Builds and deployments are
manual; pushing to Git does not change the running k3d environment.
Normal Quarkus dev mode remains independent.

## Local development

The default development environment is the k3d cluster with Envoy Gateway,
Keycloak, PostgreSQL, Redis, RabbitMQ, and observability. Run any selected
service on WSL with Quarkus hot reload using one reversible command from
`../deploy/k3d`:

```bash
k3d cluster start hero-association
./hybrid.sh run core
./hybrid.sh run bff
./hybrid.sh run expedition
```

Each `run` command belongs in its own terminal. Use only the services you
are editing; the others remain in k3d. Press Ctrl-C to restore the matching
Deployment, Service route, and HPA. The browser stays at
`https://heroassociation.test`; Core remains private. The
[local development guide](../LOCAL_DEVELOPMENT.md) covers the first setup,
port allocations, secrets, interruption recovery, and Vite.

Core dev mode validates the shared k3d schema and does not seed or reset it.
The hybrid launcher disables Quarkus Dev Services for host-run backends and
uses the shared k3d collector instead of launching a separate LGTM container.
Only an explicit k3d Core database reset/reseed command should discard game
data. The old Compose/Traefik development stack is retired. Standalone
packaged JVM/native Compose runs remain optional and do not own ports 80/443.

The [k3d E2E isolation guide](../e2e/K3D_ISOLATION.md) documents the
verified disposable Gateway and full-stack browser checks, including the
required exact-image archive gate. The retired Compose archive format does not authorize deployment.

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
empty personal inventories. A Main Party is created immediately and holds all
three starter heroes, even before an agency exists. Creating an agency links
that Party to it; the heroes remain Manager-owned.
The starter Mage begins at Magic Level 1. In combat, its basic attack spends
20 mana and earns Magic progress when enough mana is available; at lower mana,
the attack remains free but earns no Magic progress.
A Manager with no membership can create one empty Level 1 agency as its leader;
invitations are a later task. Parties belong to individual Managers inside
their agency. A Manager can assign their own available heroes or available
agency-owned heroes to a prepared party. The agency leader sets each agency
hero's nonnegative per-quest borrowing fee (0 by default). Party assignment
is free; quest start submits the expected total and atomically transfers it
from the Manager's personal gold to the agency. Stale quotes and insufficient
funds reject the start without charging. Quest start applies agency recovery
before pinning Hero resources, levels, class combat values, spell state, and
equipped runes. New quest battles copy the versioned Creature definition
through a 60-second Redis read-through cache; PostgreSQL is authoritative,
and a cache error falls back to PostgreSQL. Troll has 2,000 health and Forest
Wolf has 120. Active snapshots retain these inputs if roster or definition
data changes. Compiled spell
formulas are not versioned yet; see the planned
[Combat and Expedition plan](../COMBAT_EXPEDITION_PLAN.md). The Map UI is available
in full k3d and hybrid mode with Expedition integration enabled, and reuses
the Phaser battle animations.
Any onboarded Manager can claim a globally available recruit into their personal
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
local development, use an explicit k3d realm reset or disposable E2E namespace;
restarting a Pod alone will not reimport the realm. Do not delete k3d data
merely to apply a code change.

## Local HTTPS gateway

Envoy Gateway owns both local HTTPS hostnames in full k3d and hybrid mode:
`heroassociation.test` and `auth.heroassociation.test`. Add only those two
hosts entries and trust the ignored `../tls/certs/local-ca.crt` in the
browser's operating system. See the
[local development guide](../LOCAL_DEVELOPMENT.md#requirements-and-first-setup)
for exact instructions and the Windows offline-CA curl caveat. Never commit
the CA or leaf private keys.

## Browser end-to-end tests

The repository-level [`../e2e`](../e2e) Playwright project verifies
registration, session continuity, agency actions, market limits, and
Map/Expedition through a disposable k3d namespace and Envoy Gateway. For a
build-once candidate, create the four-image archive in
[`../pipeline`](../pipeline/README.md), then from `hero-association/` run:

```bash
(cd e2e && npm ci)
./deploy/k3d/test-isolated-stack.sh pipeline/artifacts/<build-id>/all
```

This checks both disposable RabbitMQ role ACLs over AMQP, exact Pod image
IDs, and writes the required passing k3d E2E record only after test resources
are removed. Java 25 is required on the runner for the AMQP check. It does
not deploy the candidate.
The old Compose/Traefik E2E runner is retired. From `e2e/`, `npm test` now
runs the daily k3d smoke suite; use the disposable archive gate above for
candidate verification without touching daily accounts.

To run Core, BFF, and Expedition Maven tests against disposable k3d
PostgreSQL, Redis, and RabbitMQ instead of local Dev Services or broker
Testcontainers, run `./deploy/k3d/test-isolated-components.sh all` from
`hero-association/`. Use `core`, `bff`, or `expedition` instead of `all` for a
single service. Candidate builds now run the corresponding lane before
packaging; direct `./mvnw package` remains independently runnable.

See [`../e2e/README.md`](../e2e/README.md) for the ports, cleanup behavior,
current coverage, and the k6 check of the deployed k3d market-order limit.

## Containers

From this directory, run the complete JVM stack:

```bash
docker compose up --build
```

The standalone Compose stack publishes its own development ports and does
not include a browser-facing HTTPS gateway. Use k3d Envoy Gateway for the
integrated browser workflow. Both packaged JVM/native Compose stacks reset
and reseed their disposable Core database on every Core startup; do not use
them with data you need to keep.

The JVM Compose workflow publishes the BFF at `http://localhost:17080` and
Keycloak at `http://localhost:17180`; Core remains on the private Compose
network. The native Compose workflow has separate default ports—BFF `19080`,
Keycloak `19180`, BFF Redis `19679`, and Core cache Redis `19680`—so it can run alongside the
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
recreated from the archived Core image. A Core image containing new JPA tables,
including the private Assets tables, requires this reset in the pre-Flyway
lab before a validating Core Pod can start. Do not deploy that image over an
old Core schema without a reset or explicit schema update. Neither mode changes this host-run
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
PostgreSQL advisory locks to avoid overlapping across Pods. The recovery job
also restores stamina from elapsed time at the Training rate or current
agency Rest Level rate, capped at 48 hours. An isolated concurrency test
exercises two, four, and eight Core Pods, active combat and recovery,
concurrent database reads, and Pod restarts without duplicate quest
resolution. Separate CPU HPAs keep
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
