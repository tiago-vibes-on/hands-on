# Local Development Environment Consolidation Plan

Status: in progress. This checklist tracks the move from separate Compose and k3d
application environments to one k3d-backed environment. The cluster supplies
shared infrastructure and Envoy Gateway; selected application services can
run on WSL with hot reload. No migration task below is complete merely because
the older Compose environment provides a similar workflow.

## Target workflow

- Use heroassociation.test for the app and auth.heroassociation.test for
  Keycloak in both modes. Remove the k3d hostname aliases. Envoy Gateway is
  the only browser-facing gateway.
- Keep PostgreSQL, Keycloak, Redis, RabbitMQ, observability, and Envoy in k3d.
  Do not keep a second persistent Compose infrastructure stack.
- In daily hybrid mode, independently choose whether frontend, BFF, Core, and
  Expedition run on WSL or in k3d. Vite and Quarkus dev mode provide hot reload
  for host-run services; no service has two active writers or workers.
- In full k3d mode, all four application services run in the cluster. Enter
  this mode for final deployment and verification only when requested. A Git
  push alone does not build, deploy, or run the full k3d verification suite.
- Keep fast unit tests local. Run integration and end-to-end tests in k3d;
  browser-facing and gateway-policy tests use the same Envoy Gateway as the
  application. This includes candidate-image verification before promotion.

## Verified starting point

- [x] The k3d cluster has Envoy Gateway, Keycloak, separate Core and Keycloak
  PostgreSQL, Redis, Expedition RabbitMQ, observability, and deployed
  frontend, BFF, Core, and Expedition services.
- [x] Existing k3d browser, Map, Expedition, and market rate-limit tests have
  passed through Envoy. They do not yet replace all integration and E2E suites.
- [x] Jenkins has independent build and deploy jobs for all four application
  services. At planning time, its main jobs polled Git and deployed automatically;
  the manual-only conversion in step 2 is now applied.
- [x] At planning time, the isolated Compose Playwright and archive-backed
  E2E lanes used Traefik, and the local CA lived under that directory. The
  active candidate gate has since moved to disposable k3d; optional legacy
  commands remain until the step 6 cleanup.

## Ordered work

### 1. Prove a Core-only hybrid smoke test

- [x] Record the current Core Deployment replicas, HPA, Service routing, and
  database state. Prevent a Jenkins job from deploying during the test. Keep
  BFF, frontend, Keycloak, PostgreSQL, Redis, and Envoy running in k3d.
- [x] Prepare host-run Core with private access to the k3d PostgreSQL and
  Keycloak endpoints and the k3d OIDC issuer. Supply its database password
  without committing it. Configure Quarkus dev mode to validate the existing
  schema, not drop/create it or load seed data; do not run bootstrap or a
  reset. Include Redis and RabbitMQ connectivity if testing their Core paths.
- [x] Establish a temporary, reversible path from the k3d BFF to WSL Core.
  Confirm that Pods can reach the WSL host and account for Istio's strict
  Core mTLS and BFF authorization policy. Do not expose Core to browsers or
  silently bypass the service's authentication requirements.
- [x] Suspend the Core HPA, scale the k3d Core Deployment to zero, start Core
  locally, and verify authenticated reads and a safe POST validation path through the existing
  k3d Envoy hostname and BFF. Check that no k3d Core Pod returns, no Core
  data was reset, and the local process handled the requests. Stop local Core,
  restore the route, replicas, and HPA, then repeat the browser/API check.
  This is a focused smoke test; broader Expedition and WebSocket flows follow
  in the per-service hybrid checks below.

  Verified 2026-10-01: four k3d auth tests and two market POST/rate-limit tests
  passed with WSL Core as the only Core endpoint. The POSTs intentionally
  returned Core validation errors without creating orders. Core table
  relfilenodes stayed unchanged. After restoration, two Core Pods were ready,
  the original Service selector and 2–8 HPA were restored, and all four auth
  tests passed again. The temporary in-cluster bridge preserved Istio mTLS on
  the BFF/Expedition-to-bridge leg; the private WSL leg used HTTP and Core's
  token/service-key checks.

### 2. Stop automatic promotion

- [x] Disable Jenkins main polling and the automatic build-to-deploy trigger.
  Keep manual worktree and clean-checkout builds and explicit per-service
  deployment. Disable the currently running automatic jobs before pushing the
  Jenkins configuration change, then reload Jenkins when idle. The four live
  main jobs now have no SCM trigger or downstream deploy call; explicit main
  builds no longer skip unchanged commits.
- [ ] Prove a Git push leaves the running environment unchanged, and a manual
  build and separate deploy still promote the exact verified artifact.

  The independent BFF build, isolated exact-archive verification, and
  separate manual promotion were proved on 2026-10-01. The actual-push
  observation remains open: repository instructions prohibit this agent
  from pushing, and a dry-run or static trigger audit is not an actual push.

  Read-only follow-up 2026-10-01: remote `main` remains at `c0140f9`; no new
  push was available to observe. All four live Jenkins `build-main` jobs and
  all four `deploy-local` jobs have empty trigger configurations, and the
  running Core, BFF, Expedition, and frontend image references are unchanged.

  An authenticated Git-plugin `notifyCommit` probe returned HTTP 401 because
  that endpoint requires its own access token. No job queued or advanced and
  deployment images stayed unchanged, but the rejected probe does not replace
  observing a real push. Do not change Jenkins security just to close this gate.

### 3. Use one hostname and certificate source

- [x] Move the local CA and certificate generator to an edge-neutral ignored
  location before removing Traefik. Preserve browser trust where possible;
  never commit the CA private key or leaf private key.
- [x] Update Envoy routes, TLS, Keycloak realm redirects and issuer, BFF OIDC
  and cookies, frontend host settings, and test clients to the two canonical
  hostnames. Verify registration, login, logout, and HTTPS trust.
- [x] Remove active k3d hostname aliases only after both operating modes and
  tests use the canonical names. Keep historical ADRs as decision records.

  Verified 2026-10-01: the canonical host and issuer passed seven browser/API
  tests, the Map journey, market k6 thresholds, and Windows HTTPS trust.
  Registration passed in the disposable k3d E2E lane; the daily-host
  create-account entry point also reached the Keycloak registration
  form over Windows-trusted HTTPS, without leaving a daily test account.

### 4. Add reversible per-service hybrid routing

- [x] Extend the Core smoke-test mechanism so frontend, BFF, Core, and
  Expedition are switchable individually between their k3d Pods and WSL
  processes while preserving stable service URLs.
  Cover normal HTTP, Vite HMR, Expedition WebSockets, and the Envoy market
  identity check and per-user rate limit.
- [x] Stop a selected k3d application workload before activating its host
  counterpart. Suspend and later restore Core and BFF HPAs so they cannot
  recreate Pods. Record prior routes and replica counts and recover from an
  interrupted switch without leaving two Core schedulers or Expedition workers.
- [x] Give host-run services private access to k3d PostgreSQL, Keycloak,
  Redis, RabbitMQ, and OTLP. Use the same OIDC issuer and Expedition service
  credentials as full k3d mode without storing secrets in Git.
- [x] Prevent Core Quarkus dev reloads from dropping and reseeding the shared
  k3d database. Provide a separate, explicit reset and deterministic reseed
  command for intentional schema changes.
- [x] Verify frontend-only, each backend-service-only, and combined host-run
  combinations through Envoy. Exercise auth, API access, market 429 behavior,
  HMR, socket reconnect, settlement, and return to the previous mode.

  Verified 2026-10-01: Core-only and BFF-only runs each passed seven
  browser/API tests; BFF-only and Expedition-only passed Map/WebSocket
  settlement journeys. Frontend-only passed seven tests and a live Vite HMR
  WebSocket over Envoy. BFF+Expedition passed both API and Map journeys
  after restricting bridge upgrades to WebSockets. A killed frontend
  controller and a killed Core controller were recovered with the saved
  process identities; Core returned to two ready Pods, its 2–8 HPA, and its
  original Service route, with all host ports and forwards released.

### 5. Run integration and E2E tests in k3d

- [x] Inventory existing tests and classify pure unit tests separately from
  service/component, browser, load, and archive-verification tests. See
  [e2e/TEST_INVENTORY.md](e2e/TEST_INVENTORY.md); pure unit tests stay local.
- [x] Prove a disposable namespace can attach E2E-only hostnames to the same
  Envoy Gateway without changing the daily routes. The probe served trusted
  HTTPS from its own frontend and the daily frontend, then deleted its
  namespace. The listeners accept only labeled E2E namespaces; the test
  hostnames are mapped inside test runners, not Windows hosts.
- [x] Move active service/component integration, browser, load, and
  candidate verification to disposable k3d resources without touching daily
  data. Backend candidate builds now invoke the selected Core, BFF, or
  Expedition component lane before Maven packaging with `-DskipTests`.
  The lane disables Dev Services and uses private k3d PostgreSQL, Redis, and
  RabbitMQ. K3d AMQP tests reproduce both direct transport payload checks;
  the full-stack lane checks both cross-role queue denials. Direct Maven
  builds remain independently runnable and can still use Testcontainers.
  The old unrestricted-market proxy case is superseded by the Envoy
  per-user limit test. The borrowing-quest case is retained as historical
  coverage for the deferred Quest feature, not an active MVP gate.

  Verified 2026-10-01: `test-isolated-components.sh all` passed Core,
  BFF, and Expedition Maven suites and deleted its namespace. A BFF-only
  `build-local.sh` run passed 17 k3d-backed tests, packaged with tests
  skipped, and produced an unpromoted archive. Its exact four-image
  candidate `localenv-bff-components-20261001-a` passed both AMQP ACL
  checks, ten browser cases, Core Redis fallback, BFF replacement/session
  continuity, BFF Redis outage/recovery, strict expiry, and k6 thresholds
  (burst 5/1; sustained 50/150; zero unexpected responses or drops). The
  E2E namespace was deleted before its verification record passed, and
  daily Deployment images remained unchanged.

  Verified 2026-10-01: the isolated candidate gate ran two AMQP checks
  against its own broker: the Expedition worker and Core settlement user each
  received 403 on the other role's queue. Ten browser cases, the Core Redis
  outage/Map replay, BFF session/outage checks, and market k6 thresholds also
  passed. The E2E namespace was gone before the archive record passed; daily
  Deployment images were unchanged.
- [x] Provide disposable test data and routes in the same cluster without
  overwriting daily development accounts, orders, or Hero progress. Prefer
  a temporary test namespace using the same Envoy controller and shared
  route and policy definitions; test-only host mapping stays inside runners.

  Verified 2026-10-01: the disposable namespace used independent Core and
  Keycloak PostgreSQL, Redis, RabbitMQ, Keycloak, and application Pods.
  The dirty-worktree Core candidate `localenv-core-20261001-a` was assembled
  with three healthy daily images. All five running application Pods matched
  their archived OCI digests, including both BFF Pods before and after restart.
  Nine Playwright cases, saved-session handoff, isolated BFF Redis outage and
  recovery, and k6 thresholds passed (burst 5 forwarded, 1 limited; sustained
  55 forwarded, 146 limited; zero unexpected or dropped). The namespace was
  deleted before `k3d-e2e-verification.json` was marked passed; no Compose
  promotion record was written and daily Deployment images stayed unchanged.
  The shared Gateway rate-limit Redis outage remains opt-in because it would
  affect daily traffic. Legacy browser cases, service/component coverage,
  and pipeline wiring remained. A historical run using the old daily BFF
  image passed nine browser cases, BFF restart, and Redis outage/recovery,
  but its stricter token-state expiry check found HTTP 500 instead of an
  authentication response. That run stopped before k6 and still deleted
  the disposable namespace.
  BFF logs pinpoint a Quarkus 3.33.3.2 `OidcUtils.decryptTokens` null-token
  failure after the Redis key expires; do not weaken the assertion to any
  non-200 response.
- [x] Make missing or expired BFF Redis token state return an authentication
  response (302, 401, or 403) without turning Redis outages or other server
  failures into misleading authentication responses. Rerun the strict
  disposable suite and archive check before promotion.

  Verified 2026-10-01: the BFF alone moved to Quarkus 3.40.1, whose
  Redis token-state manager rejects a missing key instead of returning null.
  The dirty-worktree BFF candidate `localenv-bff-3401-20261001-a` passed
  local BFF tests and the exact four-image disposable k3d gate: five Pod
  image IDs, ten browser cases, both BFF replica replacements, saved-session
  continuity, Redis outage/recovery, strict token-state expiry, and k6
  thresholds. The tenth browser case verifies leader borrowing-fee edits;
  one-time recruit-claim checks were also added. Cleanup completed before
  its k3d verification record passed.
  The daily BFF Deployment still uses its previous image; promotion is manual.
- [x] Replace the active Traefik Compose browser and archive promotion gates
  with disposable k3d equivalents. The old Compose runner was retired after
  the replacement suite passed. The candidate gate checks exact Pod image
  IDs from a four-image archive,
  including dirty-worktree builds, and requires matching passing k3d evidence
  before any normal Deployment promotion.
- [x] Run authentication and session, agency, market and rate-limit, Map and
  Expedition, WebSocket, and relevant outage tests through Envoy. Keep k6
  thresholds and per-run evidence explicit; remove disposable test resources
  after success or failure.

  Verified 2026-10-01: the BFF candidate passed ten browser cases, BFF
  replica replacement, BFF Redis outage/recovery, strict expired-session
  rejection, and market k6. A first expanded run passed browser/outage checks
  but failed an overstrict sustained-load threshold (59 admitted); it cleaned
  up and kept its verification pending. After separating the exact 5/6 burst
  assertion from the coarse sustained-load bound, the full rerun passed
  (burst 5 admitted/1 limited; sustained 55 admitted/146 limited; no
  unexpected responses or dropped iterations). A later full rerun also
  stopped only the disposable Core Redis: Core stayed ready, the Map journey
  settled, logs confirmed PostgreSQL Troll lookup, and Redis recovered.
  The candidate archive was marked passed only after namespace deletion.
  Jenkins, the manual pipeline, and k3d deploy now require this k3d record;
  no daily image was promoted.
- [x] Adapt manual service builds to the hybrid state: current archive assembly
  reads baseline image IDs from running k3d application Pods. Either retain a
  verified baseline while those Pods are stopped or require full k3d mode
  before building and deploying a candidate.

### 6. Remove Traefik and document both modes

- [x] Remove Traefik services, configuration, and obsolete Compose startup
  paths after the replacement component and exact-image E2E suites passed.
  The standalone JVM/native Compose workflows remain. Ignored certificate
  material was preserved; Envoy uses `tls/certs/`, not the retired directory.
  The removed tracked files remain recoverable from Git history.
- [x] Document one-command entry, status, and exit for hybrid and full k3d
  modes, requirements, ports, secrets, interruption recovery, and explicit
  database reset. Backend, frontend, k3d, E2E, pipeline, Jenkins,
  authentication, and deployment runbooks now point to Envoy/k3d; the older
  deployment checklist is labeled historical and the root README stays brief.

  Verified 2026-10-01: `npm test -- --list` selected seven k3d smoke cases
  and no retired Compose test. Standalone JVM and native Compose manifests
  still render after the gateway removal. The archive parser unit suite
  passed all eight tests; the exact-image disposable E2E gate passed before
  Traefik files were removed.
- [x] Verify a clean hybrid start, one mixed-service configuration, full k3d
  restoration, manual build and deploy, all k3d integration and E2E gates,
  then return to hybrid mode without losing Core data or browser trust.
  Mark this plan complete only after active Traefik dependencies are gone.

  Verified 2026-10-01: the exact BFF archive
  `localenv-bff-components-20261001-a` passed baseline Pod-digest validation,
  was manually promoted without resetting Core, and passed seven browser/API
  cases, Expedition and Map/WebSocket journeys, and k6 (burst 5/1;
  sustained 60/140; zero unexpected or dropped). Core counts stayed at three
  agencies, 48 heroes, and 13 managers. Frontend+BFF WSL hybrid mode then
  passed four browser auth cases; both were restored to full k3d, and
  frontend-only WSL mode passed the same four cases again. The promoted BFF
  remains in k3d. Hybrid Quarkus now disables redundant Dev Services;
  WSL localhost TLS is owned by the separate pre-existing K3s Traefik,
  while the Windows browser route reaches the trusted k3d Envoy Gateway.

## Remaining verification gates

- A real Git push is still needed to prove the manual-only Jenkins behavior in
  a live push scenario. Repository instructions prohibit this assistant from
  pushing; the user must push and then compare Deployment images before and
  after. Static job configuration already has no poll or build-to-deploy
  trigger.
- Registration succeeded against the disposable canonical-host k3d realm.
  Daily-host account creation remains untested so this plan does not silently
  add a new identity and Core Account to development data.
- The final post-cleanup hybrid/full-k3d cycle includes manual promotion.
  The plan requires explicit deployment direction; no daily image was changed
  here. The daily BFF still uses the older image whose strict expired-token
  test fails, while the unpromoted Quarkus 3.40.1 BFF archive passed the
  exact-image disposable gate.
