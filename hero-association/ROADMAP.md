# Hero Association Implementation Backlog

This backlog turns the current frontend prototype and agency-state endpoint
into an authoritative game. The backend must own game rules, resource changes,
and quest outcomes; the frontend should send player intent and render the
resulting state.

## Delivery rules

- Keep the database reset-and-seed workflow until Flyway is introduced.
- Use RFC 9562 UUIDv7 values as every persistent entity and API resource ID;
  PostgreSQL stores them using its native `uuid` type.
- Add an API contract test and deterministic seed coverage for every
  behavior-changing backend task.
- Add or update the frontend integration for every new player-facing endpoint.
- Update `SPEC.md`, `GAME.md`, and the backend README when a task changes
  supported behavior or local workflows.
- Follow [`AUTHENTICATION.md`](AUTHENTICATION.md) for account, Keycloak, BFF,
  and Game Core boundary work.
- Follow [`DEPLOYMENT.md`](DEPLOYMENT.md) for the local Traefik migration,
  Envoy Gateway and Istio in k3d, and BFF/Core scaling progress.

## Milestone 1 — Authoritative agency management

- [x] Define the Account, Manager, and AgencyMember identity model. See
  [`AUTHENTICATION.md`](AUTHENTICATION.md).
- [x] Add agency creation and retrieval endpoints. An onboarded Manager without
  a membership can create one empty Level 1 agency as its `LEADER`; its name is
  unique case-insensitively. Invitations, leaving, and ownership transfer are
  still separate work.
- [x] Add hero recruiting and hero detail endpoints. The initial global board offers three free Level 1 NPCs; each can be claimed once.
- [x] Add a hero activity command for `TRAINING` and `RESTING`. Prevent a hero
  on a quest from changing agency activity.
- [x] Add party creation and membership commands, and include party details in
  agency state. Allow a party to contain one or more heroes.
- [x] Replace frontend-only party-preparation actions with these APIs.

## Milestone 2 — Inventory and rune loadouts

- [x] Model agency storage for gold, runes, and stackable item materials.
  Market reservations and transfers are implemented; quest loot remains to be
  added.
- [x] Add commands to equip and unequip a rune, atomically moving it between
  agency inventory and a hero's five rune slots.
- [x] Lock a hero's rune loadout while the hero is on a quest.
- [x] Validate that a rune belongs to the agency and that a target slot exists.
  Rune-class compatibility is deliberately out of scope for this milestone.
- [x] Update the agency-state response and frontend rune drawer to use the
  persisted loadout commands.

## Milestone 3 — Quest lifecycle

- [x] Add initial quest definitions with an objective description, creature
  group, party-size range, duration estimate, and gold reward. Difficulty and
  recommended classes remain to be defined.
- [x] Add a command to start an available quest with a prepared party. Validate
  party membership, hero availability, and party-size eligibility.
- [x] Persist quest status and timestamps. The seeded combat terminal states
  persist `COMPLETED` or `FAILED`, set `finishedAt`, release the party, and
  return its heroes to Training. Cancellation remains unimplemented.
- [x] Add a command or scheduled process that advances an in-progress quest.
  The seeded combat snapshot advances through an explicit sync command and a
  five-second background worker; terminal combat states now resolve the quest.
- [ ] Define and implement quest cancellation rules and a cancellation command.
- [x] Replace the frontend's fixed active quest and available quest cards with
  API data and persisted quest starts.

## Milestone 4 — Server-side automatic combat

- [x] Define the initial basic-attack, target-selection, attack-timer,
  critical-hit, recovery, and mage-spell formulas. Armor and attack-speed
  formulas remain to be defined.
- [x] Implement a deterministic combat engine with supplied time and random
  values so it can be tested reliably.
- [x] Resolve individual hero and creature timers, health, mana, deaths, and
  the initial mage spells in the server-side rules engine.
- [x] Apply Critical Chance and Critical Damage Rune effects when a new combat
  snapshot is created.
- [ ] Apply the remaining displayed rune effects: attack, armor, health, mana,
  and attack speed.
- [x] Persist a compact quest-combat snapshot and bounded event history. The
  initial Troll encounter stores its current snapshot plus the latest 100
  server-generated events, and every newly started quest creates a snapshot
  from its party and creature objective.
- [x] Replace the local Phaser combat simulation with server state. The Phaser
  view renders the synchronized snapshot.
- [x] Render new server combat events in Phaser, including attacks, spells,
  recovery, critical hits, and defeats. The scene does not replay history that
  happened before it was opened.

## Milestone 5 — Combat progression and recovery

Track this ordered Core work here. Check a step only when its code, API contract
tests, and deterministic seed coverage are complete. The rules and formulas are
in [`PROGRESSION.md`](PROGRESSION.md). Gold, item loot, and payout settlement
stay deferred until domain separation; combat progression does not need them.

Already available:

- [x] Persist and synchronize combat snapshots, including attacks, mana spent,
  and defeated-target hits.
- [x] Recover agency hero health and mana at the class base rate in Training
  and twice that rate in Resting.

Decisions to settle before the affected step, not before starting Step 1:

- [x] Initial Troll base XP is 100; only living party heroes receive XP
  from each kill. Calculate XP separately from the full creature base; never
  split by party size or damage dealt.
- [ ] Choose a practice-action cadence and Magic mana cost; confirm Shield
  aptitude rates and a block rule before Shield combat progression.
- [ ] Choose health, mana, and stamina on return after defeat, and whether a
  level-up refills current resources, before Step 4.

Ordered implementation:

1. [x] Persistence and pure rules: replace percentage stamina with up to
   48 hours of precise time; store cumulative hero XP and fractional Melee,
   Distance, Magic, and Shield progress. Start all four skills at Level 10,
   replacing the current Magic Level 0 seed. Derive levels and max resources
   from the documented formulas. Reset deterministic local data; no Flyway
   migration or compatibility layer is needed at this stage.
2. [ ] Combat progression: advance each living hero stamina by actual active
   battle time (one minute per minute), stopping at the real terminal event,
   not the later sync target. Apply Melee/Distance progress per attack and
   Magic progress for mana spent; add Shield combat points after a block
   rule exists. Once per defeat, calculate XP separately for each eligible
   party hero from the full creature base using individual stamina at the
   kill timestamp. Never split XP by party size or damage dealt. Consume
   the complete new event stream inside the locked combat transaction,
   before truncating the latest-100 UI history. A retry or competing Pod
   must not award or drain twice.

   Progress:

   - [x] Time-based drain for living heroes, including no-hit intervals and
     terminal mid-sync cases; repeated syncs do not drain twice.
   - [x] Melee/Distance attack and mana-spent Magic points, including the
     below-15-hour penalty; process all events before the 100-event UI cap.
   - [x] Per-creature XP awards from the 100-XP provisional creature base,
     for each living hero only; repeated syncs do not award twice.
   - [ ] Shield combat points after a block rule is defined.

3. [ ] Agency recovery and practice: recover one stamina minute per real
   minute in Training or two at Rest Level 1, plus 10% of the Level 1 Rest
   rate per additional level. Add selected-skill practice at 2x at Training
   Level 1, plus 5% of that baseline per later level. Magic practice spends
   real mana; below 15 hours, skill progress is halved even in Training.
   Test higher levels with fixtures; player upgrade commands remain Milestone 6.
4. [ ] Event rates and defeat: add Core-owned, shared XP and skill rates
   defaulting to `1x`, with scheduled overrides evaluated at the action or
   kill timestamp. Above 40 hours, add 50 percentage points to hero XP only;
   below 15 hours, multiply the active XP and skill rates by 0.5. Apply the
   level-scaled, once-per-defeat XP/skill loss and chosen return resources.
5. [ ] API and frontend: expose stamina as time, hero XP/level, all skill
   levels/progress, and agency Rest/Training levels. Replace the current
   80%/30% color bands with >40h and <15h; clearly mark or hide advertised
   quest gold until economic payouts exist.
6. [ ] Verify: unit-test boundaries (exactly 40h and 15h), `1x`/`2x`
   stacking, fractional points, class/agency rates, level-up and defeat.
   Integration-test no-kill battles, multi-kill and terminal mid-sync cases,
   delayed/repeated syncs, agency activity changes, and two Core replicas.
   Add an API/UI flow that shows progression without granting gold or items.

## Milestone 6 — Agency progression

- [ ] Implement agency gold balance, Agency Level, and specialized upgrades.
- [ ] Enforce the Agency Level cap on specialized upgrades.
- [ ] Implement and test exponential upgrade costs; the exact cost curve needs
  balancing before it can be finalized.
- [ ] Apply the concrete effects of Training, Rest, Size, Reputation, and
  Intelligence levels.

## Milestone 7 — Market

- [x] Define the initial tradable category (stackable materials) and reserve
  items or gold while market orders remain open.
- [x] Add buy and sell order creation, cancellation, listing, and price-time
  matching.
- [x] Apply the 10% market fee atomically when an order matches.
- [x] Update the frontend market screen to use the live order book.
- [x] Limit buy/sell order placement to 5 requests per second per authenticated
  user across sessions and Envoy proxy replicas in k3d; see
  [ADR 0005](adr/0005-k3d-market-order-edge-auth.md).
- [x] Verify the deployed limit with a k6 burst and sustained authenticated
  traffic through the k3d gateway.
- [x] Move the public market contract to `GET/POST /api/v1/market/orders`
  and `DELETE /api/v1/market/orders/{orderId}`; market mutations return order
  state, while clients refresh agency state separately. See
  [ADR 0003](adr/0003-market-service-boundary.md).
- [ ] Extract Market as an independently buildable and deployable service with
  its own order data store. Keep Core authoritative for agency membership,
  gold, and inventory; define idempotent reservation/settlement and failure
  recovery before moving matching out of Core. Route `/api/v1/market/**` from
  BFF to Market while retaining the k3d Envoy per-user rate limit. Follow the
  [Market service extraction plan](MARKET_ARCHITECTURE.md).
- [ ] Add market history.

## Milestone 8 — Social feed and multiplayer

- [x] Add agency-scoped text feed posts by agencies, the current leader, and
  heroes, including one non-consuming reference to an agency item stack.
- [ ] Add agency invitations, membership permissions, manager departure, and
  starting a new agency.
- [ ] Define feed visibility and moderation rules before exposing posts beyond
  an agency.

## Milestone 9 — Authentication and real-time updates

- [x] Define the Keycloak and Identity BFF architecture, including its private
  Game Core boundary. See [`AUTHENTICATION.md`](AUTHENTICATION.md).
- [x] Rename the current backend as `hero-association-core` and create an
  independently buildable `hero-association-bff` Quarkus service. Update
  Docker, Compose, documentation, and local development commands so the
  frontend calls the BFF rather than Game Core.
- [x] Add local Keycloak Compose support, the versioned Hero Association realm,
  and non-secret configuration templates.
- [x] Add BFF session, login, logout, callback, server-side token storage, and
  CSRF protection. The frontend calls only the BFF and game proxy routes now
  require a BFF session.
- [x] Move BFF OIDC token state from PostgreSQL to Redis.
  - [x] Replace Quarkus's database token-state manager with its Redis
    token-state manager and retain encrypted, server-side Keycloak tokens.
  - [x] Add Redis to `backend/compose.infra.yaml` so
    `backend/scripts/start-infra.sh` starts it with the other local
    infrastructure; configure a dedicated local host port for host-run BFF
    development.
  - [x] Add Redis to the JVM, native, and E2E Compose workflows as needed, with
    isolated test state and no impact on local development data.
  - [x] Replace BFF PostgreSQL connection configuration, dependencies, tests, and
    documentation with Redis configuration; then remove `postgres-bff` and its
    BFF-only environment variables.
- [x] Add Redis session coverage for token refresh, session expiration, and
  multiple BFF instances. The isolated browser suite verifies refresh without a
  browser redirect, expiration fails closed, and a second BFF reads the session.
- [x] Forward the BFF-held Keycloak access token to Game Core and require a
  valid `hero-association-core` bearer-token audience for all Core API routes.
- [x] Add Account provisioning and Manager onboarding from the Keycloak
  subject; do not use email as the account key.
- [x] Add `AgencyMember` roles and enforce membership and leader permissions
  on agency reads and commands. Market-order creation and cancellation require
  `LEADER`.
- [x] Use five-second browser polling for initial agency, quest, feed, and
  market refreshes while the tab is visible. Revisit real-time transport when
  higher-frequency updates need it.
- [x] Add browser end-to-end coverage with Playwright.
  - [x] Provide an isolated Compose stack with separate ports, databases, and
    project name, so E2E runs never affect local development services.
  - [x] Validate native Keycloak login, BFF session creation, RP-initiated
    logout, the state-validated post-logout return, and that the next login
    requires credentials again.
  - [x] Register a new user through Keycloak, sign out, sign in again, and
    confirm both sessions map to the same Core Account and Keycloak subject.
  - [x] Add browser coverage for onboarding, no-agency access, agency creation,
    and membership permissions. The isolated suite provisions `user3`, verifies
    its no-agency gate and first agency, and confirms User 2 cannot read
    Dawnwatch state.
- [ ] Add WebSocket or server-sent event updates only for features that need
  near-real-time changes, such as combat progress, matched market orders, and
  new feed posts.

## Milestone 10 — Delivery topology

- [x] Add identity-aware market limiting at the k3d Envoy Gateway. The BFF
  validates the opaque session for Envoy but applies no market rate limit.
  Normal local Traefik development has no market limit. Auth and k6
  regressions pass. See
  [`deploy/k3d/EDGE_AUTH.md`](deploy/k3d/EDGE_AUTH.md).
- [x] Test gateway Redis failure and two Envoy proxy replicas in k3d. Redis
  outage makes Envoy fail closed with HTTP 500 without forwarding the order.
  The two proxies share the gateway limit. See the
  [resilience runbook](deploy/k3d/EDGE_AUTH.md).
- [x] Add three Sentinel-managed gateway Redis Pods in k3d, on separate nodes,
  and test primary failover, complete fail-closed outage, and shared limits
  across two Envoy proxies. See [ADR 0006](adr/0006-k3d-gateway-redis-sentinel.md).
- [ ] Before adopting the gateway policy outside this lab, provide durable,
  authenticated, TLS-protected Redis with multi-fault-domain availability,
  monitoring/alerts, and a reviewed fail-closed outage plan.

- [x] Add an initial local Caddy edge stack with HTTPS, a packaged frontend,
  same-origin BFF routing, and a separate Keycloak hostname.
- [x] Finish the local Caddy-to-Traefik migration. The Windows current-user
  store trusts the development CA, the isolated local E2E suite passes, and
  obsolete Caddy files are removed. See [`DEPLOYMENT.md`](DEPLOYMENT.md).
- [x] Bootstrap the isolated k3d cluster with Istio and an Envoy Gateway
  HTTPS edge; application deployment and scaling remain separate tasks.
- [x] Install a disposable k3d observability stack with OpenTelemetry,
  Prometheus, Loki, Tempo, and Grafana. App instrumentation and dashboards
  remain in [`DEPLOYMENT.md`](DEPLOYMENT.md).
- [x] Test independent BFF and Game Core autoscaling from 2 to 8 Pods in k3d,
  after making shared database initialization and Core jobs safe across Pods.

## Milestone 11 — Build once, deploy to local targets

- [x] Add a local build stage with separate Core, BFF, and frontend lanes and
  an all-in-one run. Run backend tests and frontend lint/build, create
  version-tagged images, and export a checksummed archive with source and
  image IDs. See
  [`pipeline/README.md`](pipeline/README.md).
- [x] Make the isolated browser E2E lane consume the exact archived images,
  without rebuilding them, before an artifact is eligible for deployment.
  It records passing evidence beside the verified archive.
- [x] Import and deploy the verified archive to k3d without rebuilding it;
  keep the current local development environment and Core data untouched.
- [x] Add one local command that runs build, archived-image E2E verification,
  and k3d promotion in order, stopping at the first failed gate.
- [ ] Run that command automatically for trusted `main` commits with a local
  Docker-hosted Jenkins controller and WSL build agent. The controller and
  agent connected on 2026-09-29; the first complete Jenkins build awaits a
  commit containing its Jenkinsfile on GitHub `main`. Do not run unreviewed
  pull requests on the Docker/k3d-capable agent. See
  [the local Jenkins runbook](ci/jenkins/README.md).
- [x] Include the containerized market k6 rate-limit test in the k3d
  promotion gate after browser E2E. A failure restores previous application
  image references; the isolated lab passed both suites on 2026-09-28.
- [x] Verify the actual Core, BFF, and frontend Pod image IDs against the
  archived OCI platform manifests during k3d promotion, not only the tags.
- [x] Add a read-only audit for an already-running k3d deployment against an
  E2E-verified archive, without importing images or changing Pods.
- [x] Reject an unverified archive before k3d promotion loads any images into
  local Docker or imports them into the cluster.
- [x] Save a local k3d promotion result only after Pod-image, browser, and
  market k6 checks pass; retain a separate read-only live audit.
- [x] Run rollback regression tests before the k3d pipeline builds images;
  verify restored Deployment references and never overwrite concurrent changes.
- [x] Rehearse a failed k3d browser gate after rolling out a different verified
  archive; confirm rollback restores all application Pod digests and the normal
  browser and market k6 suites still pass.
- [x] Store the same E2E-verified image archive and evidence in the isolated
  Floci S3 lab, verifying uploaded bytes without rebuilding images.
- [x] Download the Floci-stored archive to a new location and verify its
  checksum, image identities, and E2E gate before deployment.
- [x] Launch and verify the archived frontend image as one real Floci ECS EC2
  task using an explicit Docker-socket override and no published host port.
- [x] Provision independent private PostgreSQL 18.6 RDS instances for Core
  and Keycloak in the Floci lab. Verify SQL connectivity to each from its
  Docker network without publishing database host ports.
- [x] Provision a separate single-node, Redis-compatible Floci ElastiCache
  group for BFF sessions. Verify actual cache reads and writes from the lab
  Docker network without publishing a host port.
- [ ] Deploy Core, BFF, and frontend as a functional application in the
  separate Floci/AWS lab, with independent Keycloak, database, Redis, and
  ingress at the reserved AWS-lab hostnames. Defer real AWS and production
  rollout.

## Milestone 12 — Personal progression and shared agencies

Implement and validate these ownership rules inside Game Core first. Core is
temporary; separate game domains after their rules and cross-domain contracts
are established. See [ADR 0004](adr/0004-core-as-temporary-modular-monolith.md).

- [x] Add manager1 through manager10 as deterministic local test identities and
  distribute them across three multi-Manager agencies. See [TEST_DATA.md](TEST_DATA.md).
- [ ] Give each Manager a personal hero roster, gold wallet, item inventory,
  and rune inventory that survive agency changes. Keep agency assets separate.
- [ ] Start every new Manager with no gold, items, or runes and one personal
  Level 1 Warrior, Mage, and Archer, with starting skills at Level 10 per
  `PROGRESSION.md`. Keep provisioning idempotent.
- [ ] Make personal recruitment the default; allow an agency-owned recruit
  only when the Manager has agency recruitment permission and selects it.
- [ ] Give each party a Manager owner; allow only that Manager's heroes and
  available borrowed agency heroes, without transferring hero ownership.
- [ ] Define and implement the agency-hero borrowing fee and charge timing.
- [ ] Support Manager-owned and agency-owned market orders, reserving from the
  correct wallet/inventory and enforcing agency trading permissions. Revise
  the current agency-only market request and extraction plan before splitting
  the Market service.

## Deferred until domain separation — Economic quest rewards

- [ ] Define the reward-owning domain and its reliable creature-defeat and
  quest-completion contracts before enabling economic payouts.
- [ ] Roll each defeated creature gold and item entries once at
  `min(100%, baseChance * dropRate) * lowStaminaLootFactor`, preserve
  amount ranges, and respect party Capacity. Halve final drop chance below
  15 hours of stamina. Decide the factor for mixed-stamina parties and add
  the authoritative loot-chance event rate there.
- [ ] Transfer quest items to the party Manager and split gold between the
  Manager and agency at the applicable share. Define covered inflows and when
  a changed share takes effect.

## Post-MVP

- [ ] Add Google sign-in through Keycloak. Keep native email/password sign-in
  for the MVP and preserve existing account links when social sign-in arrives.

## Open decisions before their respective implementations

These do not all block the current combat-progression slice.

- [ ] Decide the hero's health, mana, and stamina on return after PvE defeat.
- [ ] Balance creature XP, participation, training cadence, and skill costs
  after gameplay tests.
- [ ] Define creature loot tables, gold/item amounts, and party Capacity
  behavior; test chance caps and precision for ultra-rare drops.
- [ ] Define quest duration, difficulty, failure, and cancellation rules.
- [ ] Define armor and attack-speed formulas, plus initial persistent creature
  attributes.
- [ ] Define agency-share treatment of market proceeds, transfers, and refunds,
  and how rate changes affect already-started quests and open orders.
- [ ] Define the agency-hero borrowing fee and its charge/refund timing.
- [ ] Define additional tradable item categories.
- [ ] Define agency invitation, ownership transfer, and permission rules.
