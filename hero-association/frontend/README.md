# Hero Association frontend

The Hero Association frontend is a React application built with Vite. At
startup, it loads the seeded agency state and recruitment board from the BFF
and refreshes them every five seconds while the tab is visible. Recruitment,
rune loadouts, agency activity, party preparation, quest starts, and the seeded
combat encounter use backend APIs.

## Requirements

- Node.js 24 LTS or later
- npm 11 or later

## Run locally

Run these in four terminals, each starting from `hero-association/`. Configure
`backend/.env` from its template once; keep any existing local secrets.

```bash
# Terminal 1: local infrastructure
cd backend
./scripts/start-infra.sh

# Terminal 2: Game Core
cd backend/hero-association-core
./mvnw quarkus:dev

# Terminal 3: BFF (loads backend/.env and validates its secrets)
cd backend
./scripts/run-bff-dev.sh

# Terminal 4: frontend
cd frontend
npm ci
npm run dev
```

Vite listens at `http://localhost:15172`, but use
`https://heroassociation.test` in the browser. The Traefik container from the
infrastructure stack proxies that secure origin to Vite and to the BFF, and
proxies Keycloak at `https://auth.heroassociation.test`. Vite binds its local
development server to the WSL network so Traefik can reach it; its allowed-host
list still limits browser requests to `heroassociation.test` by default. Set
`VITE_API_PROXY_TARGET` in a local `.env` file to use a different BFF address;
[`.env.example`](.env.example) documents the variable. `VITE_API_PROXY_CHANGE_ORIGIN`
defaults to `true`; set it to `false` only when a controlled OIDC callback must
return through the browser-visible development proxy, as in the E2E workflow.
Leave `VITE_ALLOWED_HOSTS` unset unless a controlled environment needs Vite to
serve an additional host.

If the API cannot be reached, the frontend shows a visible notice and uses a
static local fixture. A production deployment sends `/api` requests to the
public BFF; the browser never calls Game Core directly.

The local HTTPS setup, hosts-file entries, and one-time Traefik certificate trust
steps are in [`../backend/README.md`](../backend/README.md#local-https-gateway).

Keycloak is available locally on `http://localhost:17180`. The frontend begins
at a sign-in screen, uses the BFF's `/auth/login` redirect, and receives no
Keycloak tokens in browser storage. The first signed-in visit provisions an
Account and requires a unique Manager name before the game opens. A Manager
without a membership sees an agency-creation form. The created Level 1 agency
starts empty and can claim available heroes from the recruitment board.

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

## Deploy to the isolated k3d lab

The k3d frontend is a production-built Nginx image, not the Vite dev server.
Build and deploy it after the k3d backend from `../deploy/k3d`:

```bash
./build-frontend-image.sh
./deploy-frontend.sh
```

Open `https://k3d.heroassociation.test`. Envoy Gateway serves the UI
and routes `/api` and `/auth` to BFF on the same origin. The k3d Playwright
suite runs with `npm run test:k3d` from `../e2e` without resetting lab data.
See [`../deploy/k3d/README.md`](../deploy/k3d/README.md#build-and-deploy-the-frontend)
for cluster setup, local CA trust, and image-restart instructions.

## Current prototype

- Side navigation for Overview, Heroes, Quests, Agency, Market, and Feed
- Backend-loaded agency summary, roster, quest progress, upgrade levels, item
  and rune inventory, hero rune slots, agency feed posts, and market order
  book; the state refreshes every five seconds while the tab is visible
- Live market order book with buy and sell order creation and cancellation for
  the current agency
- The Heroes screen loads the global recruitment board. An agency member can
  claim a free Level 1 NPC once; the claimed hero immediately joins the agency
  roster in Training.
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
- The current stamina color and XP preview still use prototype percentage
  thresholds: 150% at 80% or more, 100% from 30% to 79%, and 50% below
  30%. The planned 48-hour model and its different thresholds are described
  in [`GAME.md`](../GAME.md); the UI has not been updated yet.
- Five rune slots on every hero card and two displayed mage spell slots for
  Elara Moonweaver
- Five read-only rune slots beneath each hero in combat, populated from the
  party's API-loaded equipped loadouts
- API-loaded agency stackable materials and rune inventory. Runes have a
  persisted rune-slot drawer; item stacks can be reserved by market orders
- Rune slots on quest heroes are read-only; their loadouts are locked until the
  quest is resolved.
- API-loaded agency feed with a text composer. The prototype can publish as
  the agency, its leader, or any of its heroes, with one optional agency-item
  reference that does not consume the displayed stack
- Agency hero cards show current health and mana. Training recovers both at
  the base class rate, while Resting uses 2× that rate; stamina recovery is
  still pending.
- Phaser-backed battlefield renderer inside every expanded active quest card
- Starting a quest creates a backend combat snapshot from its party's current
  resources, class combat values, and equipped Critical Chance and Critical
  Damage Rune effects, plus one provisional creature per required objective.
  The provisional creature profile uses 120 health, 10 damage, a
  1.6-second attack interval, 100 mana, no recovery, and no critical chance.
  While expanded, the frontend synchronizes the encounter every two seconds
  through the combat-sync API; Phaser does not calculate combat outcomes and
  replays only new server events as visual effects. A backend worker also
  advances active combat every five seconds when the view is closed. Terminal
  combat states become completed or failed quest cards and return the party's
  heroes to Training; rewards and failure consequences remain pending.
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
