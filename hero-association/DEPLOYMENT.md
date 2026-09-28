# Local Delivery and Scaling Plan

This checklist tracks the completed Traefik local migration and isolated k3d
lab. Checkboxes reflect verified work; a manual k3d browser check remains.

## Agreed scope

- Keep normal local development independently runnable and preserve its
  existing data while building the k3d lab.
- Keep Traefik as the normal Docker Compose edge; use Envoy Gateway as the
  browser-facing ingress in k3d. Disable K3s's bundled Traefik there, so only
  Envoy Gateway owns the k3d public routes.
- Add Istio to the isolated k3d lab as a sidecar service mesh for BFF and
  Core traffic; do not install an Istio ingress gateway in the first iteration.
- Test independent autoscaling of the BFF and Game Core, each with a minimum of
  2 and a maximum of 8 Pods. These are Pod counts, not k3d node counts.
- Leave the existing WSL K3s installation untouched. Use a separate k3d
  cluster for this project.
- Defer Floci/EKS integration, multi-AZ behavior, and production deployment.

## Phase 1 — Replace Caddy and verify the edge

- [x] Replace Caddy with Traefik in the normal hot-reload infrastructure and
  packaged Docker Compose workflows. Preserve `heroassociation.test` and
  `auth.heroassociation.test`, same-origin `/api` and `/auth` routing, local
  HTTPS, trusted browser certificates, and the Keycloak login/logout flow.
  Prefer a file-based Traefik configuration so this migration does not grant
  the gateway access to the Docker daemon socket.
  The local CA was imported into the Windows current-user root store on
  2026-09-27; Windows HTTPS checks validate the local hostnames.
- [x] Extend the isolated Playwright stack to reach the application through
  Traefik and HTTPS. The browser uses the gateway's two HTTPS hostnames while
  retaining separate Compose ports, databases, and Redis state.
- [x] Run the full browser E2E suite (`npm test` from `e2e/`) after the migration.
  Verify registration, login, logout, login again, API routing, and the shared
  session across two BFF instances. Run relevant service tests as well.
- [x] Remove obsolete Caddy files and the old Caddy CA from Windows trust.
  Update local development, authentication, and E2E docs. Manual Windows
  browser verification of the separate k3d lab remains a follow-up below.

## Phase 2 — Create an isolated k3d application lab

### First k3d application deployment

- [x] Build and test the JVM Core and BFF images, tag them for this local lab,
  and import the images into the isolated k3d cluster.
- [x] Provision separate k3d PostgreSQL databases for Core and Keycloak,
  Redis for BFF sessions, and a Keycloak realm/theme with k3d-only OIDC URLs.
  Generate ignored lab-only secrets; never reuse normal development data.
- [x] Deploy one Core and one BFF Pod with private Services, health probes,
  resource requests, and opt-in Istio sidecars. Keep Core on a `Recreate`
  rollout pending two-Pod correctness and restart tests.
- [x] Route BFF `/api` and `/auth` and the Keycloak hostname through the
  existing Envoy Gateway, using the k3d HTTPS port in OIDC callbacks.
- [x] Verify rollouts, seeded Core data, BFF session and login redirects,
  Gateway routing, and normal-development isolation. Document build/deploy,
  restart, and cleanup commands.
- [x] Build and deploy the frontend separately to complete the browser flow;
  run k3d browser E2E tests before calling the application deployment done.

The isolated k3d application was verified on 2026-09-27: one server and two
agents are Ready, and Istio 1.30.5 and Envoy Gateway 1.9.1 are healthy. Core,
BFF, Keycloak, PostgreSQL, Redis, and the frontend are Ready at one replica
each. HTTPS serves the app and preserves the BFF `/api` and `/auth` routes.
Two k3d Playwright tests passed against seeded users, including login, logout,
account identity, and an agency-state API read. Windows current-user CA trust
is installed; manual k3d browser verification remains a separate task.

The disposable observability Pod is also Ready: its OpenTelemetry receiver,
Prometheus, Loki, Tempo, and Grafana endpoints passed local smoke checks.
BFF and Core now emit traces, metrics, and logs to it in the k3d lab.

- [x] Create a versioned k3d cluster configuration with one server and two
  agents, distinct API/Ingress ports, and an isolated kubeconfig that does not
  switch the existing WSL K3s context. Document start, stop, and cleanup.
- [x] Install pinned Istio 1.30.5 in minimal mode. Verify `istiod` readiness
  and a connected, Ready opt-in sidecar without meshing the whole namespace.
- [x] Install pinned Envoy Gateway 1.9.1 and Gateway API CRDs, replacing the
  initial k3d Traefik controller. Configure a TLS Gateway and HTTP redirect
  on the isolated ports; verify a temporary HTTPS backend route and remove it.
- [x] Build and import the frontend image; add its non-meshed Deployment,
  private Service, and Envoy Gateway root route without changing normal Vite
  development. Keep the BFF and Core images reusable for the later AWS deploy.
- [x] Run k3d-specific Playwright coverage for seeded users: login, logout,
  login again, account identity, and an authorized agency-state API read. The
  test browser ignores the untrusted local CA warning only for this run.
- [x] Import the local CA into the Windows current-user root store. Windows
  HTTPS checks validate both k3d hostnames with offline revocation best-effort.
- [ ] After restarting the Windows browser, manually verify the same k3d
  login flow without a certificate warning.
- [x] Confirm BFF-to-Core traffic uses Istio mutual TLS, enforce STRICT mTLS
  only on Core, and rerun k3d browser tests. A direct request from the
  non-meshed frontend Pod is rejected while Envoy-to-BFF remains available.
  Record an initial per-container CPU/memory snapshot, including sidecars.
- [x] Give BFF and Core distinct Kubernetes ServiceAccounts and add a
  Core-scoped AuthorizationPolicy that allows only BFF's meshed identity.
  Verify BFF receives 200, another meshed identity receives 403, and the k3d
  browser tests still pass. Core's `Recreate` rollout reseeded lab data.
- [x] Measure sustained read load and tune resource requests before enabling HPAs.
  A 60-second, four-client agency-state run using one login returned 1,932/1,932
  HTTP 200 responses at 32.2 requests/second, with 33 ms p95 latency.
  Sampled Core reached 496m CPU / 238Mi memory plus up to 84m / 43Mi
  for its Istio sidecar. BFF reached 272m / 166Mi plus up to 31m / 39Mi.
  Lab requests are now 500m CPU / 384Mi for Core and 300m / 256Mi for BFF.
  This read-only baseline is not a mixed-workload capacity limit.
- [x] Keep real secrets out of Git; provide templates or documented local
  Secret creation instead of committing passwords or signing keys.

## Phase 2a — Observe the lab

- [x] Install an isolated, private local OpenTelemetry Collector, Prometheus,
  Loki, Tempo, and Grafana stack. Pin the image, use an ignored generated
  Grafana password, and verify readiness and OTLP acceptance. Document the
  requirements, local build, access, and disposable storage limitations in
  [`deploy/k3d/README.md`](deploy/k3d/README.md).
- [x] Instrument BFF and Core with OTLP traces, HTTP/JVM metrics, and
  structured logs. BFF's custom HTTP client propagates W3C trace context
  to Core. k3d browser tests passed; a Tempo trace contains BFF, client,
  and Core spans. Prometheus has both services' HTTP metrics, Loki has
  their structured logs, and all three Grafana data sources report healthy.
  Auth and raw agency-ID paths are excluded from traces; Redis connection
  strings and bearer tokens are not added to spans or logs.
- [x] Collect Istio sidecar and Envoy Gateway metrics and logs, define useful
  labels and retention limits, and build dashboards for request latency,
  errors, Pod resources, and 2-to-8-Pod scaling runs. Three node agents are
  Ready. Prometheus has mesh, Gateway, and Pod CPU/memory series; Loki has
  sanitized Envoy requests and Istio diagnostics without raw request paths.
  Prometheus, Loki, and Tempo use 24-hour retention in this disposable lab;
  the Traffic and Scaling dashboards are provisioned in Grafana. The k3d
  browser tests pass (2/2). Actual multi-Pod scaling remains a later phase.

## Phase 3 — Make Game Core safe for multiple Pods

- [x] Move schema creation and deterministic seed data out of normal Core
  startup. A one-shot k3d Job creates and seeds an absent database; ordinary
  Core Pods validate the schema and preserve data. An explicit
  `deploy-backend.sh --reset-core-db` resets only this disposable lab's Core
  database. Dev/test and packaged Compose keep their deliberate reset behavior.
  Verified with a temporary database, an unchanged table identity and row
  count after validation and Core restart, and the k3d browser suite (2/2).
- [x] Coordinate combat progression and agency recovery across Core Pods.
  Separate transaction-scoped PostgreSQL advisory locks let one Pod run each
  job while another skips; the next tick catches up from persisted timestamps.
  Testcontainers verifies competing transactions, skip/resume for both jobs,
  independent locks, and release after rollback.
- [x] Run concurrency and restart tests with two Core Pods sharing one
  PostgreSQL database. The isolated k3d test puts the Pods on distinct nodes,
  verifies recovery against elapsed time and combat event continuity, restarts
  a Pod during combat, forces one quest resolution, then restarts a Pod again
  and verifies that the resolved quest and event count remain unchanged.
  The test database and Pods are removed afterward; live Core stayed at one
  replica until the Phase 4 scaling configuration was introduced.

## Phase 4 — Autoscaling and load tests

- [x] Configure separate `autoscaling/v2` HPAs for BFF and Core, each
  with `minReplicas: 2`, `maxReplicas: 8`, and an initial 60% Pod-CPU target
  from K3s Metrics Server. Pod CPU includes Istio sidecar overhead. Both use
  rolling updates and a 15-second drain before termination; an explicit
  Core database reset removes its HPA before stopping Pods. PostgreSQL,
  Redis, and Keycloak are not autoscaled.
- [x] Add a repeatable authenticated read-load and rollout test. A 90-second,
  16-client run returned 9,344/9,344 HTTP 200 responses at 103.7 requests/s
  with 96 ms p95 and 206 ms p99 while BFF rolled. BFF reached six Pods,
  Core eight, and both returned to two; four k3d browser tests passed after
  scale-in. Early runs exposed brief 503s during Pod turnover, resolved by
  the drain window.
- [x] Exercise active quest/combat and agency recovery while an isolated Core
  deployment scales from two to four to eight Pods on three k3d nodes. A
  20-transaction/s PostgreSQL read workload runs against the same temporary
  database while Core writes. The run completed 3,640 read transactions with
  zero failures, and verified elapsed-time recovery, combat event continuity,
  one quest resolution, and active/post-resolution Pod restarts. This validates
  shared-database concurrency, not authenticated API writes.
- [x] Measure a bounded mixed API workload against an isolated k3d BFF/Core
  stack and temporary Core database. With 90% authenticated state reads and
  10% activity writes, the staged 2-8-Pod HPA run passed 64 clients at 247.2
  requests/s with zero errors. In a warmed fixed-eight-Pod run, 64 clients
  passed at 251.5 requests/s; 128 clients reached 301.7 requests/s but
  breached the illustrative 500 ms write-p95 target (581 ms). These are
  workload- and warm-up-dependent lab observations, not a universal maximum.
  All k3d nodes share one computer, so this does not prove physical-node or
  multi-AZ resilience. See `deploy/k3d/CAPACITY.md` for method and caveats.
- [x] Roll the Redis-backed market limiter into the running k3d BFF without
  restarting Core or resetting its database. Both BFF Pods started from the
  imported image, and five k3d browser tests passed through Envoy Gateway.
  The new test sent six concurrent invalid market-order attempts from two
  sessions of one user: five reached Core validation, one received `429`,
  while another user's request remained independent.
- [x] Move k3d browser ingress to host ports 80/443, exclusive with local
  Compose. Recreated only the disposable k3d lab and restored Istio, Envoy,
  observability, and seeded application data on 2026-09-28. Windows HTTP to
  HTTPS, frontend, BFF, and Keycloak checks passed; k3d Playwright passed 5/5
  and k6 thresholds passed. A stop/start switch preserved local Compose data.
