# k3d edge authentication and market limiting

## Status

Implemented in the active k3d lab. Full k3d and hybrid development both use
Envoy Gateway and the same market-order rate limit. The browser keeps an opaque BFF session
cookie; Keycloak tokens remain server-side in BFF Redis. The BFF owns login,
logout, and CSRF, but not market limiting. Core independently validates bearer
tokens and financial authorization. [ADR 0005](../../adr/0005-k3d-market-order-edge-auth.md)
records the current gateway decision; [ADR 0002](../../adr/0002-market-order-rate-limit-in-bff.md)
records the superseded BFF guard.

## Request path

```text
Browser POST /api/v1/market/orders
  -> k3d Envoy Gateway exact market-order HTTPRoute
  -> SecurityPolicy HTTP external auth: BFF /internal/market-order-identity
       validates the session and returns the Keycloak subject
  -> BackendTrafficPolicy: global 5 requests/second per verified subject
       using gateway rate-limit service and its own Redis
  -> BFF: CSRF, session, proxy to Core
  -> Core: bearer token, agency leadership, order validation/transaction
```

Envoy forwards the Cookie and `X-CSRF-TOKEN` to external authorization. Its
trusted result sets `X-Hero-Association-Subject` for the rate-limit key;
a browser-supplied copy cannot choose a bucket. The BFF identity endpoint is
enabled only by the k3d Deployment setting and has no public HTTPRoute.
External authorization is fail-closed. Its request has a two-second timeout.
The dedicated gateway Redis is a three-Pod Redis/Sentinel group on separate
k3d nodes; it is not the BFF OIDC session Redis.
Gateway rate-limit errors fail closed with HTTP 500 before BFF forwarding.

Only `POST /api/v1/market/orders` and its trailing-slash form have this
gateway policy. GET order-book requests and order cancellation do not. BUY
and SELL attempts, agencies, sessions, and BFF replicas share a subject's
budget. An Envoy-generated `429` has `Retry-After: 1` and
`X-Hero-Association-Rate-Limit-Layer: envoy`. The BFF does not
produce a market-limit `429`. An admitted but deliberately invalid test order
returns Core `400`, unrelated to the edge limit.

## Build and verify

From `hero-association/deploy/k3d`, use this lab's ignored `.kubeconfig`
for every cluster command. Do not apply these resources to the separate WSL
K3s cluster. Install the gateway after creating the cluster, then build and
deploy as described in [README.md](README.md):

```bash
./install-envoy-gateway.sh
./install-gateway.sh
./build-backend-images.sh
./deploy-backend.sh
```

The installer applies `../k8s/local/redis-gateway-ha.yaml` and
`../k8s/local/envoy-gateway-config.yaml`. The backend Kustomize overlay
installs `../k8s/backend/market-order-edge.yaml`, and the BFF Deployment
enables its private identity endpoint. When importing a rebuilt image with
the same `:k3d` tag, restart only that Deployment so Pods load the new image:

```bash
KUBECONFIG="$PWD/.kubeconfig" kubectl -n hero-association rollout restart deployment/bff
KUBECONFIG="$PWD/.kubeconfig" kubectl -n hero-association rollout status deployment/bff
```

From `hero-association/e2e`, run:

```bash
npm run test:k3d
npm run test:market:k6
```

On 2026-09-28, all 6 k3d browser tests passed after deployment. They cover
login/logout, cross-user separation, two BFF Pods and two sessions for one
user, an Envoy-marked `429`, another user's independent budget even with a
spoofed subject header, and rejection of an anonymous spoofed request. The
k6 burst admitted 5 attempts and limited 1; the ten-second sustained run
admitted 45 and limited 156, with 0 unexpected results and 0 dropped
iterations. k6 also requires the Envoy layer header on every limited response.

Run the opt-in resilience check from `hero-association/deploy/k3d`:

```bash
./test-market-edge-resilience.sh
```

The command verifies the isolated kubeconfig, pauses the current Redis primary
to test Sentinel election, waits for the old primary to rejoin as a replica,
and checks the steady-state market limit. It then briefly stops all three
gateway Redis Pods to verify a local Envoy `500` without BFF forwarding,
restores the group, and scales Envoy to two proxies. The two-proxy load test
disables connection reuse so both proxies must receive market requests and
emit local `429`s. It restores replica counts even after a failed check. Do
not run concurrent user 1 market tests.

On 2026-09-28, all three checks passed. Sentinel promoted a replacement
primary and steady-state k6 again admitted five burst attempts and limited
the sixth. A complete gateway Redis outage produced a local Envoy `500` with
`rate_limiter_error` before the BFF was reached. With two proxies, each
emitted local `429`s and neither emitted an upstream market-limit `429`.
During primary election and client reconnection, fail-closed `500`s can
briefly occur; the test waits for the old primary to rejoin before checking
the steady-state limit. The counters and Sentinel configuration are ephemeral
and all three k3d nodes run on one computer. This is Pod redundancy, not a
production multi-AZ design. Do not reset either database for this test.

To inspect quorum and the primary from `deploy/k3d`:

```bash
KUBECONFIG="$PWD/.kubeconfig" kubectl -n envoy-gateway-system get pods -l app=redis-gateway-ha -o wide
KUBECONFIG="$PWD/.kubeconfig" kubectl -n envoy-gateway-system exec redis-gateway-ha-0 -c sentinel -- redis-cli -p 26379 sentinel ckquorum gateway
KUBECONFIG="$PWD/.kubeconfig" kubectl -n envoy-gateway-system exec redis-gateway-ha-0 -c sentinel -- redis-cli -p 26379 sentinel get-master-addr-by-name gateway
```

For this lab, investigate a missing quorum or unhealthy `envoy-ratelimit`
Pod before restarting anything; a full Redis outage blocks only market
placement, not the BFF session store. Outside k3d, require authenticated and
TLS-protected Redis, durable operator-managed failover, alerts, and a reviewed
outage runbook. See [ADR 0006](../../adr/0006-k3d-gateway-redis-sentinel.md).

## References

- [Envoy Gateway external authorization](https://gateway.envoyproxy.io/v1.9/tasks/security/ext-auth/)
- [Envoy Gateway global rate limiting](https://gateway.envoyproxy.io/v1.9/tasks/traffic/global-rate-limit/)
- [Envoy Gateway API reference](https://gateway.envoyproxy.io/v1.9/api/extension_types/)
