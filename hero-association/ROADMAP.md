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

- [x] Model agency storage for gold, runes, and read-only stackable item
  materials. Quest loot and item transfers remain to be added.
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
- [x] Add a command or scheduled process that advances an in-progress quest.
  The seeded combat snapshot advances through an explicit sync command and a
  five-second background worker; quest completion still remains to be added.
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
- [x] Persist a compact quest-combat snapshot and bounded event history. The
  initial Troll encounter stores its current snapshot plus the latest 100
  server-generated events. New quests still need snapshot creation.
- [x] Replace the local Phaser combat simulation with server state. The Phaser
  view renders the synchronized snapshot.
- [x] Render new server combat events in Phaser, including attacks, spells,
  recovery, critical hits, and defeats. The scene does not replay history that
  happened before it was opened.

## Milestone 5 — Rewards, progression, and recovery

- [ ] Award shared party loot immediately after a creature is defeated while
  respecting party Capacity.
- [ ] Award individual hero experience using the stamina thresholds in
  `GAME.md`, and implement level-ups.
- [x] Synchronize current health and mana from authoritative combat snapshots
  to quest heroes.
- [x] Recover agency hero health and mana over time at the base Training rate
  and twice that rate while Resting.
- [ ] Apply quest stamina costs and stamina recovery over time.
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

- [x] Add agency-scoped text feed posts by agencies, the current leader, and
  heroes, including one non-consuming reference to an agency item stack.
- [ ] Add agency invitations, membership permissions, manager departure, and
  starting a new agency.
- [ ] Define feed visibility and moderation rules before exposing posts beyond
  an agency.

## Milestone 9 — Authentication and real-time updates

- [ ] Add authentication and authorization before exposing player-owned data
  outside local development.
- [ ] Enforce agency membership and leader permissions on every write command.
- [x] Use five-second browser polling for initial agency, quest, and feed
  refreshes while the tab is visible. Revisit real-time transport when market
  matching or higher-frequency updates need it.
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
- [ ] Define which item categories can be traded, and the fee destination.
- [ ] Define agency invitation, ownership transfer, and permission rules.
