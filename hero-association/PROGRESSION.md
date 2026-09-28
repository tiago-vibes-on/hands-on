# Hero progression and PvE defeat

Status: partially implemented in Game Core. Progression storage and formulas,
combat-time stamina drain, Melee, Distance, and mana-spent Magic gains, and
creature XP awards are in place. Shield blocks, agency practice and stamina
recovery, event rates, and defeat penalties remain planned. Skill-point values
and training pace should be play-tested before becoming final balance.

Track implementation order and completed steps in [ROADMAP.md](ROADMAP.md),
Milestone 5.

This uses [Tibia's published experience table](https://www.tibia.com/library/?subtopic=experiencetable)
and [death guide](https://www.tibia.com/gameguides/?section=characters&subtopic=manual)
as references. The XP threshold formula below reproduces Tibia's table. The
skill-point curve and the precise death percentage are Hero Association rules
inspired by Tibia, **not** claims about Tibia's internal formulas.

## Hero experience and levels

Store a non-negative, cumulative XP total for every hero. Level is the largest
integer `L >= 1` whose threshold does not exceed that total:

```text
XP_required(L) = (50L^3 - 300L^2 + 850L) / 3 - 200
XP_to_next(L) = XP_required(L + 1) - XP_required(L)
              = 50L^2 - 150L + 200
```

| Level reached | Total XP required |
| ---: | ---: |
| 1 | 0 |
| 2 | 100 |
| 3 | 200 |
| 10 | 9,300 |
| 50 | 1,847,300 |
| 100 | 15,694,800 |

Each defeated creature has a base XP value. At defeat, calculate an XP award
independently for every eligible hero in the party from that same full base
value; do not divide it by party size, damage dealt, or hero class. Apply each
hero's stamina-adjusted server XP rate, then round that hero's award down to a
whole point. Heroes with different stamina may therefore earn different final
XP from the same kill. Each hero keeps their own cumulative XP and level.
Stamina is tracked as time, from zero to a full 48 hours. The provisional Troll
base is 100 XP; other current provisional creatures also use 100 XP until
creature-specific balance is defined. Only heroes alive at the kill timestamp
receive XP. A hero who has fallen receives no XP from subsequent kills.

For example, one creature worth 100 base XP at the default `1x` rate gives
party heroes at 42, 30, and 10 stamina hours respectively 150, 100, and 50 XP.
Each receives their own award from the same 100 XP base; nothing is split.

Creature defeat is the XP trigger, not the trigger for every progression
change. A battle with no defeated creature can still advance skills through
qualifying actions and consume stamina through elapsed active combat time.

Maximum health and mana follow the previously chosen class gains. Level-ups
recalculate both maxima; the class recovery rate does not change merely from
leveling:

```text
maxHealth(L) = baseHealth + (L - 1) * healthGainPerLevel
maxMana(L)   = baseMana   + (L - 1) * manaGainPerLevel
```

| Class | Base health | Health / level | Base mana | Mana / level |
| --- | ---: | ---: | ---: | ---: |
| Warrior | 300 | 40 | 50 | 10 |
| Mage | 100 | 15 | 500 | 60 |
| Archer | 200 | 25 | 200 | 25 |

## Skill levels

Keep cumulative, non-negative **skill points** separately for Melee, Distance,
Magic, and Shield on every hero, regardless of class. This includes a
Warrior's Magic Level. For now, every starter skill begins at Level 10 with
zero points; cross-class starting levels remain open to balancing. A Level 1
Mage therefore has Magic Level 10 and can use Fire Ball; Lightning Rail
becomes available at Magic Level 15. A skill's level is the highest `S >= 10`
reached by its cumulative point threshold:

```text
points_for_next_skill_level(S) = ceil(100 * (23 / 20)^(S - 10))
total_skill_points_required(S) = sum(points_for_next_skill_level(k), k = 10..S-1)
```

| Skill level reached | Total skill points required | Points to next level |
| ---: | ---: | ---: |
| 10 | 0 | 100 |
| 11 | 100 | 115 |
| 12 | 215 | 133 |
| 15 | 676 | 202 |
| 20 | 2,035 | 405 |

Proposed base point-earning rules, before class, training, and server rates:

- Each valid Melee or Distance attack **attempt** against a living creature
  earns one base point in that attack's skill. A miss or zero-damage hit still
  counts; an invalid action or dead target does not. One attack earns progress
  once, not once per target hit or damage dealt.
- Magic earns `manaActuallySpent / 20` base points from a mana-consuming spell
  or basic attack. The provisional 20-mana divisor keeps the existing skill
  curve usable; it needs play-testing. A cast earns no extra per-hit or
  per-target points. A zero-mana basic attack, a failed cast, and mana recovery
  earn no Magic points. The current Mage basic attack spends zero mana, so it
  does not train Magic; Mage spells do. If a future Mage basic attack spends mana, that spent
  mana counts just like spell mana.
- Shield earns one base point on a successful block while using a shield. It
  does not advance merely because the hero attacks or takes damage. Combat
  blocking is not implemented yet.
- At the agency, a hero may train a selected skill at **2x the base progress**
  at Training Level 1. Each additional Training Level adds 5% of that Level 1
  rate, not 5% compounded on the previous level. Melee/Distance use practice
  attacks; Shield uses practice blocks; Magic training must actually spend
  mana on a practice action, with mana recovery between actions. A
  mana-consuming Magic practice action must be available to every class,
  including Warriors. It is not free Magic progress every few seconds.
  Training action cadence still needs design. `RESTING` earns no skill points.

Class aptitude rates are provisional Hero Association balance values:

| Skill | Warrior | Archer | Mage |
| --- | ---: | ---: | ---: |
| Melee | 1x | 0.25x | 0.05x |
| Distance | 0.25x | 1x | 0.05x |
| Magic | 0.05x | 0.25x | 1x |

Shield aptitude rates remain to be decided. Class rates change how quickly a
hero learns a skill, not the skill's current level or the cost of an action.
All classes can own all skills, but combat progress requires a matching action;
for example, a Warrior cannot earn Distance points from a melee attack.

```text
skillPointAward = baseSkillPoints * classSkillRate * trainingRate * skillRate * staminaSkillFactor
trainingRate = 2 * (1 + 0.05 * (trainingLevel - 1)) at the agency; otherwise 1
staminaSkillFactor = 0.5 below 15 hours of stamina; otherwise 1
```

At default `1x` server skill rate and at least 15 hours of stamina, one
valid melee attack gives a Warrior 1 point, an Archer 0.25, or a Mage 0.05;
a comparable Training Level 1 strike gives 2, 0.5, or 0.1 points. Spending
20 mana gives a Mage 1 Magic point, an Archer 0.25, or a Warrior 0.05; the
same 20 mana spent practicing at Training Level 1 gives 2, 0.5, or 0.1 points.
At Training Level 2, those practice awards become 2.1, 0.525, and 0.105.
An independent `2x` server skill event doubles them again. Below 15 hours
of stamina, all skill awards (including agency practice) are halved; high
stamina gives no skill bonus. Keep cumulative fractional points exactly;
never round a small per-action award down to zero.

This is inspired by [Tibia's skill-by-use and vocation descriptions](https://www.tibia.com/gameguides/?section=characters&subtopic=manual)
and its [mana-spent Magic Level rule](https://www.tibia.com/gameguides/?section=magic&subtopic=manual),
but the rates, divisor, and training bonus above are our own proposed balance,
not Tibia's published formulas.

## Server-wide rates and timed events

For the current progression slice, Core is authoritative for two independent,
global rates: `xpRate` and `skillRate`, each `1x` by default. A scheduled
event may override either or both (for example, `2x` XP and `2x` skills)
from its server-side start time until its end time. The previous rates resume
automatically afterward. These are server configuration, not values supplied
by the browser, party, or agency. Changing an active event should not require
rebuilding or restarting a service. Keep the schedule in Core-owned persistent
storage, shared by all Core replicas rather than per-process properties.
Rate edits take effect only prospectively; progress already recorded or earned
before an event began is never recalculated. A future reward-owning domain
will apply the separately designed `dropRate` to economic loot.

```text
if stamina > 40 hours: effectiveXpRate = xpRate + 0.5
else if stamina < 15 hours: effectiveXpRate = xpRate * 0.5
else: effectiveXpRate = xpRate
heroXpAward = floor(creatureBaseXp * effectiveXpRate)
```

The high-stamina bonus is 50 **percentage points** of base hero XP only;
the low-stamina penalty halves the active XP rate. Exact boundary values
(40 hours and 15 hours) receive the normal rate. Compare raw stamina time,
not a rounded display percentage: 40 hours is 83.33% of full stamina and
15 hours is 31.25%.

| Stamina | Hero XP at `1x` | Hero XP at `2x` | Skill progress |
| --- | ---: | ---: | ---: |
| Above 40 hours | 100% + 50% = 150% | 200% + 50% = 250% | Normal |
| 15 through 40 hours | 100% | 200% | Normal |
| Below 15 hours | 100% * 0.5 = 50% | 200% * 0.5 = 100% | Half |

A creature worth 100 base XP gives an eligible high-stamina hero 150 XP at
`1x` or 250 XP at `2x`; below 15 hours it gives 50 or 100 XP. High-stamina
addition and low-stamina halving are deliberately different operations.

A full hero has 48 hours (2,880 stamina minutes). Each living hero loses one
stamina second per second of **active battle time** while their party fights
at least one living creature. Quest travel, waiting between encounters, and
time after that hero falls or combat ends do not drain stamina. The current
quest encounter begins fighting immediately, but future quests may include
non-combat intervals. Use simulated combat time, not kills, browser views, or
background sync calls; delayed synchronization must never drain an interval
twice. Clamp stamina between zero and 48 hours. For XP from a kill, use the
hero's stamina **at the creature's defeat timestamp**, after combat-time drain
accrued up to that instant.

At an agency with Rest Level 1, `RESTING` recovers two stamina minutes per
real minute. At any Rest Level `L >= 1`, the rate is
`2 * (1 + 0.10 * (L - 1))` stamina minutes per real minute: Level 2 gives
2.2 and Level 3 gives 2.4. `TRAINING` recovers one stamina minute per real
minute, regardless of Training Level. The Rest upgrade does not change
training recovery; the Training upgrade changes skill progress, not stamina
recovery. Clamp recovery at the 48-hour maximum.

Economic rewards (gold and items), including the following proposed loot
rules, are deferred until the game domains are separated. They are not needed
to test combat-time stamina, action-based skills, or per-kill hero XP.

`dropRate` multiplies the chance of **each creature loot entry**, including a
gold entry. A low-stamina penalty halves the final, capped drop chance rather
than item or gold amounts. Roll each entry once per defeated creature:

```text
effectiveDropChance = min(100%, baseDropChance * dropRate) * staminaLootFactor
staminaLootFactor = 0.5 below 15 hours; otherwise 1
```

For a solo hero below 15 hours, a 10% gold entry becomes 5%, a 0.25% Mana
Crystal entry becomes 0.125%, and a 0.0001% rune entry becomes 0.00005% at
`1x` drop rate. High stamina gives no loot bonus. When a party contains heroes
in different stamina bands, the rule selecting the party's loot factor is
still open and must be decided before economic rewards are implemented.

| Creature loot entry | At `1x` | At `2x` | Amount on success |
| --- | ---: | ---: | --- |
| Gold | 10% | 20% | 5-25 gold |
| Mana Crystal | 0.25% | 0.50% | One crystal |
| King Troll Rune | 0.0001% | 0.0002% | One rune |

These are examples, not a universal loot table; each creature defines its own
entries and amount ranges. The rolls are independent, so gold and an item may
both drop. `2x` does **not** double the amount, make two rolls for the same
entry, or change fixed quest gold rewards. Gold that actually drops follows
the normal Manager/agency allocation; `dropRate` does not change that share.
The chance cap means a high-chance entry may gain less than twice its expected
drops. Keep enough precision for rare entries: `0.0001%` is one in a million
and `0.0002%` is one in 500,000.

Apply the rate active at the **game event's timestamp**: creature defeat for
hero XP, a qualifying action for combat skill points, or each completed
practice action for agency training. Future loot rules also use the defeat
timestamp. A quest started before an event can benefit from it only for events
occurring during its active window. Delaying a combat sync must not turn
earlier kills into event-rate XP. Split elapsed practice time at event
boundaries. Persist enough event/rate information to replay and deduplicate
progress across Core workers and restarts.

For the initial implementation, allow one effective override per rate at a
time; overlapping events must not stack multipliers implicitly. Scheduling,
validation, audit history, and an authorized way to change rates remain
implementation tasks. Fractional skill progress is required even with integer
server rates; keep it exact rather than rounding per action.

## PvE defeat penalty

A defeated hero is not deleted. Apply the penalty **once per defeat**, even if
the rest of the party later wins. Use the hero's level immediately before that
defeat to calculate one percentage `p`. Up to Level 23, `p = 10%`. From Level
24 onward, approximate Tibia's published level-loss milestones with:

```text
levels_equivalent(L) = 0.5 + L / 100
previous_level_xp(L) = XP_required(L) - XP_required(L - 1)
p(L) = min(10%, levels_equivalent(L) * previous_level_xp(L) / XP_required(L))
```

This approximates one level lost at Level 50, 1.5 levels at Level 100, and 2.5
levels at Level 200. It is a simple public-rule approximation, not Tibia's
exact server calculation. Apply the **same pre-death percentage** to total XP
and to each skill's cumulative points, not only to progress in the current
level:

```text
XP_after = XP_before - floor(XP_before * p)
skill_points_after = skill_points_before - floor(skill_points_before * p)
```

Recalculate hero and skill levels from the remaining totals. Hero Level cannot
fall below 1 and skills cannot fall below Level 10. Their progress bars are
recomputed from the totals; levels may go down. At Level 1, the 10% rate can
still reduce skill points even when hero XP is zero.

| Level at its XP threshold | Approximate loss of total XP and skill points |
| ---: | ---: |
| 23 | 10.00% |
| 24 | 9.57% |
| 50 | 6.11% |
| 100 | 4.54% |
| 200 | 3.77% |

Example: a Level 100 hero exactly at 15,694,800 XP loses 713,100 XP. A skill
at Level 15 with exactly 676 cumulative points loses 30 points and falls back
to Level 14. Use integer/rational arithmetic rather than floating-point
rounding so repeated processing and tests give identical results.

There is no permanent hero death or death fee. Blessings, item loss, and PvP
penalties are out of scope. The hero leaves the encounter and returns to its
owner's roster when the quest resolves. The exact health, mana, and stamina on
return still need a decision; if a lost level lowers a resource maximum, the
current resource must at least be clamped to that new maximum.
