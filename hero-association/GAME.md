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
- An agency belongs to its leader.
- An agency can be managed by its leader alone or together with invited
  managers.
- Managers recruit heroes, equip them, care for their well-being, and assign
  them to quests.
- A manager can leave an agency and start a new agency as its leader.

### Agency revenue

- Quest payments are shared with the managers who take part in the quest.
- The agency retains a fee from quest payments.
- This model treats the agency like a real-world company: managers are paid for
  their work while the agency retains revenue to operate and grow.

## Heroes

- Heroes are recruitable non-player characters (NPCs).
- Heroes can equip runes.
- Each hero has five rune slots. A hero who has learned spells also displays
  spell slots.
- Unequipped runes are stored in the agency rune inventory. During the initial
  prototype, any available agency rune can be equipped in any hero rune slot;
  compatibility rules will be added later.
- Agency inventory also stores stackable materials. The initial Magic Crystal
  and Iron Ingot stacks are visible but cannot yet be equipped, spent, looted,
  or traded.
- The initial hero classes are:
  - Warrior
  - Mage
  - Archer
- Each hero has abilities that affect their performance on quests.

### Initial class training

Each initial class has two core training skills:

| Class | Training skills |
| --- | --- |
| Warrior | Melee Level; Shield Level |
| Mage | Magic Level |
| Archer | Distance Level |

Training happens only at the agency. Heroes assigned to a quest cannot train;
they earn individual experience by fighting creatures instead.

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

Managers are responsible for managing their heroes' stamina.

- Quests will consume hero stamina.
- At the agency, a hero can train or rest. Both recover health and mana based
  on the hero's class: training uses the base rate, while resting uses twice
  the base rate. A background worker applies elapsed recovery every five
  seconds. Stamina recovery is not implemented yet.
- Lower stamina reduces a hero's effectiveness on quests.
- Stamina also changes experience earned from creatures:

| Stamina | Experience gain |
| --- | ---: |
| 80% or more | 150% |
| 30% to 79% | 100% |
| Below 30% | 50% |

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

The Rest Level represents the agency's recovery facilities. Its concrete
mechanical effect will be defined later.

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

## Quests

- Managers always send a party on a quest. A party can contain one hero or
  multiple heroes.
- A manager can prepare a party at the agency before choosing a quest. Its
  members remain at the agency and keep training or resting until the quest
  begins.
- The first available quest is Lost Courier: find the missing courier in the
  old forest. It requires one to two heroes, has an estimated duration of 30
  minutes, and offers 85 gold. Starting it moves every selected party member
  from their agency activity to the quest. The game records the start and
  expected completion time; it does not yet advance or resolve the quest.
- All heroes assigned to a quest belong to that quest's party and are
  unavailable at the agency until the quest is complete. Other heroes remain
  at the agency, where they are either training or resting before they can join
  another party.
- Quest outcomes depend on the heroes' abilities and stamina.
- A poorly matched or exhausted hero can fail a quest.
- Quests take time to complete and can require objectives such as killing a
  specified number of creatures or another defined objective.
- Quest rewards can include gold, chests, and items dropped by creatures.
- After a quest, loot moves from the party's shared Capacity to the agency
  inventory, where it can be equipped or traded.
- Each hero in the party earns their own experience from creatures defeated
  during the quest, using the stamina-based experience gain, and can level up
  independently.
- The party has a shared Capacity that determines how many resources it can
  carry. Items are collected as soon as creatures are defeated.
- Hero death is permanent. When a hero dies, the agency pays a fee based on
  that hero's level.

## Combat

- Combat is automatic; managers prepare heroes before a quest rather than
  directly controlling each attack.
- Heroes are displayed side by side using simple placeholder representations at
  first.
- Creatures are displayed on the opposing side.
- A combat encounter can contain one to four creatures.
- Every hero and creature has its own attack timer.
- When a combatant's timer is ready, that combatant performs its next attack.
- Attack-speed rune bonuses are intended to shorten a hero's attack timer.
  Rune stat effects are not implemented in the initial prototype yet.
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
  client can render recent attacks, spells, recovery, critical hits, and
  defeats. Armor, attack speed, attack, health, and mana rune formulas still
  need a game-design decision; only the established critical values are
  represented in the engine inputs.
- Each combat synchronization persists the current health and mana of heroes
  in the encounter. Stamina costs, rewards, and permanent death resolution
  remain to be implemented.

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
  rate and resting at 2× the base rate; stamina recovery is still pending.
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
- When compatible offers match, the market exchanges the currency and item.
- The market charges a 10% transaction fee.

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

- The exact formula and recipient for the hero death fee.
- Invitations, permissions, and shared agency-management rules.
- Detailed abilities, equipment, strengths, and weaknesses for each hero
  class.
- Quest duration, progression, difficulty, and failure consequences.
- Combat damage, targeting, attack-speed, and creature-ability rules.
- Which item categories can be exchanged through the market.
- How the 10% market fee is collected and used.
- Feed moderation, visibility, and item-posting rules.
