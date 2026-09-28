# Isolated k3d lab

This lab is separate from normal Docker Compose development, the existing WSL
K3s installation, and the later Floci/AWS lab. The first versioned cluster
and Istio control-plane configurations are in `cluster.yaml` and
`istio-operator.yaml`. Application deployment is tracked in
[`../../DEPLOYMENT.md`](../../DEPLOYMENT.md).

## Requirements

Use Docker Desktop with WSL integration (or a Linux Docker engine). Allow roughly
8 GiB of RAM for the cluster, Istio, Envoy Gateway, and the observability
stack. Install Docker Compose, `kubectl`,
[`k3d` v5.9.0](https://github.com/k3d-io/k3d/releases/tag/v5.9.0),
[`istioctl` 1.30.5](https://istio.io/latest/docs/setup/additional-setup/download-istio-release/),
`curl`, `openssl`, and `sha256sum` in the same environment as Docker. The Envoy
installer downloads a checksum-pinned manifest and requires network access.
Java 25 and Node.js 24/npm 11 are needed for building the application; Node.js
is also used to derive the k3d-only Keycloak realm during backend deployment.

If the pinned CLIs are cached in this checkout's ignored `.tools/` directory,
run `export PATH="$PWD/.tools:$PATH"` from `deploy/k3d` before the commands
below. Otherwise install `k3d` and `istioctl` on your normal `PATH`.

The config pins K3s 1.34.7 because this Docker Desktop engine exposes cgroup
v1 and Kubernetes 1.35's kubelet does not start on cgroup v1 by default. It
creates one server and two agents, disables K3s's bundled Traefik, and reserves
host ports `16550` for the Kubernetes API, `80` for HTTP, and `443` for
HTTPS. It does not modify the default kubeconfig or switch the current context,
protecting the existing WSL K3s context.

## Create the cluster

Run the following from this directory (`hero-association/deploy/k3d`):

```bash
(cd ../../backend && docker compose -f compose.infra.yaml down)
k3d cluster create --config cluster.yaml
k3d kubeconfig get hero-association > .kubeconfig
chmod 600 .kubeconfig
KUBECONFIG="$PWD/.kubeconfig" kubectl get nodes
```

The kubeconfig is ignored by Git. Pass it explicitly to every `kubectl`,
`istioctl`, and Helm command for this lab. `k3d cluster stop hero-association`
pauses only this lab; `k3d cluster start hero-association` resumes it. Deleting this
lab with `k3d cluster delete hero-association` permanently removes its own
cluster data—never use that command to reset normal development.

For an existing cluster created with the old `19080`/`19443` bindings,
changing `cluster.yaml` does not update its Docker port mappings. Stop local
Compose, run `k3d cluster delete hero-association`, then repeat the creation
and deployment steps below. This
recreates the isolated k3d databases and telemetry; normal Compose volumes
are untouched.

For everyday switching, run these commands from this directory. Stop any
host-run Quarkus/Vite processes separately if they are still running. To use
k3d after local development:

```bash
(cd ../../backend && docker compose -f compose.infra.yaml down)
k3d cluster start hero-association
```

To return to local development without deleting k3d data:

```bash
k3d cluster stop hero-association
(cd ../../backend && ./scripts/start-infra.sh)
```

If the other gateway still owns port 80 or 443, the new environment will not
start; stop the other environment first. Do not use `down --volumes` to switch.

Keep normal development's hosts entries and add the two k3d names to the
Windows hosts file (`C:\Windows\System32\drivers\etc\hosts`) or the Linux
hosts file (`/etc/hosts`). Entries contain names, not URL schemes:

```text
127.0.0.1 heroassociation.test
127.0.0.1 auth.heroassociation.test
127.0.0.1 k3d.heroassociation.test
127.0.0.1 auth.k3d.heroassociation.test
```

The k3d URLs are `https://k3d.heroassociation.test` and
`https://auth.k3d.heroassociation.test`. Both use standard HTTPS port 443.
Normal Compose development uses the same host ports, so stop its gateway
before starting k3d; only one environment can own ports 80 and 443 at a time.
The k3d leaf certificate covers both hosts and is signed by the same local
development CA.
Trust the CA in the Windows current-user store as described in the
[backend README](../../backend/README.md#local-https-gateway); otherwise the
browser may show a warning. Routes are installed by their deployment scripts.

On WSL with the separate, pre-existing K3s installation, WSL `curl` to
`127.0.0.1:80` or `:443` may reach K3s's Traefik instead of Docker Desktop's
k3d port forwarding. Verify these URLs from the Windows browser or Windows
`curl.exe` (with `--ssl-revoke-best-effort` for the offline development CA),
or run the containerized E2E tests. Do not stop the separate WSL K3s service.

Envoy Gateway owns browser ingress in k3d. Istio is separate: workload-level
sidecars will observe and secure BFF-to-Core traffic. There is no Istio ingress
gateway, and the namespace is not globally labeled for injection. Core mTLS
is enforced after probe and identity checks; separate CPU HPAs now manage
two to eight BFF and Core Pods.

Install the pinned Istio 1.30.5 `istioctl` binary, then install only its
control plane using the isolated kubeconfig:

```bash
KUBECONFIG="$PWD/.kubeconfig" istioctl install -f istio-operator.yaml --skip-confirmation
KUBECONFIG="$PWD/.kubeconfig" kubectl -n istio-system rollout status deploy/istiod
```

Install pinned Envoy Gateway 1.9.1 and Gateway API CRDs, then create this
project's HTTPS Gateway. The installer checks the isolated kubeconfig and a
checksum of the official pinned manifest. The Gateway script generates a
separate ignored leaf certificate, creates a Kubernetes TLS Secret, and
applies the versioned resources under `../k8s/`:

```bash
../../traefik/generate-local-certs.sh
./install-envoy-gateway.sh
./install-gateway.sh
KUBECONFIG="$PWD/.kubeconfig" kubectl -n hero-association wait --for=condition=Programmed gateway/hero-association --timeout=150s
```

The Gateway listens through k3d's mapped ports `80` and `443`. HTTP
redirects to HTTPS on port `443`. `deploy-backend.sh` adds the BFF `/api` and
`/auth` routes plus Keycloak's hostname; `deploy-frontend.sh` adds the app
root route. More-specific BFF paths continue to reach BFF.

To repeat the HTTPS backend-routing smoke test, apply the temporary resources,
check the response, and remove them immediately:

```bash
KUBECONFIG="$PWD/.kubeconfig" kubectl apply -f gateway-smoke.yaml
KUBECONFIG="$PWD/.kubeconfig" kubectl -n hero-association rollout status deploy/gateway-smoke
curl --cacert ../../traefik/certs/local-ca.crt --resolve 'k3d.heroassociation.test:443:127.0.0.1' https://k3d.heroassociation.test/__gateway_smoke
KUBECONFIG="$PWD/.kubeconfig" kubectl delete -f gateway-smoke.yaml
```

The expected body is `envoy-gateway-ok`. BFF and Core opt into Istio
sidecar injection in the backend manifests; the frontend,
Keycloak, PostgreSQL, and Redis will remain outside the mesh initially.

## Observability

Run the installer after creating the cluster. It installs the pinned
[Grafana OTel LGTM development image](https://github.com/grafana/docker-otel-lgtm)
in `hero-association-observability`: OpenTelemetry Collector, Prometheus,
Grafana Loki, Grafana Tempo, and Grafana in one Pod. Its Service is private to
the cluster; it does not use the browser-facing Envoy Gateway. The installer
generates a random Grafana admin password in the Git-ignored `certs/` directory.

```bash
./install-observability.sh
KUBECONFIG="$PWD/.kubeconfig" kubectl -n hero-association-observability get pods,svc
```

To inspect the lab from the host, keep this command running in another
terminal, then open `http://127.0.0.1:13000` and log in as `admin` with the
password in `certs/grafana-admin-password`:

```bash
KUBECONFIG="$PWD/.kubeconfig" kubectl -n hero-association-observability port-forward --address 127.0.0.1 svc/otel-lgtm 13000:3000 13100:3100 13200:3200 14318:4318 19090:9090
```

Prometheus is then at `http://127.0.0.1:19090`, Loki at port `13100`, and
Tempo at port `13200`; the OTLP/HTTP receiver is at
`http://127.0.0.1:14318/v1/traces` (also `/v1/metrics` and `/v1/logs`). For
future in-cluster workloads, use
`http://otel-lgtm.hero-association-observability.svc.cluster.local:4318` for
OTLP/HTTP or port `4317` for OTLP/gRPC. Grafana's included data sources point
to the bundled Prometheus, Loki, and Tempo instances.

With the port-forward running, check the bundled services:

```bash
curl --fail http://127.0.0.1:13000/api/health
curl --fail http://127.0.0.1:13100/ready
curl --fail http://127.0.0.1:13200/ready
curl --fail http://127.0.0.1:19090/-/ready
```

This is a disposable learning setup, not a production observability topology.
The storage volume is `emptyDir`, so Pod recreation loses telemetry. BFF and
Core export OTLP/gRPC traces, HTTP/JVM metrics, and structured logs to
the private Collector. Packaged JVM containers also log JSON to stdout.
A per-node OpenTelemetry Collector DaemonSet scrapes selected Istio sidecar
and Envoy Gateway metrics and Pod CPU/memory for the application and Gateway
namespaces. Its read-only log mount collects Istio diagnostics and Envoy
proxy access logs. Istio access lines are dropped; Envoy access lines are
reduced to method, status, route name, duration, and flags before export.
These access-log pipelines do not send raw request paths, client IPs, or
request IDs to Loki. Diagnostic messages may still contain arbitrary text;
do not treat this local pipeline as a production redaction policy.
Only service, namespace, and container are indexed as Loki labels; other
fields remain structured metadata.

The lab retains Prometheus, Loki, and Tempo data for 24 hours. Prometheus
also has a 2 GiB block limit, with a shared 4 GiB `emptyDir` for the stack.
Replacing the LGTM Pod erases its entire telemetry history immediately,
regardless of retention. Re-running the installer restarts collector agents
to pick up ConfigMap changes but does not recreate LGTM when its config is
unchanged. BFF/Core OTLP log export supplies their Loki records.

## Verify application telemetry

From `hero-association/e2e`, run `npm run test:k3d` to generate browser
traffic. With the Grafana port-forward above running, open
`http://127.0.0.1:13000` and use Explore:

- Tempo: search for `service.name=hero-association-bff`. A recruit or account
  request should contain BFF's server span, its `game-core.request` client
  span, and Core's server span in one trace.
- Prometheus: query
  `sum by (service_name, uri, status) (http_server_requests_milliseconds_count{service_name=~"hero-association-(bff|core)"})`.
  Both services should return route-templated HTTP counts.
- Loki: query `{service_name=~"hero-association-(bff|core)"}` for structured
  startup logs from both services.

The **Hero Association** Grafana folder provisions two dashboards:

- `http://127.0.0.1:13000/d/hero-association-traffic/hero-association-traffic`
  shows request rate, p95 latency, and 5xx errors for BFF/Core, plus Envoy
  Gateway upstream traffic and BFF-to-Core mesh traffic.
- `http://127.0.0.1:13000/d/hero-association-scaling/hero-association-scaling`
  shows BFF/Core Pod count, Pod CPU, working memory, latency, and errors
  during 2-to-8-Pod scaling runs. The idle baseline is two Pods per service.
  Pod count is inferred from sampled resource metrics and may lag briefly
  during a rollout. Shared-database startup and jobs passed an isolated
  two-Pod test before the HPAs were enabled.

In Explore, query `istio_requests_total`,
`envoy_cluster_external_upstream_rq_total`, and
`k8s_pod_memory_working_set_bytes{namespace="hero-association"}` in Prometheus.
Query `{service_name="envoy-gateway-proxy"}` for sanitized proxy requests,
or `{service_name="istio-sidecar"}` for sidecar diagnostics in Loki.
Sidecar diagnostics appear on startup or when the proxy reports an event;
ordinary sidecar access lines are intentionally excluded.

The k3d manifests set `HERO_ASSOCIATION_OTEL_DISABLED=false` and point
`HERO_ASSOCIATION_OTLP_ENDPOINT` at the private Collector on port `4317`.
BFF uses explicit W3C trace-context propagation
for its Java HTTP client. Auth routes and raw agency-ID routes are excluded
from traces; Redis client spans are disabled so a connection URI cannot be
exported. No request body, token, email, or user name is added as a custom
telemetry attribute. HTTP metrics still use route templates. Normal
host-run development and Docker Compose have telemetry disabled by
default; see the backend README for opt-in instructions.

## Build the application locally

The normal hot-reload environment remains separate from this k3d lab. Follow
[`../../backend/README.md`](../../backend/README.md#local-development) for
PostgreSQL, Redis, Keycloak, Traefik, and the two Quarkus dev processes; follow
[`../../frontend/README.md`](../../frontend/README.md#run-locally) for Vite.
To test and create local production-style build artifacts, run from the
`hero-association` directory:

```bash
cd backend/hero-association-core && ./mvnw package
cd ../hero-association-bff && ./mvnw package
cd ../../frontend && npm ci && npm run lint && npm run build
```

The two Maven packages run service tests, including Core's Testcontainers
checks, so Docker must be available.

## Build and deploy the JVM backend

From `hero-association/deploy/k3d`, run these commands after installing Istio,
Envoy Gateway, and the HTTPS Gateway above. Docker, Java 25, Node.js 24, Maven
Wrapper prerequisites, and a running k3d cluster are required. The build script
runs both Maven test suites, builds the two JVM Docker images, and imports them
into k3d. If `k3d` is not on `PATH`, set `K3D_BIN=/absolute/path/to/k3d`.

```bash
./build-backend-images.sh
./deploy-backend.sh
```

The deploy script refuses any context except the isolated
`k3d-hero-association` context in `.kubeconfig`. It creates random credentials
in the ignored `secrets/` directory, derives a k3d-only Keycloak realm from the
versioned local realm, and deploys isolated Core and Keycloak PostgreSQL, BFF
Redis, Keycloak, Core, and BFF. Core and BFF have Istio-injected Pods and
private Services. If the Core schema is absent, the script first runs a
one-shot bootstrap Job using the Core image to create the schema and load
deterministic seed data. Normal Core Pods only validate the schema; redeploys
and restarts preserve the database. To deliberately discard this lab's Core
game data and reseed it, run `./deploy-backend.sh --reset-core-db`. The script
stops Core before the reset and leaves it stopped if bootstrap fails. This
flag does not reset Keycloak or Redis. The script temporarily removes the
Core HPA before an explicit reset, then restores it afterward. BFF and Core
use rolling updates and separate CPU HPAs with two to eight replicas. Both
scheduled jobs use distinct transaction-scoped PostgreSQL advisory locks, so
competing Core Pods skip a tick. An isolated correctness test now exercises
two, four, and eight Pods during combat and recovery.

The Envoy BFF route sets trusted forwarded host, scheme, and port headers so
Quarkus generates OIDC callbacks on the public HTTPS URL. The local
Compose environment and its volumes are not changed.

Check the Pods and browser-facing routes:

```bash
KUBECONFIG="$PWD/.kubeconfig" kubectl -n hero-association get pods,svc,httproute
curl --cacert ../../traefik/certs/local-ca.crt --resolve 'k3d.heroassociation.test:443:127.0.0.1' https://k3d.heroassociation.test/api/v1/session
curl --cacert ../../traefik/certs/local-ca.crt --resolve 'auth.k3d.heroassociation.test:443:127.0.0.1' https://auth.k3d.heroassociation.test/realms/hero-association
```

The frontend is served at `https://k3d.heroassociation.test`. The
imported test users include `user1@mail.com` / `user1`, `user2@mail.com` /
`user2`, and `manager1@mail.com` through `manager10@mail.com` with matching
`managerN` passwords, for this disposable lab only. See the
[test-data map](../../TEST_DATA.md) for agency roles.
The Keycloak admin username is `hero-association-admin`; its generated
password is in the ignored `secrets/KEYCLOAK_ADMIN_PASSWORD` file.

To rebuild after source changes, rerun both scripts and restart the two
Deployments so they use the newly imported fixed `:k3d` image tags:

```bash
KUBECONFIG="$PWD/.kubeconfig" kubectl -n hero-association rollout restart deploy/core deploy/bff
KUBECONFIG="$PWD/.kubeconfig" kubectl -n hero-association rollout status deploy/core
KUBECONFIG="$PWD/.kubeconfig" kubectl -n hero-association rollout status deploy/bff
```

For BFF-only changes, keep Core and its database running. Build, test, and
import just the BFF image, then restart only its Deployment:

```bash
./build-backend-images.sh bff
KUBECONFIG="$PWD/.kubeconfig" kubectl -n hero-association rollout restart deploy/bff
KUBECONFIG="$PWD/.kubeconfig" kubectl -n hero-association rollout status deploy/bff
cd ../../e2e && npm run test:k3d
```

The image builder also accepts `core` alone; with no argument it builds both.
On WSL with Docker Desktop, prefix the builder with
`TESTCONTAINERS_HOST_OVERRIDE=host.docker.internal` if its temporary test
container ports are not reachable through `localhost`.

## Test Core scaling under concurrent work

After building and importing the Core JVM image and deploying the backend, run
this isolated correctness test from `deploy/k3d`:

```bash
./test-core-concurrency.sh
```

The script checks the k3d context, creates a uniquely named temporary Core
PostgreSQL database, and seeds it with the one-shot bootstrap Job. It starts
two test-only Core Pods on different nodes, then scales them to four and eight
across the three k3d nodes. While Core writes combat and agency recovery, the
PostgreSQL Pod runs `pgbench` at 20 read-only transactions per second against
that same temporary database for 180 seconds; no host-side `pgbench` install
is needed. The test checks elapsed-time recovery, persisted combat time and
event sequence continuity at each size, restarts a Pod during combat, resolves
the quest once, and restarts a Pod again to check that the result stays fixed.

The script removes its test Pods, bootstrap Job, and temporary database on
exit. It does not modify the live Core database or deployment, and its manifest
is excluded from the normal backend Kustomization. A failed test prints Pod
diagnostics and returns nonzero. This is a shared-database concurrency check,
not a measurement of maximum capacity or authenticated API-write traffic.

## Core mesh policy

`deploy-backend.sh` creates distinct `hero-association-bff` and
`hero-association-core` ServiceAccounts and waits for their Pods before it
applies `../k8s/mesh/`. Core then requires STRICT mutual TLS and an Istio
`AuthorizationPolicy` allows only callers presenting the BFF ServiceAccount
identity. BFF remains reachable from Envoy Gateway. PostgreSQL, Redis,
Keycloak, and the frontend stay outside the mesh. Core still checks OIDC
user tokens for protected APIs; workload authorization is an additional layer.
Istio rewrites Core's HTTP probes, so kubelet health checks remain functional.

Core uses a rolling update with at least two replicas. Changing its
ServiceAccount or restarting a Pod preserves the Core schema. The separate
bootstrap Job is not part of normal Pod startup.

Check the policies, Pods, and per-container usage from this directory:

```bash
KUBECONFIG="$PWD/.kubeconfig" kubectl -n hero-association get serviceaccount,peerauthentication,authorizationpolicy
KUBECONFIG="$PWD/.kubeconfig" kubectl -n hero-association get pods
KUBECONFIG="$PWD/.kubeconfig" kubectl -n hero-association top pods --containers
```

After `npm run test:k3d`, port-forward Core's sidecar metrics in one terminal,
then query the request metric in another. The BFF source principal should
contain `/sa/hero-association-bff` and the connection policy should be
`mutual_tls`:

```bash
KUBECONFIG="$PWD/.kubeconfig" kubectl -n hero-association port-forward deployment/core 15091:15090
curl --silent http://127.0.0.1:15091/stats/prometheus | grep 'istio_requests_total{reporter="destination",source_workload="bff"' | grep 'connection_security_policy="mutual_tls"'
```

These two requests distinguish an allowed BFF identity from another meshed
identity. The BFF request returns 200; the Core Pod's request to its own
Service returns 403. A plaintext request from the non-meshed frontend Pod
fails at the STRICT mTLS layer with a connection reset:

```bash
KUBECONFIG="$PWD/.kubeconfig" kubectl -n hero-association exec deployment/bff -c bff -- wget -S -O /dev/null -T 3 http://core:8081/q/health/ready
KUBECONFIG="$PWD/.kubeconfig" kubectl -n hero-association exec deployment/core -c core -- wget -S -O /dev/null -T 3 http://core:8081/q/health/ready
KUBECONFIG="$PWD/.kubeconfig" kubectl -n hero-association exec deployment/frontend -c frontend -- wget -S -O /dev/null -T 3 http://core:8081/q/health/ready
```

If BFF-to-Core calls unexpectedly fail, remove only the allow policy with
`KUBECONFIG="$PWD/.kubeconfig" kubectl delete -f ../k8s/mesh/core-bff-authorization.yaml`;
Core will still require mutual TLS. The next `deploy-backend.sh` run reapplies
both mesh policies. On 2026-09-27, a brief idle/test snapshot showed BFF at
46m CPU / 136Mi memory plus a 7m / 40Mi sidecar, and Core at 3m / 223Mi plus
a 5m / 47Mi sidecar. This idle snapshot is not an HPA target.

## Market order rate-limit load check

From `hero-association/e2e`, run `npm run test:market:k6` after the JVM
backend and frontend are deployed. It signs in with three local-only browser
sessions, then uses the pinned k6 Docker image to verify that user 1 gets
five forwarded requests and one HTTP 429 in a burst, while user 2 keeps a
separate budget. A ten-second, 20-attempts-per-second scenario checks that
the same per-user limit continues to hold through Envoy Gateway and the BFF
replicas. Requests are intentionally invalid, so they cannot place orders.
See [the E2E README](../../e2e/README.md#measure-the-market-order-rate-limit-with-k6)
for prerequisites and expected results.

## Sustained read-load baseline

From `hero-association/e2e`, run `npm run load:k3d` while sampling
`KUBECONFIG="$PWD/../deploy/k3d/.kubeconfig" kubectl -n hero-association top pods --containers`
in a second terminal. The test signs in once and sends authenticated,
read-only agency-state GETs through Envoy Gateway, BFF, and Core.

On 2026-09-27, four clients over 60 seconds returned 1,932 HTTP 200
responses and no errors: 32.2 requests/second, p50 21 ms, p95 33 ms,
and p99 45 ms. Sampled container peaks during that run were:

| Container | CPU | Memory |
| --- | ---: | ---: |
| BFF | 272m | 166Mi |
| BFF Istio sidecar | 31m | 39Mi |
| Core | 496m | 238Mi |
| Core Istio sidecar | 84m | 43Mi |

Lab requests, tuned after an initial run, are 300m CPU / 256Mi memory
for BFF and 500m / 384Mi for Core. Istio sidecars retain their injected
100m / 128Mi requests. The original rollouts and two browser tests passed.
This was a preliminary read-only baseline used to choose initial HPA requests
and targets, not a mixed-workload capacity limit.

## Autoscaling and sustained validation

K3s Metrics Server supplies CPU samples for separate `autoscaling/v2` HPAs
named `bff` and `core`. Each holds a minimum of two and a maximum of eight
Pods with an initial 60% Pod-CPU target. Pod CPU includes the application and
its Istio sidecar, so include the sidecar's 100m CPU / 128Mi memory request
when interpreting utilization and host capacity. These are lab starting
values, not production sizing. The HPAs use a 60-second downscale
stabilization window and remove at most one Pod per 30 seconds. BFF and Core
use rolling updates with no unavailable replicas. Each application Pod waits
15 seconds in a `preStop` hook before exiting, within a 45-second termination
grace period, so in-flight requests can drain after the Pod leaves endpoints.

From `hero-association/deploy/k3d`, run the repeatable scaling check after
building/deploying the JVM backend and frontend:

```bash
./test-autoscaling.sh
```

It waits for two Ready BFF and Core Pods, runs 16 authenticated read clients
for 90 seconds, restarts BFF during the load, checks scale-out and zero HTTP
or transport errors, waits for both services to scale back to two, then runs
the k3d browser suite. Override the load with
`HERO_ASSOCIATION_K3D_LOAD_CLIENTS` (1–32) and
`HERO_ASSOCIATION_K3D_LOAD_SECONDS` (60–600). The script requires Docker,
Node/npm, kubectl, and the isolated kubeconfig; it does not reset Core game data.
A lighter override may not trigger scale-out and will fail the full scaling
check. Watch the live decisions separately with:

```bash
KUBECONFIG="$PWD/.kubeconfig" kubectl -n hero-association get hpa --watch
KUBECONFIG="$PWD/.kubeconfig" kubectl -n hero-association top pods --containers
```

On 2026-09-27, a 16-client run with a BFF rolling restart returned 9,344
HTTP 200 responses at 103.7 requests/second, with p95 96 ms, p99 206 ms,
and no transport errors. BFF reached six Pods and Core reached eight during
and immediately after the run. Earlier runs without the application drain
window had brief upstream connection failures during Pod turnover; the
drain-enabled run had none. This is one read-only load point, not a proven
maximum or a write/combat capacity result. All three k3d nodes share one
computer and Docker engine; Pod autoscaling here demonstrates workload
behavior, not physical-node or multi-AZ resilience.

## Mixed read/write capacity lab

Run `./test-mixed-capacity.sh` from this directory after the JVM backend and
frontend are deployed. It creates a temporary Core database and test-only
BFF/Core stacks, routes only header-marked requests to them, then measures
authenticated state reads and hero-activity writes across 2-8-Pod HPAs. It
removes its resources and database on exit. See [CAPACITY.md](CAPACITY.md)
for prerequisites, commands, measured latency boundary, and the distinction
between this single-computer lab and real multi-node resilience.

## Build and deploy the frontend

After the backend deployment, run from this directory:

```bash
./build-frontend-image.sh
./deploy-frontend.sh
```

The build runs `npm ci`, lint, and the production Vite build before creating
and importing the Nginx image. The frontend has one non-meshed
Pod and a private Service. Envoy Gateway serves `/` from that Service while
`/api` and `/auth` keep their more-specific BFF routes. Normal Vite and Compose
are unaffected. After changing frontend source, rerun the build script and
restart the Deployment to consume the reimported fixed image tag:

```bash
KUBECONFIG="$PWD/.kubeconfig" kubectl -n hero-association rollout restart deploy/frontend
KUBECONFIG="$PWD/.kubeconfig" kubectl -n hero-association rollout status deploy/frontend
```

The isolated k3d browser suite uses the existing cluster and seeded test
accounts. It does not start, reset, or delete the Compose E2E stack or the k3d
databases:

```bash
cd ../../e2e
npm ci
npm run test:k3d
```

The Playwright container uses Docker host networking and explicit hosts entries
to reach k3d's loopback-bound HTTPS port `443`. It ignores the local CA warning for
tests only. For a trusted Windows browser, import the development CA as
described in [`../../backend/README.md`](../../backend/README.md#local-https-gateway).

To pause this lab without deleting its data, use
`k3d cluster stop hero-association`; resume with
`k3d cluster start hero-association`. Do not use `kubectl delete -k
../k8s/backend` as a casual cleanup command: that Kustomize tree includes
the namespace and database PVCs, so it would remove more than the backend
Pods and could discard all lab data. The generated credentials in `secrets/`
are kept on disk unless you intentionally remove them.
