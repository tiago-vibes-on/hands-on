# k3d mixed-workload capacity lab

This is a bounded local experiment, not a production capacity rating. It uses
an isolated Core database and temporary BFF/Core Deployments in the existing
k3d cluster. The normal BFF and Core Deployments remain running.

## Run it

First build/import the JVM images and deploy the k3d backend and frontend as
described in [README.md](README.md). Docker, Node/npm, kubectl, OpenSSL, the
ignored k3d kubeconfig, and the E2E dependencies (`cd ../../e2e && npm ci`)
must be available. From `hero-association/deploy/k3d`:

```bash
./test-mixed-capacity.sh
```

The default stages are 4, 8, 16, 32, and 64 concurrent clients for 60 seconds
each. To reproduce the fixed-eight-Pod boundary check below:

```bash
HERO_ASSOCIATION_K3D_MIXED_PREWARM_REPLICAS=8 \
HERO_ASSOCIATION_K3D_MIXED_STAGES='32 64 128' \
HERO_ASSOCIATION_K3D_MIXED_SECONDS=60 \
./test-mixed-capacity.sh
```

`HERO_ASSOCIATION_K3D_MIXED_PREWARM_REPLICAS` may be 2 (default HPA minimum)
or 8. The latter raises both temporary HPA minimums to eight before the first
stage; it does not warm up newly started JVMs. Use lower stages first. Stage
duration may be 10–300 seconds and client counts 1–128. The runner stops at
the first lab-target breach and prints the last passing stage. This is a
destructive test only for its uniquely named temporary Core database.

The script bootstraps a temporary Core database, marks its seeded agency as
`Capacity Lab Agency`, and creates temporary BFF/Core Services, Deployments,
2–8-Pod HPAs, Core mTLS policy, and a header-specific Envoy route. A request
with `X-Hero-Association-Capacity-Test: capacity` reaches the temporary BFF;
an ordinary request still reaches the live BFF. Playwright checks both routes
before load. It signs in as the local-only user1 account and sends roughly
90% authenticated `GET /api/v1/agencies/{id}/state` and 10% CSRF-protected
`PUT /api/v1/agencies/{id}/heroes/{id}/activity` requests. Those activity
writes affect only the temporary database. Login uses the existing Keycloak
and Redis session infrastructure and may update live account-login metadata;
the test does not write live agency game state.

The runner reports per-operation HTTP status counts, transport errors,
throughput, and p50/p95/p99 latency as `K3D_MIXED_RESULT`. Its illustrative
lab target is **under 1% request errors and at most 500 ms p95 for both reads
and writes**. Temporary resources and the database are removed on exit,
including after a failed stage. If cleanup reports an error, inspect the
named database and test resources before rerunning; the script refuses to
replace existing test resources.

## Observed results, 2026-09-27

The default autoscaling sequence passed all tested stages. The table shows
sampled peak BFF/Core replicas and the 60-second request results:

| Clients | Requests/s | Read p95 | Write p95 | Errors | Peak Pods BFF/Core |
| ---: | ---: | ---: | ---: | ---: | ---: |
| 4 | 30.3 | 52 ms | 58 ms | 0/1,819 | 2/4 |
| 8 | 64.8 | 32 ms | 45 ms | 0/3,893 | 2/4 |
| 16 | 121.4 | 43 ms | 59 ms | 0/7,290 | 5/4 |
| 32 | 173.7 | 142 ms | 190 ms | 0/10,433 | 8/7 |
| 64 | 247.2 | 182 ms | 295 ms | 0/14,876 | 8/8 |

A separate run held both test services at eight Pods and staged 32, 64, then
128 clients. All responses were HTTP 200, but the final stage breached the
write-latency target:

| Clients | Requests/s | Read p95 | Write p95 | Errors | Lab target |
| ---: | ---: | ---: | ---: | ---: | --- |
| 32 | 138.8 | 296 ms | 311 ms | 0/8,335 | Pass |
| 64 | 251.5 | 182 ms | 316 ms | 0/15,122 | Pass |
| 128 | 301.7 | 326 ms | 581 ms | 0/18,152 | Breach |

For this warmed, fixed-eight-Pod sequence, the measured boundary for the
stated target is **between 64 and 128 clients**: 251.5 requests/s passed,
while 301.7 requests/s exceeded the write p95 target. It is not an exact
maximum throughput or error threshold. An immediate 64-client run against
newly Ready eight-Pod Deployments was much slower (152.4 requests/s, 644 ms
read p95, 1,276 ms write p95), while a staged 16→32→64 run reached
267.7 requests/s with 157/224 ms read/write p95. Warm-up and local resource
contention materially affect this lab; repeat runs before making sizing
decisions.

All three k3d nodes share one computer and Docker engine. The test stack has
an isolated Core database but shares the PostgreSQL server, Keycloak, Redis,
Envoy Gateway, and host resources with the normal lab. It uses one account,
one agency, three agency heroes as write targets, and 60-second stages; it
does not measure market orders, multiple agencies, long endurance, native
images, hardware-node failure, or multi-AZ resilience. The temporary BFF and
Core use the same JVM images, requests, Istio injection, and 2–8-Pod CPU HPA
settings as the normal k3d services.
