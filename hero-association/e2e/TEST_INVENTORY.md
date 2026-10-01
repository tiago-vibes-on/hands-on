# Test inventory for the k3d consolidation

This inventory distinguishes tests by the resources they exercise. A green
test in one row does not substitute for a different row. The target is described
in [LOCAL_ENVIRONMENT_PLAN.md](../LOCAL_ENVIRONMENT_PLAN.md); this file records
the current migration gap rather than calling it complete.

| Kind | Current entry points | Dependencies and state | Target |
| --- | --- | --- | --- |
| Pure unit | Combat-engine JUnit, Core `HeroProgressionTest` and `UuidV7Test`, frontend `npm test`, pipeline `node --test` | Process-local objects and fixtures; no browser, live Service, or database | Keep local and fast. |
| Service/component | Direct `./mvnw test` or `package` in Core, BFF, and Expedition | Quarkus tests can start local Dev Services; the two older Rabbit transport tests use Testcontainers | Keep independent direct Maven workflows, but do not use local containers as the candidate gate. |
| Disposable k3d component | `deploy/k3d/test-isolated-components.sh [core|bff|expedition|all]` | Own PostgreSQL, Core/BFF/Expedition Redis, and RabbitMQ; Maven connects over loopback port-forwards with Dev Services disabled | Backend candidate builds run the selected lane before packaging. K3d AMQP tests cover both transport payloads; the E2E lane covers cross-role queue denial. |
| Historical Compose browser source | `tests/authentication.spec.js` and `tests/market-proxy.spec.js` | The retired runner used a second BFF route and a separate eight-second Keycloak realm | Not an active command. The isolated k3d suite covers the current MVP flows; Quest borrowing remains deferred. |
| Current k3d browser | `npm run test:k3d`, `test:k3d:expedition`, `test:k3d:map` | Existing development namespace and seeded accounts; Map tests change test Managers' Hero progress | Keep for smoke checks; use disposable data for the full integration gate. |
| Disposable k3d browser | `deploy/k3d/test-isolated-stack.sh` | Own namespace, databases, Keycloak, Redis, RabbitMQ, and Envoy E2E listeners; daily image references by default or exact four-image archive | Ten browser cases plus a second Map journey under Core Redis outage; BFF restart, BFF Redis outage/recovery, strict token expiry, and k6 thresholds pass with the BFF candidate. |
| Gateway and load | `npm run test:market:k6`, `load:k3d`, `load:mixed:k3d`, outage/scaling scripts under `deploy/k3d` | Envoy, rate-limit Redis, live Pods; some scripts stage temporary Core databases | Market k6 thresholds now also run inside the disposable namespace; keep disruptive scaling tests opt-in. |
| Candidate archive | `deploy/k3d/test-isolated-stack.sh <archive>` and `pipeline/run-k3d-pipeline.sh` | Exact four archived image IDs are checked against running disposable k3d Pods before and after BFF restart | Promotion requires matching `k3d-e2e-verification.json` after test cleanup, including uncommitted worktree candidates. The Compose archive runner is retired. |

The backend candidate path now runs Core, BFF, and Expedition component tests
against disposable k3d dependencies, including both AMQP transport payloads,
then packages with `-DskipTests`. Direct Maven builds still run their local
Dev Services and Testcontainers tests. The disposable Map journey verifies
Rabbit settlement and PostgreSQL creature fallback while Core Redis is down;
both broker roles receive AMQP 403 on the other role queue.

The isolated browser configuration already reuses the gold-transfer,
personal-market, and Expedition journeys. The old unrestricted market-proxy
case is superseded by the Envoy five-per-second policy test. The old
borrowing-quest fee journey belongs to the deferred Quest feature and is not
an active MVP gate; retain its test as historical coverage until Quest work
resumes. The Compose-only runner was retired after the full isolated archive
suite passed on 2026-10-01.

The retained legacy test source assumed a second BFF route at
`/__e2e-secondary`, eight-second Keycloak tokens, and a private Compose
Redis. Those files are not selected by the active k3d Playwright configs.
The k3d test runner ignores local certificate errors only inside its
disposable Chromium container and maps test hostnames only there.

Moving a test into k3d means running its integrated dependencies and requests
there, not merely using a k3d hostname while starting a second Compose stack.
The temporary namespace, Keycloak realm, database, Redis, RabbitMQ, and
Routes must be deleted after both success and failure. The candidate image
digest check must use running Pod `imageID`s, not only manifest tags.

The disposable runner can import a copied four-image archive and check all
running application Pod image IDs before its browser tests. It also verifies
session survival after replacement of both BFF Pods, fail-closed behavior
during disposable BFF Redis outage and after token-state expiry, and k6 market
thresholds. Pipeline promotion now requires the k3d runner's
`k3d-e2e-verification.json`; the separate Compose record is not accepted.
The BFF-only Quarkus 3.40.1 archive `localenv-bff-3401-20261001-a` passed
the strict expired-token-state check and the full disposable suite; the daily
BFF image still runs the older version until an explicit deployment.
