# Hero Association frontend

The Hero Association frontend is a React application built with Vite. At
startup, it loads the seeded agency state from the BFF and refreshes it
every five seconds while the tab is visible. Rune loadouts, agency activity,
party preparation, quest starts, and the seeded combat encounter use backend
APIs.

## Requirements

- Node.js 24 LTS or later
- npm 11 or later

## Run locally

```bash
# Terminal 1: configure local Keycloak and start its database, Core's database,
# BFF's session database, and Keycloak
cd ../backend
cp .env.example .env
docker compose up --detach postgres-core postgres-keycloak postgres-bff keycloak

# Terminal 2: run Game Core on the host
cd hero-association-core
./mvnw quarkus:dev

# Terminal 3: run the BFF on the host
cd ../hero-association-bff
set -a
source ../.env
set +a
./mvnw quarkus:dev

# Terminal 4: start the frontend
cd ../frontend
npm install
npm run dev
```

Vite prints the local development URL, normally `http://localhost:5173`. Its
development server proxies `/api` and `/auth` requests to the BFF at
`http://localhost:8080` by default. Set `VITE_API_PROXY_TARGET` in a local
`.env` file to use a different BFF address; [`.env.example`](.env.example)
documents the variable. `VITE_API_PROXY_CHANGE_ORIGIN` defaults to `true`; set
it to `false` only when a controlled OIDC callback must return through the
browser-visible development proxy, as in the E2E workflow. Leave
`VITE_ALLOWED_HOSTS` unset unless a controlled environment needs Vite to serve
an additional host.

If the API cannot be reached, the frontend shows a visible notice and uses a
static local fixture. A production deployment sends `/api` requests to the
public BFF; the browser never calls Game Core directly.

Keycloak is available locally on `http://localhost:8180`. The frontend begins
at a sign-in screen, uses the BFF's `/auth/login` redirect, and receives no
Keycloak tokens in browser storage. The first signed-in visit provisions an
Account and requires a unique Manager name before the game opens. Agency
membership is required before agency data is loaded. A Manager without one sees
an explicit no-agency screen; agency creation and invitations are pending.

For local testing, use `user1@mail.com` / `user1` or
`user2@mail.com` / `user2`. Both have seeded agency memberships. Do not use
these credentials outside local development.

## Verify a production build

```bash
npm run build
```

## Browser end-to-end tests

The repository-level [`../e2e`](../e2e) Playwright project validates the
browser journey across the frontend, BFF, Keycloak, and Core. It uses a
separate Docker Compose stack and does not reuse local development databases
or ports:

```bash
cd ../e2e
npm --prefix ../frontend install
npm install
npm run test:auth
```

See [`../e2e/README.md`](../e2e/README.md) for the details.

## Current prototype

- Side navigation for Overview, Heroes, Quests, Agency, Market, and Feed
- Backend-loaded agency summary, roster, quest progress, upgrade levels, item
  and rune inventory, hero rune slots, agency feed posts, and market order
  book; the state refreshes every five seconds while the tab is visible
- Live market order book with buy and sell order creation and cancellation for
  the current agency
- Hero roster grouped into an active quest party, prepared parties, and
  unassigned heroes at the agency
- Prepared parties can be named and have available heroes added or removed
  through the backend; their members retain Training or Resting until a quest
  starts
- API-loaded available quests show party-size, duration, and gold-reward
  details. Selecting an eligible prepared party starts a quest and moves its
  heroes to `ON_QUEST`
- Agency heroes shown as Training or Resting; quest party members shown earning
  experience from creatures
- Training and Resting actions persist through the backend when it is available
- Stamina color and XP gain preview: 150% at 80% or more, 100% from 30% to
  79%, and 50% below 30%
- Five rune slots on every hero card and two displayed mage spell slots for
  Elara Moonweaver
- Five read-only rune slots beneath each hero in combat, populated from the
  party's API-loaded equipped loadouts
- API-loaded agency stackable materials and rune inventory. Runes have a
  persisted rune-slot drawer; item stacks can be reserved by market orders
- API-loaded agency feed with a text composer. The prototype can publish as
  the agency, its leader, or any of its heroes, with one optional agency-item
  reference that does not consume the displayed stack
- Agency hero cards show current health and mana. Training recovers both at
  the base class rate, while Resting uses 2× that rate; stamina recovery is
  still pending.
- Phaser-backed battlefield renderer inside the expanded active quest card
- The seeded active quest uses the backend combat snapshot. While expanded, it
  synchronizes the encounter every two seconds through the combat-sync API;
  Phaser does not calculate combat outcomes and replays only new server events
  as visual effects. A backend worker also advances active combat every five
  seconds when the view is closed
- Newly started quests are persisted but do not yet create a combat snapshot
- Health and mana bars for Level 1 heroes and placeholder creatures
- Server-calculated class recovery, mage spell mana costs and cooldowns, and
  critical-hit values reflected in the synchronized snapshot
- The initial party equips a Critical Chance Rune on every hero; placeholder
  trolls have a 10% critical chance
- Mage spell icons show server-provided cooldown state with radial
  right-to-left cooldown sweeps
- Responsive layout for desktop and mobile screens

The game rules and the intended gameplay model live in
[`../GAME.md`](../GAME.md).
