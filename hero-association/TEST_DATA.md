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

| Manager | Personal gold | Personal items | Borrowing scenario |
| --- | ---: | --- | --- |
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

Core's local development and test profiles recreate the schema and load
`import.sql` on startup. Keycloak imports its realm only when the realm does
not already exist: changing the versioned JSON will **not** add users to an
existing Keycloak database. To pick up the new accounts in a disposable local
Compose environment, stop running host services and the k3d cluster if it is
using ports 80/443, then from `backend/` run:

```bash
docker compose -f compose.infra.yaml down --volumes
./scripts/start-infra.sh
```

This deletes the local Core and Keycloak database volumes, including any
manually created local users or game data. Do not run it against data you want
to keep. Existing k3d Keycloak data likewise needs an isolated-lab reset or
manual account creation; a normal Pod restart will not reimport the realm.
No running environment is reset merely by changing these fixture files.
