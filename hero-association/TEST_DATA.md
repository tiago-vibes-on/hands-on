# Local test accounts and agencies

These fixtures are for disposable local development and automated tests only.
All passwords below are deliberately weak and must never be used outside the
local or isolated k3d lab. Each seeded Manager also owns a separate personal
roster of one Level 1 Warrior, Mage, and Archer. Personal rune inventories
are empty; Manager 3 and Manager 4 have small item stacks for personal market
tests. Most have zero personal gold; borrowing-payment fixtures below have
specific balances. Existing agency assets remain separate. Personal trading,
parties, and recruitment are implemented.

## Sign-in accounts

The email is the Keycloak username. Each `managerN@mail.com` account uses
password `managerN`, profile name `ManagerN LastN`, and seeded display name
`Manager N` (for `N` from 1 through 10).

| Email | Password | Manager display name | Agency | Role |
| --- | --- | --- | --- | --- |
| `user1@mail.com` | `user1` | User 1 | Dawnwatch Agency | Leader |
| `user2@mail.com` | `user2` | User 2 | Ironridge Exchange | Leader |
| `user3@mail.com` | `user3` | None | None | Onboarding test |
| `manager1@mail.com` | `manager1` | Manager 1 | Dawnwatch Agency | Manager |
| `manager2@mail.com` | `manager2` | Manager 2 | Dawnwatch Agency | Manager |
| `manager3@mail.com` | `manager3` | Manager 3 | Dawnwatch Agency | Manager |
| `manager4@mail.com` | `manager4` | Manager 4 | Dawnwatch Agency | Manager |
| `manager5@mail.com` | `manager5` | Manager 5 | Ironridge Exchange | Manager |
| `manager6@mail.com` | `manager6` | Manager 6 | Ironridge Exchange | Manager |
| `manager7@mail.com` | `manager7` | Manager 7 | Ironridge Exchange | Manager |
| `manager8@mail.com` | `manager8` | Manager 8 | Silverkeep Guild | Leader |
| `manager9@mail.com` | `manager9` | Manager 9 | Silverkeep Guild | Manager |
| `manager10@mail.com` | `manager10` | Manager 10 | Silverkeep Guild | Manager |

An additional Core-only fixture, Soren, is a Manager member of Dawnwatch but
has no Keycloak login. It supports authorization tests. `user3@mail.com`
intentionally has no seeded Account or Manager so registration/onboarding
tests still cover that path.

## Agency membership layout

| Agency | Leader | Additional managers | Seeded total |
| --- | --- | --- | ---: |
| Dawnwatch Agency | User 1 | Soren, Manager 1–4 | 6 |
| Ironridge Exchange | User 2 | Manager 5–7 | 4 |
| Silverkeep Guild | Manager 8 | Manager 9–10 | 3 |

The seeded Broken Pass Party belongs to User 1. Its agency heroes remain
assigned for the existing in-progress quest fixture. Available agency-owned
heroes can now join a Manager's prepared party without changing ownership.
Borrowing is free at assignment and charged only when the quest starts.
The seeded Troll used by the first Map field has 2,000 HP, 4 attack damage,
and 100 base XP; this balances the three-creature starter encounter.
Every other seeded Manager has a Main Party containing their personal Warrior,
Mage, and Archer. In particular, User 2 can enter the Map immediately with
their three-hero party. User 2's personal Mage is a deliberate Magic Level 15
showcase exception with both combat spells unlocked; each member of that party
also has two equipped runes. Other starter skills begin at Level 1, and the
personal rune inventories remain empty. New Managers receive the same Main
Party at Manager onboarding.

| Manager | Personal gold | Personal items | Borrowing scenario |
| --- | ---: | --- | --- |
| User 2 | 100,000 | None | Large local-only wallet for game and market testing |
| Soren (Core-only) | 25 | None | Exact payment for Emberveil |
| Manager 2 | 25 | None | Exact payment for Emberveil |
| Manager 3 | 20 | 2 Magic Crystals | Insufficient for Emberveil |
| Manager 4 | 200 | 5 Iron Ingots | Can afford Hawkeye |
| All others | 0 | None | Default zero-balance case |

Dawnwatch agency heroes Oakshield, Emberveil, and Hawkeye have borrowing fees
of 0, 25, and 100 gold per quest respectively. A stale fee quote or
insufficient personal gold rejects quest start without moving gold. Manager 3
can place a personal Magic Crystal sell order; Manager 4 can place personal
Iron Ingot sell orders or buy orders using their own wallet. Existing Core
databases must be reset to receive the market-owner column and seed values;
local dev/test recreate the schema automatically. For the isolated k3d lab,
use `./run-k3d-pipeline.sh --reset-core-db` from `pipeline/` to rebuild,
verify, and reseed Core with the exact archived image. This discards only
k3d Core game data; it does not reset Keycloak, Redis, or normal development.

The fixture IDs are deterministic UUIDv7 values. The Keycloak users are in
[`backend/keycloak/realm/hero-association-realm.json`](backend/keycloak/realm/hero-association-realm.json);
the matching Core Accounts, Managers, agencies, and memberships are in
[`backend/hero-association-core/src/main/resources/import.sql`](backend/hero-association-core/src/main/resources/import.sql).
The k3d realm generator reads the same versioned Keycloak source. The Core
fixture test verifies the membership mapping and personal starter assets.

## Refreshing disposable environments

Direct local Maven test profiles can recreate the schema and load
`import.sql`; hybrid Quarkus dev mode validates the shared k3d schema and does
not reseed it. Keycloak imports its realm only when it does not already exist:
editing the versioned JSON does **not** add users to the existing k3d realm.
Use the disposable k3d E2E namespace to validate new fixtures without touching
daily accounts. To change daily Core game data, use the explicit Core
reset/reseed workflow in [LOCAL_DEVELOPMENT.md](LOCAL_DEVELOPMENT.md); this
does not reset Keycloak. Recreating the daily Keycloak realm requires a
separately planned, backed-up k3d lab reset or an explicit account update.
A normal Pod restart will not reimport the realm.
