# Hero Association Game Design

The implementation order and unfinished engineering tasks are tracked in
[`ROADMAP.md`](ROADMAP.md).

## Concept

Hero Association is a social management game in which players manage agencies
of medieval-style heroes. A player can operate an agency alone or invite
friends to participate.

The player acts primarily as an agency manager rather than directly controlling
a hero in combat.

## Agencies and social play

- A player can create an agency.
- The initial implementation lets an onboarded Manager with no agency
  membership create one empty Level 1 agency as its leader. Invitations,
  leaving, and ownership transfer remain separate features.
- An agency belongs to its leader.
- An agency can be managed by its leader alone or together with invited
  managers.
- Managers recruit heroes, equip them, care for their well-being, and assign
  them to quests.
- A manager can leave an agency and start a new agency as its leader.

### Agency revenue

- The agency receives 10% of a Manager's gold earnings by default. Its leader
  can configure the share from 0% to 99%. The design goal is to cover all
  Manager gold earnings, but the treatment of market-sale proceeds, refunds,
  and the effective rate for work already in progress still needs an explicit
  rule before implementation. Moving existing gold between wallets is not
  new earnings, so gold transfers do not trigger the agency share. Gold
  transfer commands use UUIDv7 operation keys and atomic receipts: retrying
  an uncertain response with the same key cannot debit a wallet twice.
- An agency hero's per-quest borrowing fee starts at 0 gold. The agency leader
  can set a nonnegative fee for each hero. Party assignment is free; on a
  successful quest start, the total quoted fee moves atomically from the
  party Manager's personal wallet to the agency treasury. Leaders pay too.
  A stale quote or insufficient funds rejects the start without charging.
- This treats the agency like a shared organization: Managers retain personal
  progression while its treasury funds shared heroes and upgrades.

### Personal and agency ownership

Managers own their personal hero rosters, gold wallets, item and rune
inventories, parties, and market orders. Agencies keep separate assets.
Market order ownership is implemented in the extracted Market service; Core
Assets authorizes and moves resources. Reward accounting and agency-change
workflows remain planned:

- Each Manager owns a personal hero roster, gold wallet, and item and rune
  inventory. Personal market orders use these assets; agency-change workflows
  remain planned. Personal assets will remain with the Manager when changing
  agencies.
- Each agency separately owns its treasury, heroes, item and rune inventory,
  and market orders. Agency assets stay with the agency when a Manager leaves.
- A new Manager starts with zero gold, no items or runes, and three personally
  owned Level 1 heroes: one Warrior, one Mage, and one Archer. These are starter
  heroes for each Manager, not three globally unique recruits.
- Personal recruitment is the default and works even before the Manager joins
  an agency. An agency leader can explicitly choose to recruit a hero for their
  agency instead. Both choices claim the same globally unique NPC once.
- A Manager owns their prepared parties and can assign their own available
  heroes or available agency-owned heroes. Other Managers cannot change or
  launch the party. Borrowing does not transfer hero ownership. Agency-hero
  fees are paid only when a quest successfully starts.
- Quest items go to the party's Manager. The agency's configurable gold share
  applies to the Manager's eligible gold earnings; the exact accounting scope
  is still open as described above.
- Managers can place personal market orders without choosing an agency.
  Agency market orders require leadership of the selected agency. Delegated
  trading permission is a future rule. A market order reserves gold or items
  from its owner, and refunds and trade settlement return to that same owner.
  Both owner types pay the 10% market fee. No additional agency share is taken
  from personal sale proceeds until that separate economic rule is decided.
- Any Manager can send personal gold to any existing agency, even if they are
  not a member. Only an agency's leader can send treasury gold to any
  existing Manager, including themselves. These are direct wallet transfers,
  not payments or rewards; they do not create gold or incur a market fee.
  A transfer succeeds only when its source has enough gold.

The expanded [local test fixtures](TEST_DATA.md) include multi-Manager agencies
and separate personal starter heroes, wallets, and inventories.

## Heroes

- Heroes are recruitable non-player characters (NPCs).
- The initial global recruitment board offers Alden Steelward (Warrior), Seris
  Dawnflame (Mage), and Tarin Windmark (Archer). They are free Level 1 NPCs;
  each can be claimed once for a Manager's personal roster or, by explicit
  choice of its leader, for an agency. A recruited hero starts in `TRAINING` with
  full class health and mana and 100% stamina.
- Heroes can equip runes.
- A hero's rune loadout is locked while that hero is on a quest.
- Each hero has five rune slots. A hero who has learned spells also displays
  spell slots.
- Unequipped runes are currently stored in the agency rune inventory. During
  the initial prototype, any available agency rune can be equipped in any hero
  rune slot; compatibility rules will be added later.
- Agency and personal inventories store stackable materials. Magic Crystals
  and Iron Ingots can be traded, but cannot yet be equipped, spent, or looted.
- The initial hero classes are:
  - Warrior
  - Mage
  - Archer
- Each hero has abilities that affect their performance on quests.

### Skill growth and agency training

Every class has Melee, Distance, Magic, and Shield Levels, including a
Warrior's Magic Level. New heroes start every skill at Level 1. The seeded
Elara Moonweaver has already reached Magic Level 15 to demonstrate both mage
spells. Their strongest skills remain:

| Class | Preferred skills |
| --- | --- |
| Warrior | Melee and Shield |
| Mage | Magic |
| Archer | Distance |

Melee and Distance gain progress on each valid attack attempt against a living
creature. Magic gains progress from mana actually spent, including on spells;
Mage basic attacks spend 20 mana when available. At less than 20 mana, they
still deal normal damage for free but do not train Magic. Other free attacks
and mana recovery do not train Magic. Shield progresses on
successful blocks with a shield. Class aptitude makes Warrior Melee, Archer
Distance, and Mage Magic fastest; other classes learn those skills more slowly.

Planned agency training awards 2x the corresponding skill progress per practice
action at Training Level 1. Each later Training Level adds 5% of this baseline;
low stamina halves skill progress even while training. Magic practice must
spend mana; resting awards no skill progress. Heroes on quests cannot use
agency training, but can gain hero XP from defeated creatures and skill
progress from their combat actions. The class-rate table, point formula, and
proposed death-loss rules are in
[`PROGRESSION.md`](PROGRESSION.md). Combat stamina, Melee, Distance, Magic,
and creature XP are implemented; Shield blocking and agency practice remain
planned.

### Initial mage spells

Spells can eventually be active or passive and may consume mana or life. The
first prototype implements these active, mana-consuming mage spells:

| Spell | Target | Damage | Requirement | Mana | Cooldown |
| --- | --- | --- | ---: | ---: | ---: |
| Fire Ball | One creature | 10 + 150% of Magic Level | Magic Level 10 | 20 | 3 seconds |
| Lightning Rail | Every living creature | 2 + 80% of Magic Level | Magic Level 15 | 40 | 5 seconds |

The prototype auto-casts a learned spell when its cooldown ends and the mage
has enough mana. Passive spells, life-cost spells, and spells for Warriors and
Archers remain undefined. While a spell is cooling down, a dark radial overlay
clears from right to left across its combat spell icon.

### Initial class roles

| Class | Role |
| --- | --- |
| Warrior | Tank |
| Mage | Area-of-effect damage |
| Archer | Single-target damage |

## Hero stamina and agency management

Managers are responsible for managing their heroes' stamina. Full stamina is
48 hours. A living hero loses one stamina minute per minute of active battle;
quest travel, waiting, and time after that hero falls or combat ends do not
drain it. Creature kills are not required for stamina to decrease.

At the agency, Training recovers one stamina minute per real minute. Resting at
Rest Level 1 recovers two stamina minutes per real minute; each additional Rest
Level adds 10% of the Level 1 rate (Level 2: 2.2; Level 3: 2.4). Both activities
also recover health and mana: Training uses the class base rate and Resting
uses twice that rate. Core also recovers stamina at the Training or Rest rate.

Compare exact stamina time, not rounded percentages. Above 40 hours adds 50
percentage points to hero XP only; below 15 hours halves XP, skill progress
(including agency training), and future creature drop chances. Exactly 40 or
15 hours is the normal band.

| Stamina | Hero XP at `1x` | Hero XP at `2x` | Skills | Future loot chance |
| --- | ---: | ---: | ---: | ---: |
| Above 40 hours | 150% | 250% | Normal | Normal |
| 15 through 40 hours | 100% | 200% | Normal | Normal |
| Below 15 hours | 50% | 100% | Half | Half |

For example, low stamina halves a `2x` XP event to `1x`, while high stamina
adds 50 percentage points to make it `2.5x`. Future loot amounts remain the
same; only drop chance is halved. A mixed-stamina party's loot rule remains
open until economic rewards are implemented. See [`PROGRESSION.md`](PROGRESSION.md)
for formulas.

## Server-wide event rates

Core has independent XP and skill rates, each `1x` by default. A timed event
can temporarily change either rate without restarting the service. The rate
active when the kill or skill action occurs applies; delayed combat syncs do
not retroactively receive a new rate. A future reward-owning domain will have
its own loot-chance rate. It changes drop chances, not amounts or the number
of rolls, and is not part of the current Core progression slice. See
[`PROGRESSION.md`](PROGRESSION.md).

## Agency progression

An agency has an overall Agency Level and specialized upgrade levels:

- Training Level
- Rest Level
- Size Level
- Reputation Level
- Intelligence Level

The Agency Level sets the maximum available level for each specialized upgrade.
Managers do not need to upgrade every specialized level before advancing the
Agency Level.

Each Rest Level after Level 1 increases stamina recovery speed while Resting
by 10% of the Level 1 rate. Each Training Level after Level 1 increases agency
skill-practice speed by 5% of the Level 1 rate. These bonuses are additive
against the baseline, not compounded; neither changes health/mana recovery.

The Size Level determines how much room the agency has for heroes and its
facilities.

The Reputation Level represents the agency's external recognition. It is earned
through gameplay, such as successful quests and hero achievements, rather than
bought with gold. Higher reputation can unlock better quests, stronger hero
recruits, and improved market offers.

The Intelligence Level represents the agency's scouts and information network.
It reveals more information before a quest, including its difficulty, enemies,
recommended hero classes, expected rewards, and risks.

Gold costs increase exponentially for all agency upgrades and Agency Level
advancement. The growth rate must be balanced so that progression remains
meaningful without becoming excessively grindy.

## Maps and Expeditions

World owns immutable, versioned `FIELD` and `DUNGEON` definitions. Each Map
contains ordered floors, layouts and encounters that reference exact Creature
versions. A field repeats its encounters; a dungeon finishes after its final
encounter. Troll Field and the two-floor Broken Pass Cavern are seeded. Cities,
procedural generation and travel remain future work.

Each Manager has a prepared Party and at most one active Expedition. New
Managers start with three personal Heroes. A Party must retain at least one
Hero; editing and entry require agency membership. Current Map entry accepts
personal Heroes; agency borrowing and pricing remain deferred. Core persists
Party membership and reserves Heroes, while Expedition owns the active run.

Admission pins the complete World plan, Hero resources, Assets loadouts and any
optional Quest assignment. Changing a catalog version affects later admissions.
Admitted fights and Continue need no World lookup. A run has no time limit.
Winning a field encounter leaves it waiting for Continue. Clearing a dungeon
or wiping the Party prevents further encounters and waits for explicit Return.
Return during a fight takes effect after that fight. Permanent changes settle
through their owners before Heroes become available again.

## Quests

Quest owns optional objective definitions, Manager assignments, return receipts
and reward recovery. Acceptance is independent of Party selection and starts no
combat. A Manager can accept one assignment or cancel it at the agency. Both
commands are free, and unavailable while away. A completed or cancelled
assignment may be replaced with a fresh one.

Objectives count Creature kills, boss defeats or dungeon completion, with
optional eligible Map restrictions. Entry without a Quest is allowed. An
ineligible Map can be explored without advancing the assignment. Expedition
pins the assignment and saved progress at entry, and derives new progress from
server combat outcomes. Kills before a wipe still count. Return banks progress
once; an incomplete assignment remains active for another run. Meeting the
objective earns the pinned reward on return even after a later wipe.

Assets pays gold, item or rune rewards once under the assignment ID. Unknown
delivery retains the Quest and Expedition fences until receipt recovery after
restart or outage. Cancellation pays nothing and resets no other game owner.

Initial rewards are 120 gold for three Troll kills, 85 gold for four Forest
Wolf kills in the cavern, 120 gold for its Troll boss, and 160 gold plus one
Iron Ingot for clearing the cavern. Creature seeds retain empty economic drop
tables. Drop evaluation uses a pinned table and deterministic random stream;
balanced amounts, shared Capacity and the mixed-stamina loot rule remain future
work. Carried loot belongs to the Party's Manager.

The seeded Troll has 2,000 health, 4 damage, a 1.6-second attack interval,
100 mana and 10% critical chance. Forest Wolf has 120 health, 10 damage and no
critical chance. Both grant a provisional base of 100 XP. Each living Hero gets
an individual stamina-adjusted award without dividing it by Party size or
damage dealt. Fallen Heroes get no XP from later kills. Valid attacks and mana
spending advance skills even without a kill. Heroes are not permanently lost
and there is no death fee; further defeat penalties and return-resource rules
remain separate design work in [PROGRESSION.md](PROGRESSION.md).

See [the service contracts](WORLD_QUEST_ARCHITECTURE.md).

## Combat

- Combat is automatic; Managers prepare Heroes before entering a Map.
  The server resolves each attack through the shared combat-engine library.
- Planned combat may also allow optional, timed manager interactions while
  the fight runs, such as using a consumable item to buff the party. These
  commands supplement automatic combat; they do not make basic attacks
  player-controlled. Item effects, availability, costs, and timing rules
  remain to be designed and are not part of the current combat prototype.
- Heroes are displayed side by side using simple placeholder representations at
  first.
- Creatures are displayed on the opposing side.
- A combat encounter can contain one to four creatures.
- Every hero and creature has its own attack timer.
- When a combatant's timer is ready, that combatant performs its next attack.
- Critical Chance and Critical Damage Rune effects are applied when a new combat
  snapshot is created. Attack-speed, attack, armor, health, and mana rune
  formulas still need game-design decisions.
- Damage popups cycle through three lanes above each target and drift outward,
  so closely timed hits remain readable. Basic damage is gold and magic damage
  is purple.
- There is no base critical-hit chance. Critical Chance Runes each add 1%
  critical chance. Critical Damage Runes each add 10 percentage points to the
  critical-damage multiplier: a normal critical hit deals 200% damage, while
  one Critical Damage Rune makes it deal 210%. A critical hit shakes the target
  and appears as a larger highlighted damage popup.

### Initial server combat rules

- The backend has a deterministic combat engine. It advances only when a caller
  supplies a target combat timestamp and supplies the random value used for
  critical-hit rolls; it never reads the system clock or generates randomness
  itself.
- A basic attack targets the first living opponent in that encounter. Each
  combatant has an independent initial attack time and then attacks again after
  its own attack interval.
- A critical hit is rolled independently for every basic or spell hit. It deals
  the base damage multiplied by the attacker's critical-damage multiplier,
  rounded to the nearest whole number.
- Living heroes recover their class health and mana values once each combat
  second. On the same combat time, recovery resolves before spells, and spells
  resolve before basic attacks, making ties deterministic.
- Mages who meet a spell's Magic Level requirement cast it automatically when
  its timer is ready and they have enough mana. A spell without enough mana
  retries one second later. Fire Ball targets the first living creature and
  Lightning Rail targets every living creature.
- The initial Troll encounter has a persisted server snapshot containing every
  combatant's state and next action times. An explicit combat-sync command
  advances it by elapsed real time, without mutating the normal state-read
  endpoint. The encounter retains its latest 100 server-generated events so a
  defeats. When a new snapshot is created, each hero's equipped Critical Chance
  Runes are summed (up to 100%) and Critical Damage Rune values are added to
  the base 2× multiplier. Armor, attack speed, attack, health, and mana rune
  formulas still need a game-design decision.
  represented in the engine inputs.
- Each combat synchronization persists the current health and mana of heroes
  in the encounter. When combat reaches a terminal result, Hero Victory changes
  the quest to `COMPLETED` and Creature Victory changes it to `FAILED`; both
  record `finishedAt`, release the party, and return its heroes to Training.
  Stamina costs, rewards, and non-permanent hero-defeat penalties remain to be
  implemented.

### Initial hero combat attributes

Heroes begin at Level 1. The initial health and mana values are:

| Class | Health | Mana | Basic damage | Attack interval | Health recovery / second | Mana recovery / second |
| --- | ---: | ---: | ---: | ---: | ---: | ---: |
| Warrior | 300 | 50 | 22 | 1.3 seconds | 10 | 2 |
| Mage | 100 | 500 | 32 | 1.7 seconds | 2 | 10 |
| Archer | 200 | 200 | 26 | 1.1 seconds | 6 | 6 |

### Current combat view

- In the Quests screen, clicking an in-progress quest card expands the card to
  show the current encounter.
- The battlefield is rendered inline with Phaser, while React continues to
  own the surrounding application screens and quest interface.
- The first encounter uses three heroes against three low-damage placeholder
  trolls. It shows the Level 1 heroes' health and mana from the server combat
  snapshot.
- Elara Moonweaver is Magic Level 15 and automatically casts Fire Ball at one
  target and Lightning Rail at every living target when their cooldowns are
  ready and she has enough mana. Her displayed spell slots show those spells.
- The server engine recovers hero health and mana once per second using their
  class recovery values. At the agency, training recovers both at the base
  rate and resting at 2× the base rate; agency stamina recovery is active.
- Each hero shows five read-only rune slots in combat so the party's equipped
  runes are visible. The initial quest party equips one Critical Chance Rune
  per hero, while Elara also equips a Critical Damage Rune. Creatures do not
  have rune slots.
- Each initial troll has a 10% critical-hit chance.
- Creatures also show a mana bar. The initial placeholder trolls each start
  with 100 mana, though no creature ability consumes mana yet.
- The Phaser view renders server state rather than simulating combat locally.
  It replays new server events as damage, spell, recovery, critical, and defeat
  effects while the encounter is expanded. It synchronizes every two seconds.
  A backend worker also advances all active combat snapshots every five seconds,
  so quests continue while the player is away.

## Market

- The market uses buy and sell offers, similar to a real-world stock market.
- A player can create a sell offer, such as one Magic Crystal for 100 gold, or
  a buy offer, such as 100 gold for one Magic Crystal.
- A buy offer reserves its maximum gold value and a sell offer reserves its
  items until it is filled or cancelled.
- Compatible offers match by price and creation time at the resting offer's
  price. The buyer receives the item, and the seller receives the gold.
- The market charges a 10% transaction fee. The seller receives 90% of the
  matched value and the fee is removed from the game economy for now.
- Cancelling an open order returns its remaining reserved gold or items.
- The initial implementation trades Magic Crystals and Iron Ingots. More item
  categories and market history will be added later.
- The planned ownership model allows personal and agency market accounts,
  each reserving gold or items from its own inventory. Agency trades require
  leadership or a future explicit permission.
- Assets owns item and rune definitions, wallets, inventories, and equipped
  rune ownership. These remain in Core during the first Market extraction.
  Core's private reservation API verifies personal ownership or agency
  leadership and records the authorizing Manager. Settlement/refund recovery
  follows [Assets recovery](ASSETS_RECOVERY.md); Market's separate service and
  durable worker remain future work.

## Social feed

- The game has a social feed for activity and updates within the Hero
  Association community.
- Heroes, as NPCs, can publish posts.
- Agencies and agency managers can publish posts.
- The current prototype supports agency-scoped text posts with one optional
  item-stack attachment. The attachment is a non-consuming reference to an
  item currently held by the agency. Community visibility and moderation will
  follow the authentication work.

## Initial gameplay loop

1. Create or join an agency.
2. Recruit and equip heroes.
3. Restore hero stamina through rest and agency management.
4. Assign heroes individually or in teams to quests.
5. Resolve quest outcomes based on hero abilities and stamina.
6. Use rewards and the market to improve the agency and prepare for new quests.

## Open design decisions

The following details are intentionally not defined yet:

- Which gold inflows count toward the agency share, especially personal market
  sales and refunds; when a changed share takes effect; and how to
  protect Managers from a surprise increase during an active quest or order.
- Whether and how an already-paid agency-hero fee is refunded if quest
  cancellation is introduced; later PvE defeat does not undo the start fee.
- The hero's health, mana, and stamina on return after PvE defeat.
- Creature-specific XP and skill-point balance.
- Starter-hero names.
- Invitations, permissions, and shared agency-management rules.
- Detailed abilities, equipment, strengths, and weaknesses for each hero
  class.
- Field and dungeon encounter generation, travel, and entry rules.
- Quest duration, progression, difficulty, and failure consequences.
- Combat damage, targeting, attack-speed, and creature-ability rules.
- Which additional item categories can be exchanged through the market.
- Feed moderation, visibility, and item-posting rules.
