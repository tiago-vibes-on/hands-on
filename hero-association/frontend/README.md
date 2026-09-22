# Hero Association frontend

The Hero Association frontend is a React application built with Vite. At
startup, it loads the seeded agency state from the Quarkus API. Combat and
rune-loadout changes remain local prototypes until their write APIs exist.

## Requirements

- Node.js 24 LTS or later
- npm 11 or later

## Run locally

```bash
# Terminal 1: start PostgreSQL and the Quarkus API
cd ../backend
./mvnw quarkus:dev

# Terminal 2: start the frontend
cd ../frontend
npm install
npm run dev
```

Vite prints the local development URL, normally `http://localhost:5173`. Its
development server proxies `/api` requests to `http://localhost:8080` by
default. Set `VITE_API_PROXY_TARGET` in a local `.env` file to use a different
API address; [`.env.example`](.env.example) documents the variable.

If the API cannot be reached, the frontend shows a visible notice and uses a
local fixture so that the combat prototype remains explorable. A production
deployment needs an `/api` reverse proxy or gateway serving the Quarkus API on
the same origin.

## Verify a production build

```bash
npm run build
```

## Current prototype

- Side navigation for Overview, Heroes, Quests, Agency, Market, and Feed
- Backend-loaded agency summary, roster, quest progress, upgrade levels, rune
  inventory, and hero rune slots
- Local prototype market offers and social feed
- Hero roster grouped into an active quest party, prepared parties, and
  unassigned heroes at the agency
- Prepared parties can be named and have available heroes added or removed
  through the backend; their members retain Training or Resting until a quest
  starts
- Agency heroes shown as Training or Resting; quest party members shown earning
  experience from creatures
- Training and Resting actions persist through the backend when it is available
- Stamina color and XP gain preview: 150% at 80% or more, 100% from 30% to
  79%, and 50% below 30%
- Five rune slots on every hero card and two displayed mage spell slots for
  Elara Moonweaver
- Five read-only rune slots beneath each hero in combat, populated from the
  party's API-loaded equipped loadouts
- API-loaded agency rune inventory and a persisted rune-slot drawer;
  compatibility rules are intentionally not implemented yet
- Resting activity explains the future 2× stamina, health, and mana recovery
- Phaser-backed automatic combat inside the expanded active quest card
- Health and mana bars for Level 1 heroes and placeholder creatures
- Class-based health and mana recovery every second in combat: Warrior 10/2,
  Mage 2/10, and Archer 6/6
- Three-lane floating damage indicators: gold for basic damage and purple for
  magic damage
- Critical runes: Critical Chance Rune (+1% chance) and Critical Damage Rune
  (+10 percentage points to the critical multiplier); local combat applies
  them when a new encounter starts and triggers the target shake and
  highlighted popup
- The initial party equips a Critical Chance Rune on every hero; placeholder
  trolls have a 10% critical chance
- Automatic mage spells in combat: Fire Ball against one target and Lightning
  Rail against all living targets, with mana costs, cooldowns, and radial
  right-to-left cooldown sweeps on each spell icon
- Responsive layout for desktop and mobile screens

The game rules and the intended gameplay model live in
[`../GAME.md`](../GAME.md).
