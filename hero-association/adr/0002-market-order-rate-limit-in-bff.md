# ADR 0002: Shared BFF rate limit for market order placement

- Date: 2026-09-27
- Status: Superseded by ADR 0005 on 2026-09-28

This records the earlier BFF limiter. It has been removed; the current k3d
market limit is enforced only by Envoy Gateway. Normal local Traefik has no
market rate limit. See [ADR 0005](0005-k3d-market-order-edge-auth.md).

## Context

Market order placement must accept at most five requests per second per
authenticated user across BUY and SELL, agencies, browser sessions, and BFF
replicas. The browser carries an opaque BFF session cookie; the k3d Envoy
Gateway cannot derive a trustworthy player identity from it. Using an IP or
browser-supplied identity header would group unrelated players or permit
spoofing. [ADR 0001](0001-envoy-gateway-for-k3d-ingress.md) still governs the
k3d browser-facing gateway.

## Original decision (superseded)

Enforce the limit in the BFF before forwarding
`POST /api/v1/market/orders` to Game Core. The BFF uses the
subject of its validated Keycloak token as the bucket identity. A single Redis
sorted set per subject records accepted request timestamps. An atomic Redis
script uses Redis server time, discards entries at least one second old,
admits a request only when fewer than five entries remain, and expires idle
keys after one second. This is a rolling one-second window with no extra
burst allowance. All BFF replicas use their shared, BFF-owned Redis.

An over-limit request receives HTTP `429` and `Retry-After: 1`. If Redis is
unavailable, placement fails closed with HTTP `503` and is not forwarded.
Other API calls, including order-book reads and order cancellation, are not
subject to this rule. A failed or invalid order attempt still consumes an
admitted slot; the BFF does not need to inspect a Core response to enforce the
limit. Core retains authorization, validation, and transactional safeguards.

## Historical consequences

- This BFF guard applies in Compose development and k3d. k3d additionally
  uses the trusted Envoy handoff in [ADR 0005](0005-k3d-market-order-edge-auth.md);
  this rolling-window guard remains in place behind it.
- Redis availability is required to place orders. It already holds BFF token
  state, but this limiter must not change or flush session keys.
- BFF tests use a temporary Redis container to check the rolling window,
  concurrency, and HTTP contract. Isolated browser E2E tests exercise two BFF
  instances, two sessions for one user, and another user.
- This is an abuse-control limit, not a guarantee against duplicate orders or
  a substitute for Core's financial authorization.

## References

- [Redis scripting and atomic execution](https://redis.io/docs/latest/develop/programmability/eval-intro/)
- [Quarkus Redis client](https://quarkus.io/guides/redis-reference/)
