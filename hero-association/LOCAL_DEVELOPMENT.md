# Local development with k3d

The active development environment uses one k3d cluster. Envoy Gateway serves
`https://heroassociation.test` and Keycloak serves
`https://auth.heroassociation.test`. PostgreSQL, Redis, RabbitMQ, Keycloak,
observability, and the Gateway stay in k3d. You can run any application service
on WSL with hot reload while the others remain in k3d. The old
Compose/Traefik gateway and E2E runner were retired during the
[consolidation](LOCAL_ENVIRONMENT_PLAN.md); standalone JVM/native Compose
packaging is still optional.

## Requirements and first setup

Use Docker Desktop with WSL integration or a Linux Docker engine, Java 25,
Node.js 24/npm 11, Docker, `kubectl`, k3d v5.9.0, `istioctl` 1.30.5,
`curl`, `openssl`, `sha256sum`, `ss`, and `ip`. Allow about 8 GiB for the
cluster and observability stack. Follow the one-time
[cluster and deployment instructions](deploy/k3d/README.md) before using the
commands below. The ignored `deploy/k3d/.kubeconfig` must exist.

Add these names, without URL schemes, to the Windows hosts file
(`C:\Windows\System32\drivers\etc\hosts`) or the Linux `/etc/hosts` file:

```text
127.0.0.1 heroassociation.test
127.0.0.1 auth.heroassociation.test
```

The Gateway certificate and CA are generated in ignored `tls/certs/`. Trust
`tls/certs/local-ca.crt` in the browser's OS; never import or commit the CA
private key. On Windows, run from the `hero-association` directory:

```bash
WINDOWS_CERTIFICATE_PATH="$(wslpath -w tls/certs/local-ca.crt)"
(cd /mnt/c && certutil.exe -user -addstore -f Root "$WINDOWS_CERTIFICATE_PATH")
```

Restart the browser after first trusting the CA. Windows `curl.exe` may need
`--ssl-revoke-best-effort` for this offline local CA; do not disable TLS
verification. The two retired `k3d.*` aliases are not needed.

## Start and inspect the full k3d application

From `hero-association/deploy/k3d`:

```bash
k3d cluster start hero-association
KUBECONFIG="$PWD/.kubeconfig" kubectl -n hero-association get deploy,svc,httproute,hpa
./hybrid.sh status core
./hybrid.sh status bff
./hybrid.sh status expedition
./hybrid.sh status market
./hybrid.sh status frontend
```

Starting an existing cluster does not rebuild images or reset data. After
first setup, all six application Deployments should be ready. To pause the
whole environment, restore any hybrid services first, then run
`k3d cluster stop hero-association`; this keeps the cluster data. The `status`
command reads only the isolated k3d context; it does not change another
Kubernetes cluster. Envoy alone owns the local browser ports 80 and 443.

## Switch a service to WSL hot reload

Run one command per selected service in its own terminal, from
`hero-association/deploy/k3d`:

```bash
./hybrid.sh run core
./hybrid.sh run bff
./hybrid.sh run expedition
./hybrid.sh run market
./hybrid.sh run frontend
```

Use only the services you are editing. Each command first checks the k3d
context and existing Deployment, starts private port-forwards, stops that
service's k3d Pods and HPA if present, then starts Quarkus dev mode or Vite on
the private WSL address. A small in-cluster bridge keeps the original Service
name and Envoy route; BFF and Core traffic still reaches Istio on the bridge
leg. The WSL leg is plain HTTP and development-only. Core, BFF, Expedition,
Market, Assets and Vite use host ports `17081`, `17080`, `17083`, `17084`, `17085`, and
`15172` respectively.
The script reserves additional loopback ports for k3d PostgreSQL, Redis,
Keycloak, RabbitMQ, OTLP, and service-to-service forwards; it stops with a
clear error if one of those ports is already in use. It reads k3d Secrets at
runtime and does not write them into Git. Host-run Quarkus services disable
Dev Services and export telemetry to the shared k3d collector; they do not
launch a separate LGTM container.

When combining services, start dependencies before callers (for example,
Expedition and Core before BFF). Switching a dependency while a host-run BFF
is already serving may cause an in-flight or pooled request to fail once;
restart the BFF hybrid command after changing its upstream route. Test only
after all selected bridges report ready.

Press Ctrl-C in each hybrid terminal to stop the local process and restore
that service's previous k3d replicas, Service selector, and HPA. To recover
after a terminal or WSL interruption, run `./hybrid.sh restore <service>`
for each affected service. It stops only the recorded host process and
port-forwards, then restores k3d. The ignored `.hybrid-state/` files record
the original replica/HPA state and process identities. Never delete those
files or scale the old workload manually while a hybrid switch is active;
the restore command refuses to create a second writer if an unrelated host
process still listens on the service port.

Core, Assets and Market dev modes use schema `validate`, disable seed loading and
bootstrap, and connect to their own k3d PostgreSQL over distinct loopback ports.
Ordinary reloads preserve shared data. After a schema change, restore full
k3d mode and use the archive-based `pipeline/deploy-k3d.mjs --reset-game-db`
workflow. It checks for unfinished Expeditions, stops the application services and checks unfinished workflows, and resets
Core, Assets and Market together from the verified images. The legacy
`--reset-core-db` flag is an alias. This reset discards disposable game data;
it is separate from hot reload.

## Verify

Fast pure unit tests run locally. Core, BFF, Expedition, Market and Assets component tests
use disposable k3d PostgreSQL, Redis, and RabbitMQ without changing daily data:

```bash
./deploy/k3d/test-isolated-components.sh all
./deploy/k3d/test-isolated-components.sh bff
```

The optional argument selects `core`, `bff`, `expedition`, `market`, `assets`, or `all` (default).
Backend candidate builds invoke the matching lane before Maven packages with
`-DskipTests`; direct `./mvnw package` still runs its own local test workflow.
The k3d lane replaces the two direct RabbitMQ Testcontainers transport checks
with equivalent AMQP assertions against its private broker. Browser and gateway-policy
tests use the k3d Envoy route. From `hero-association/e2e`:

```bash
npm ci
npm run test:k3d
npm run test:k3d:expedition
npm run test:k3d:map
npm run test:market:k6
```

The Expedition and Map tests change the seeded test Managers' game progress.
The market k6 test sends intentionally incomplete orders; HTTP 400 from Market
only means Envoy allowed the request, while HTTP 429 means the per-user limit
was enforced. The full candidate gate now runs an exact six-image archive
in a disposable k3d namespace, including browser, session, Redis, and k6
checks. See [K3D_ISOLATION.md](e2e/K3D_ISOLATION.md); the seeded-user smoke
commands above are not a substitute for that isolated gate.

Jenkins builds and deploys are explicit separate actions; a Git push alone
does not promote an image. See the [Jenkins](ci/jenkins/README.md) and
[pipeline](pipeline/README.md) runbooks for candidate verification and
promotion. Use full k3d mode for final promotion, not a hybrid bridge.
