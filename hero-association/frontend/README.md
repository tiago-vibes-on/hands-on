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
- Five empty item slots and two empty spell slots on every hero card
- Resting activity explains the future 2× stamina, health, and mana recovery
- Phaser-backed automatic combat inside the expanded active quest card
- Health and mana bars for Level 1 heroes and placeholder creatures
- Empty spell slots for heroes and creatures, ready for later spell behavior
- Responsive layout for desktop and mobile screens

The game rules and the intended gameplay model live in
[`../GAME.md`](../GAME.md).
