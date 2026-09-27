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

The config pins K3s 1.34.7 because this Docker Desktop engine exposes cgroup
v1 and Kubernetes 1.35's kubelet does not start on cgroup v1 by default. It
creates one server and two agents, disables K3s's bundled Traefik, and reserves
host ports `16550` for the Kubernetes API, `19080` for HTTP, and `19443` for
HTTPS. It does not modify the default kubeconfig or switch the current context,
protecting the existing WSL K3s context.

## Create the cluster

Run the following from this directory (`hero-association/deploy/k3d`):

```bash
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

Keep normal development's hosts entries and add the two k3d names to the
Windows hosts file (`C:\Windows\System32\drivers\etc\hosts`) or the Linux
hosts file (`/etc/hosts`). Entries contain names, not URL schemes:

```text
127.0.0.1 heroassociation.test
127.0.0.1 auth.heroassociation.test
127.0.0.1 k3d.heroassociation.test
127.0.0.1 auth.k3d.heroassociation.test
```

The k3d URLs are `https://k3d.heroassociation.test:19443` and
`https://auth.k3d.heroassociation.test:19443`; port `19443` is explicit because
normal Docker Compose development already uses port 443. The k3d leaf
certificate covers both hosts and is signed by the same local development CA.
The CA has not been imported into the Windows trust store, so a browser may
still show a certificate warning. Backend, Keycloak, and frontend routes are
installed by their separate deployment scripts below.

Envoy Gateway owns browser ingress in k3d. Istio is separate: workload-level
sidecars will observe and secure BFF-to-Core traffic. There is no Istio ingress
gateway, and the namespace is not globally labeled for injection. We will
validate the app with probes before enforcing Core mTLS or enabling 2-to-8-Pod
HPAs.

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

The Gateway listens through k3d's mapped ports `19080` and `19443`. HTTP
redirects to HTTPS on `19443`. `deploy-backend.sh` adds the BFF `/api` and
`/auth` routes plus Keycloak's hostname; `deploy-frontend.sh` adds the app
root route. More-specific BFF paths continue to reach BFF.

To repeat the HTTPS backend-routing smoke test, apply the temporary resources,
check the response, and remove them immediately:

```bash
KUBECONFIG="$PWD/.kubeconfig" kubectl apply -f gateway-smoke.yaml
KUBECONFIG="$PWD/.kubeconfig" kubectl -n hero-association rollout status deploy/gateway-smoke
curl --cacert ../../traefik/certs/local-ca.crt --resolve 'k3d.heroassociation.test:19443:127.0.0.1' https://k3d.heroassociation.test:19443/__gateway_smoke
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
  during future 2-to-8-Pod scaling runs. It shows one Pod per service now.
  Pod count is inferred from sampled resource metrics and may lag briefly
  during a rollout. Do not scale Core until shared-database startup and
  jobs are made safe.

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
Redis, Keycloak, Core, and BFF. Core and BFF each have one Istio-injected Pod
and private Services. Core uses `Recreate` rollout because its current startup
still drops and reseeds its database. Do not scale Core or enable an HPA yet.
The Envoy BFF route sets trusted forwarded host, scheme, and port headers so
Quarkus generates OIDC callbacks on the external `:19443` URL. The local
Compose environment and its volumes are not changed.

Check the Pods and browser-facing routes:

```bash
KUBECONFIG="$PWD/.kubeconfig" kubectl -n hero-association get pods,svc,httproute
curl --cacert ../../traefik/certs/local-ca.crt --resolve 'k3d.heroassociation.test:19443:127.0.0.1' https://k3d.heroassociation.test:19443/api/v1/session
curl --cacert ../../traefik/certs/local-ca.crt --resolve 'auth.k3d.heroassociation.test:19443:127.0.0.1' https://auth.k3d.heroassociation.test:19443/realms/hero-association
```

The frontend is served at `https://k3d.heroassociation.test:19443`. The
imported test users are `user1@mail.com` / `user1` and `user2@mail.com` /
`user2`, for this disposable lab only. The Keycloak admin username is
`hero-association-admin`; its generated password is in the ignored
`secrets/KEYCLOAK_ADMIN_PASSWORD` file.

To rebuild after source changes, rerun both scripts and restart the two
Deployments so they use the newly imported fixed `:k3d` image tags:

```bash
KUBECONFIG="$PWD/.kubeconfig" kubectl -n hero-association rollout restart deploy/core deploy/bff
KUBECONFIG="$PWD/.kubeconfig" kubectl -n hero-association rollout status deploy/core
KUBECONFIG="$PWD/.kubeconfig" kubectl -n hero-association rollout status deploy/bff
```

## Core mesh policy

`deploy-backend.sh` creates distinct `hero-association-bff` and
`hero-association-core` ServiceAccounts and waits for their Pods before it
applies `../k8s/mesh/`. Core then requires STRICT mutual TLS and an Istio
`AuthorizationPolicy` allows only callers presenting the BFF ServiceAccount
identity. BFF remains reachable from Envoy Gateway. PostgreSQL, Redis,
Keycloak, and the frontend stay outside the mesh. Core still checks OIDC
user tokens for protected APIs; workload authorization is an additional layer.
Istio rewrites Core's HTTP probes, so kubelet health checks remain functional.

Core uses a `Recreate` rollout. Changing its ServiceAccount or restarting its
Pod currently drops and reseeds the disposable Core schema; avoid the rollout
if you need to keep local changes. This must be fixed before scaling Core.

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
100m / 128Mi requests. The rollouts and both k3d browser tests passed.
This is a preliminary read-only baseline, not a
mixed-workload capacity limit or a reason to enable Core autoscaling yet.

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
to reach k3d's loopback-bound port `19443`. It ignores the local CA warning for
tests only. For a trusted Windows browser, import the development CA as
described in [`../../backend/README.md`](../../backend/README.md#local-https-gateway).

To pause this lab without deleting its data, use
`k3d cluster stop hero-association`; resume with
`k3d cluster start hero-association`. Do not use `kubectl delete -k
../k8s/backend` as a casual cleanup command: that Kustomize tree includes
the namespace and database PVCs, so it would remove more than the backend
Pods and could discard all lab data. The generated credentials in `secrets/`
are kept on disk unless you intentionally remove them.
