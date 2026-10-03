# World catalogs and Expedition Quest objectives

Status: implementation and verification in progress.

World owns immutable, versioned Creature definitions and Map definitions in its
own PostgreSQL database. Map and Creature are grouped because an encounter
layout names exact Creature versions. The public catalog exposes the latest
Maps; private admission reads a complete Map plan. Core persists that plan
with its Hero reservation, so an exact admission retry reuses the same versions.
Expedition copies it into Redis and needs no catalog request during a fight or
Continue. Publishing a version cannot change an admitted run.

Fields repeat their encounter sequence indefinitely. Dungeons have consecutive
floors and finite encounter sequences; clearing the final encounter leaves the
Party waiting for explicit Return. A wipe also waits for Return. Neither case
starts another encounter. The first content preserves Troll Field and adds
Broken Pass Cavern, with Forest Wolves on its entrance floor and a Troll boss
on its second floor. Creature loot tables are pinned and evaluated with a
separate deterministic random stream; current Creature seeds retain empty
economic drops, while Quest rewards introduce the first objective payouts.

Quest owns versioned objective/reward definitions, Manager assignments,
admission pins, return receipts, and reward recovery in its own PostgreSQL
database. A Manager accepts one optional Quest at the agency. Accepting does
not select a Party, start combat, or charge a borrowing fee. Cancellation is
allowed at the agency. An active Expedition blocks acceptance and cancellation.
A completed or cancelled assignment can be replaced by a new acceptance.

The initial objectives are Creature kill count, boss defeat, and dungeon
completion. A definition may accept every Map or restrict eligible Map IDs.
An Expedition can start without a Quest and can enter an ineligible Map without
making progress. At admission Quest freezes the active assignment and its
banked progress. Expedition accumulates bounded progress in Redis from server
combat outcomes, including Creature kills before a wipe. It writes no per-hit
or per-fight Quest rows. Return banks progress once; unmet objectives remain
active for a later run. An objective met before Return earns its reward even
if a later encounter wipes the Party.

Core remains the temporary permanent-Hero settlement coordinator. It validates
the complete frozen aggregate before asking Quest to apply its matching return.
Quest stores the immutable return before
calling Assets with the assignment's stable reward operation key and its own
service credential. Assets commits gold/items/runes and a receipt together.
Quest validates that receipt before completing the assignment and releasing
its admission fence. Unknown delivery remains pending and a database worker
retries after restart. Core then confirms carried Assets and permanent Hero
progress. Core's admission reservation still prevents a new run or agency-only
Quest commands during that handoff. It acknowledges Expedition only after all
owners confirm, so Redis retains the frozen aggregate until every owner is safe.

The new public paths are `/api/v1/maps`, `/api/v1/creatures`, and
`/api/v1/quests/**`, routed by BFF to World or Quest with the server-held player
token. Internal service credentials never reach the browser. Core's old
Quest-start/combat-sync endpoints, Quest combat scheduler, Creature cache,
Quest tables, and Creature definition table are retired at the verified cutover.
Agency borrowing-fee configuration remains stored for future agency-Hero
Expedition borrowing, which is outside this change.

Verification must cover immutable versions, changed-payload retries, foreign
Managers, acceptance/cancellation races with admission, incomplete progress
across returns, completed Dungeon refusal of Continue, lost reward responses,
Assets/Quest outages and restart recovery, and exact-once payouts. The initial
cutover uses one verified eight-image archive and matching deterministic data
across Core, Assets, Market, World, and Quest; refuse active runs or unresolved
work before resetting the local lab. Restore frontend hot reload afterward.

Public APIs are `GET /api/v1/maps`, `GET /api/v1/creatures`, `GET /api/v1/quests`,
`POST /api/v1/quests/{definitionId}/accept` and
`POST /api/v1/quests/assignments/{assignmentId}/cancel`. The POST body is
`{ "commandId": "<UUIDv7>" }`; Manager identity always comes from Core's
service-key-protected authority check of the player JWT. Exact retries return
the original assignment; reusing a key with changed inputs is rejected.

Internal World plan reads require `HERO_ASSOCIATION_WORLD_SERVICE_KEY`.
Quest admission, absent-run release and orphan reads require
`HERO_ASSOCIATION_QUEST_EXPEDITION_SERVICE_KEY`. Core authority and Quest return
confirmation use `HERO_ASSOCIATION_QUEST_CORE_SERVICE_KEY`. Reward credit uses
`HERO_ASSOCIATION_ASSETS_QUEST_SERVICE_KEY` on the dedicated Quest route; the
Core credential cannot credit a Quest reward. Every service key is at least
32 characters and is held only by its callers/owner. Mesh authorization also
limits paths and workload identities. BFF only forwards the player JWT.

The first objective rewards are 120 gold for three Troll kills, 85 gold for
four Forest Wolf kills in the cavern, 120 gold for the cavern Troll boss, and
160 gold plus one Iron Ingot for clearing the cavern. These are initial seed
amounts, not balanced loot rules. Rune rewards are supported by the contract.
