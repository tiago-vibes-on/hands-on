# Hero Association frontend

The Hero Association frontend is a React application built with Vite. It is
currently a navigable game prototype that uses local mock data; it does not yet
call the Quarkus API.

## Requirements

- Node.js 24 LTS or later
- npm 11 or later

## Run locally

```bash
npm install
npm run dev
```

Vite prints the local development URL, normally `http://localhost:5173`.

## Verify a production build

```bash
npm run build
```

## Current prototype

- Side navigation for Overview, Heroes, Quests, Agency, Market, and Feed
- Mock agency summary, roster, quest progress, agency upgrades, market offers,
  and social feed
- Hero roster grouped into a named active quest party and heroes at the agency
- Agency heroes shown as Training or Resting; party members shown earning
  experience from creatures
- Stamina color and XP gain preview: 150% at 80% or more, 100% from 30% to
  79%, and 50% below 30%
- Five rune slots on every hero card and two displayed mage spell slots for
  Elara Moonweaver
- Five read-only rune slots beneath each hero in combat, populated from the
  party's mock equipped loadouts
- Mock agency rune inventory and rune-slot drawer, with compatibility
  intentionally ignored for now
- Resting activity explains the future 2× stamina, health, and mana recovery
- Phaser-backed automatic combat inside the expanded active quest card
- Health and mana bars for Level 1 heroes and placeholder creatures
- Class-based health and mana recovery every second in combat: Warrior 10/2,
  Mage 2/10, and Archer 6/6
- Three-lane floating damage indicators: gold for basic damage and purple for
  magic damage
- Critical runes: Critical Chance Rune (+1% chance) and Critical Damage Rune
  (+10 percentage points to the critical multiplier); they apply when a new
  mock encounter starts and trigger the target shake and highlighted popup
- The initial party equips a Critical Chance Rune on every hero; placeholder
  trolls have a 10% critical chance
- Automatic mage spells in combat: Fire Ball against one target and Lightning
  Rail against all living targets, with mana costs, cooldowns, and radial
  right-to-left cooldown sweeps on each spell icon
- Responsive layout for desktop and mobile screens

The game rules and the intended gameplay model live in
[`../GAME.md`](../GAME.md).
