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
- Responsive layout for desktop and mobile screens

The game rules and the intended gameplay model live in
[`../GAME.md`](../GAME.md).
