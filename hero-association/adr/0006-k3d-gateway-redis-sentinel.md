# ADR 0006: Sentinel for gateway rate-limit Redis in k3d

- Date: 2026-09-28
- Status: Accepted for the isolated k3d lab only

## Context

The market's five-per-second per-user limit is shared by Envoy proxies through
gateway Redis. The original single Redis Pod made every Pod failure a market
placement outage. Gateway Redis is separate from BFF session Redis and stores
short-lived rate-limit counters, not durable game data.

## Decision

Run three Redis/Sentinel Pods in one StatefulSet with a quorum of two. Spread
them across the three k3d nodes, keep at least two available during voluntary
disruptions, and let Sentinel elect a new primary. Configure the Envoy
rate-limit service with `REDIS_TYPE=sentinel` and all three Sentinel endpoints.
Keep the existing fail-closed policy: if the rate-limit service cannot check
Redis, market placement receives an Envoy `500`, never an unbounded bypass.

The counters and Sentinel configuration remain ephemeral in this study lab.
Normal local Traefik development and the BFF's session Redis do not change.

## Alternatives

- Keep one Redis Pod: simpler, but it cannot tolerate a Pod failure.
- Use Redis Cluster: adds sharding and more nodes for a small rate-limit
  dataset; Sentinel is enough for this lab's single writable primary.
- Use managed Redis: suitable for a later external environment, not available
  in this isolated k3d lab.

## Consequences

Primary election and client reconnection can briefly return fail-closed
`500`s. A full Redis outage continues to block market placement. Restarting
all three Pods loses short-lived counters; it does not affect game or session
data. Three k3d nodes on one computer are not independent failure domains.
This setup has no Redis authentication, TLS, durable configuration, or alerting
and must not be copied to production. An external deployment needs those
controls, a genuinely multi-fault-domain Redis service, and an outage runbook.

The [k3d edge runbook](../deploy/k3d/EDGE_AUTH.md) verifies failover, total
outage, and the shared limit across two Envoy proxies. The design follows
[Redis Sentinel guidance](https://redis.io/docs/latest/operate/oss_and_stack/management/sentinel/)
and the [Envoy rate-limit Redis client](https://github.com/envoyproxy/ratelimit#redis-type).
