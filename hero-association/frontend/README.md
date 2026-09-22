# Hero Association frontend

The Hero Association frontend is a React application built with Vite. At
startup, it loads the seeded agency state from the Quarkus API and refreshes it
every five seconds while the tab is visible. Rune loadouts, agency activity,
party preparation, quest starts, and the seeded combat encounter use backend
APIs.

## Requirements

- Node.js 24 LTS or later
- npm 11 or later

## Run locally

```bash
# Terminal 1: start the local PostgreSQL database
cd ../backend
docker compose up --detach postgres

# Terminal 2: run Quarkus on the host
./mvnw quarkus:dev

# Terminal 3: start the frontend
cd ../frontend
npm install
npm run dev
```

Vite prints the local development URL, normally `http://localhost:5173`. Its
development server proxies `/api` requests to `http://localhost:8080` by
default. Set `VITE_API_PROXY_TARGET` in a local `.env` file to use a different
API address; [`.env.example`](.env.example) documents the variable.

If the API cannot be reached, the frontend shows a visible notice and uses a
static local fixture. A production deployment needs an `/api` reverse proxy or
gateway serving the Quarkus API on the same origin.

## Verify a production build

```bash
npm run build
```

## Current prototype

- Side navigation for Overview, Heroes, Quests, Agency, Market, and Feed
- Backend-loaded agency summary, roster, quest progress, upgrade levels, item
  and rune inventory, hero rune slots, and agency feed posts; the state
  refreshes every five seconds while the tab is visible
- Local prototype market offers
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
  persisted rune-slot drawer; item stacks are currently read-only
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
