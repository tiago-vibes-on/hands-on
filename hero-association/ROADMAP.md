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

## Milestone 1 — Authoritative agency management

- [ ] Define the initial manager identity model and agency membership roles.
  Start with a single leader if authentication is intentionally deferred.
- [ ] Add agency creation and retrieval endpoints.
- [ ] Add hero recruiting and hero detail endpoints.
- [x] Add a hero activity command for `TRAINING` and `RESTING`. Prevent a hero
  on a quest from changing agency activity.
- [x] Add party creation and membership commands, and include party details in
  agency state. Allow a party to contain one or more heroes.
- [x] Replace frontend-only party-preparation actions with these APIs.

## Milestone 2 — Inventory and rune loadouts

- [ ] Model an agency inventory that can contain runes, items, gold, and quest
  loot.
- [x] Add commands to equip and unequip a rune, atomically moving it between
  agency inventory and a hero's five rune slots.
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
- [ ] Persist quest status and timestamps. `AVAILABLE` and `IN_PROGRESS`, plus
  start and expected-completion timestamps, are persisted; completion, failure,
  cancellation, and completion timestamps remain to be added.
- [ ] Add a command or scheduled process that advances an in-progress quest.
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
- [ ] Apply rune effects that are already displayed: attack, armor, health,
  mana, attack speed, critical chance, and critical damage.
- [ ] Persist a compact quest-combat snapshot and combat event history for the
  frontend to render. The initial Troll encounter now has a persisted snapshot;
  snapshot creation for new quests, event history, and progression remain.
- [ ] Replace the local Phaser combat simulation with state and events from the
  backend.

## Milestone 5 — Rewards, progression, and recovery

- [ ] Award shared party loot immediately after a creature is defeated while
  respecting party Capacity.
- [ ] Award individual hero experience using the stamina thresholds in
  `GAME.md`, and implement level-ups.
- [ ] Apply quest stamina costs, health and mana state, recovery over time,
  and the 2× Rest bonus.
- [ ] Implement permanent hero death and the agency fee. The fee formula needs
  a game-design decision before implementation.
- [ ] Transfer completed-quest rewards from party capacity to agency inventory.

## Milestone 6 — Agency progression

- [ ] Implement agency gold balance, Agency Level, and specialized upgrades.
- [ ] Enforce the Agency Level cap on specialized upgrades.
- [ ] Implement and test exponential upgrade costs; the exact cost curve needs
  balancing before it can be finalized.
- [ ] Apply the concrete effects of Training, Rest, Size, Reputation, and
  Intelligence levels.

## Milestone 7 — Market

- [ ] Define tradable inventory item categories and a safe reservation model
  for items and gold placed in market orders.
- [ ] Add buy and sell order creation, cancellation, listing, and matching.
- [ ] Apply the 10% market fee atomically when an order matches.
- [ ] Add market history and update the frontend market screen to use live
  orders rather than prototype offers.

## Milestone 8 — Social feed and multiplayer

- [ ] Add feed posts by agencies, managers, and heroes, supporting text and
  references to in-game items.
- [ ] Add agency invitations, membership permissions, manager departure, and
  starting a new agency.
- [ ] Define feed visibility and moderation rules before exposing posts beyond
  an agency.

## Milestone 9 — Authentication and real-time updates

- [ ] Add authentication and authorization before exposing player-owned data
  outside local development.
- [ ] Enforce agency membership and leader permissions on every write command.
- [ ] Decide whether polling is sufficient for initial quest, feed, and market
  refreshes.
- [ ] Add WebSocket or server-sent event updates only for features that need
  near-real-time changes, such as combat progress, matched market orders, and
  new feed posts.

## Open decisions that block implementation

- [ ] Define the hero-death fee formula and who receives it.
- [ ] Define quest duration, difficulty, failure, and cancellation rules.
- [ ] Define armor and attack-speed formulas, plus initial persistent creature
  attributes.
- [ ] Define agency revenue sharing between participating managers and the
  agency.
- [ ] Define market-tradable item categories and fee destination.
- [ ] Define agency invitation, ownership transfer, and permission rules.
