# Hero Association Game Design

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
- Heroes can receive equipment.
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

### Initial class roles

| Class | Role |
| --- | --- |
| Warrior | Tank |
| Mage | Area-of-effect damage |
| Archer | Single-target damage |

## Hero stamina and agency management

Managers are responsible for managing their heroes' stamina.

- Quests consume hero stamina.
- Rest restores hero stamina.
- Lower stamina reduces a hero's effectiveness on quests.

## Agency progression

An agency has an overall Agency Level and specialized upgrade levels:

- Training Level
- Rest Level
- Size Level
- Reputation Level
- Medical Level
- Intelligence Level

The Agency Level sets the maximum available level for each specialized upgrade.
Managers do not need to upgrade every specialized level before advancing the
Agency Level.

The Rest Level improves the agency's ability to restore hero stamina.

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

- Managers can send one hero or a team of heroes on a quest.
- Quest outcomes depend on the heroes' abilities and stamina.
- A poorly matched or exhausted hero can fail a quest.
- Quests take time to complete and can require objectives such as killing a
  specified number of creatures or another defined objective.
- Quest rewards can include gold, chests, and items dropped by creatures.
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
- The initial feed supports text posts and in-game items.

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
