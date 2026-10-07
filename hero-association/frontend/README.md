# Hero Association frontend

The Hero Association frontend is a React application built with Vite. At
startup, it loads the seeded agency state and recruitment board from the BFF
and refreshes them every five seconds while the tab is visible. Recruitment,
rune loadouts, agency activity, party preparation, quest starts, and the seeded
combat encounter use backend APIs. The market places personal orders by
default; an agency leader can select agency trading instead. Each order
reserves gold or items from the selected owner. The open book identifies
owners, and personal account and agency balances refresh after mutations and
while the tab is visible.

Market placement IDs survive uncertain responses and same-tab reloads. A
pending response stays visible while the browser polls the initiating player's
placement status. Cancelling freezes the order and remains pending until
allocated trades settle and unfilled resources are returned. Gold transfers
also retain their operation keys until the result and balance refresh are
confirmed, preventing another debit on an exact retry.

## Requirements

- Node.js 24 LTS or later
- npm 11 or later

## Run locally

The default local environment is the k3d cluster with Envoy Gateway and its
shared infrastructure. From `hero-association/deploy/k3d`, run:

```bash
k3d cluster start hero-association
./hybrid.sh run frontend
```

The hybrid command starts Vite on WSL port `15172`, stops only the k3d
frontend Pod, and routes `https://heroassociation.test:8443` through the
unchanged Envoy Gateway. Vite HMR connects to the same HTTPS origin over
WebSocket. Press Ctrl-C to restore the frontend Pod. Core, BFF, Expedition,
and Keycloak can remain in k3d or be switched separately using
[`hybrid.sh`](../LOCAL_DEVELOPMENT.md#switch-a-service-to-wsl-hot-reload).
The ignored k3d Secrets are passed to host-run backend processes at runtime.
Run `npm ci` once if `node_modules` is absent.

The Map page is enabled in the k3d image and the hybrid Vite command. It
loads the Manager's active Expedition, offers Troll Field to prepared
personal-hero parties, sends UUIDv7 Start/Continue/Return commands with the
existing CSRF token, and receives fight snapshots over a same-origin BFF
WebSocket. It reconnects from the current server snapshot. On returning to
Map or resuming a hidden tab, queued hit animations are dropped and the
current server frame is painted immediately; only subsequent live events
animate. The browser never calculates fight results or receives a Keycloak
token. The standalone Dockerfile still defaults Map off unless
`VITE_EXPEDITION_ENABLED=true` is passed at build time.

If the API cannot be reached, the frontend shows a visible notice and uses a
static local fixture. A production deployment sends `/api` requests to the
public BFF; the browser never calls Game Core directly.

The hosts entries and one-time certificate trust steps are in the
[local development guide](../LOCAL_DEVELOPMENT.md#requirements-and-first-setup).

Keycloak is available through `https://auth.heroassociation.test:8443`. The frontend begins
at a sign-in screen, uses the BFF's `/auth/login` redirect, and receives no
Keycloak tokens in browser storage. The first signed-in visit provisions an
Account and requires a unique Manager name before the game opens. A Manager
without a membership sees an agency-creation form. Manager onboarding creates
a Main Party with three personal starter heroes; creating an agency attaches
that Party. The Level 1 agency starts without agency-owned heroes. The Manager
can claim available NPCs into their personal roster.

For local testing, use `user1@mail.com` / `user1` or
`user2@mail.com` / `user2`. Both have seeded agency memberships. Do not use
these credentials outside local development.

## Verify the frontend

```bash
npm test
npm run lint
npm run build
```

## Browser end-to-end tests

Use the shared k3d Envoy route for current browser, Map, and market-policy
checks. From `../e2e`:

```bash
npm ci
npm run test:k3d
npm run test:k3d:map
npm run test:market:k6
```

The Compose/Traefik browser and archive lanes were retired. The
[isolated k3d E2E runbook](../e2e/README.md) describes candidate verification;
the commands above are smoke checks against the running development data.

## Deploy to the isolated k3d lab

The k3d frontend is a production-built Nginx image, not the Vite dev server.
Build and deploy it after the k3d backend from `../deploy/k3d`:

```bash
./build-frontend-image.sh
./deploy-frontend.sh
```

Open `https://heroassociation.test:8443`. Envoy Gateway serves the UI
and routes `/api` and `/auth` to BFF on the same origin. The k3d Playwright
suite runs with `npm run test:k3d` from `../e2e` without resetting lab data.
See [`../deploy/k3d/README.md`](../deploy/k3d/README.md#build-and-deploy-the-frontend)
for cluster setup, local CA trust, and image-restart instructions.

## Current prototype

- Side navigation for Overview, Heroes, Quests, Agency, Market, and Feed;
  Map is visible in k3d and opt-in for ordinary local development
- Backend-loaded agency summary, roster, quest progress, upgrade levels, item
  and rune inventory, hero rune slots, agency feed posts, and market order
  book; the state refreshes every five seconds while the tab is visible
- Live market order book with buy and sell order creation and cancellation for
  the current Manager or, for a leader, their agency
- The Heroes screen loads the global recruitment board. An onboarded Manager
  can claim a free Level 1 NPC once for their personal roster by default, or
  explicitly for the agency if they are its leader. The claim API also works
  before agency membership, but the Heroes screen appears after agency setup.
  Both claim routes share the globally unique recruitment board.
- Hero roster grouped into an active quest party, prepared parties, and
  unassigned heroes at the agency
- Prepared parties can be named and have available personal or agency heroes
  added or removed through the backend. Assignment is free and does not
  change hero ownership; members retain Training or Resting until a quest
  starts.
- API-loaded available quests show party-size, duration, and gold-reward
  details. The selected party displays its total agency-hero borrowing fee
  beside the Manager's personal gold. Starting the quest submits this quote,
  atomically pays the agency on success, and moves its heroes to `ON_QUEST`.
  The agency leader can set each agency hero's fee from its card.
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
- Quests shows available objectives and active or resolved progress; it no longer
  embeds a battle or polls the legacy combat-sync endpoint.
- Map is the k3d battle screen: server-authoritative Expedition snapshots arrive
  over the BFF WebSocket, with reconnect, Continue, and Return controls. The
  earlier Phaser scene animates ordered hits, criticals, recovery, spells,
  cooldowns, Hero and creature resources, and equipped runes without deciding
  the combat result.
- The existing Core quest worker still advances older quest fixtures in the
  background. Map battles are separate from Quest objectives for now; linking
  them is deferred until Quest gameplay is redesigned.
- Responsive layout for desktop and mobile screens

The game rules and the intended gameplay model live in
[`../GAME.md`](../GAME.md).
