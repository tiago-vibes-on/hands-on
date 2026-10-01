# ADR 0005: k3d gateway identity handoff for market placement

- Date: 2026-09-28
- Status: Accepted and implemented in full k3d and hybrid local modes

## Context

The browser has an opaque BFF session cookie. Envoy Gateway cannot derive a
trustworthy Keycloak subject from that cookie or from a browser-supplied header.
[ADR 0002](0002-market-order-rate-limit-in-bff.md) initially placed the
limit in the BFF. We now want Envoy Gateway to own the limit in k3d without
changing browser login. The former separate Traefik development mode was
retired on 2026-10-01; hybrid mode uses the same Envoy policy.

## Decision

Keep Keycloak OIDC, access tokens, logout, CSRF, and the browser session in the
BFF. In full k3d and hybrid local modes, an exact market-placement HTTPRoute
(`POST /api/v1/market/orders`, including a trailing slash) requires Envoy
Gateway HTTP external authorization. Envoy sends the session cookie and CSRF
header to the BFF's private `/internal/market-order-identity` endpoint. The
BFF checks its authenticated session and returns the Keycloak subject in
`X-Hero-Association-Subject`; Envoy uses that trusted authorization result,
not a client-provided header, as the distinct global rate-limit key.

The gateway uses a separate Redis-backed rate-limit service and allows five
placement requests per second per subject across gateway replicas. Its local
`429` response includes `Retry-After: 1` and
`X-Hero-Association-Rate-Limit-Layer: envoy`. External authorization fails
closed. The identity endpoint is disabled outside k3d and is not exposed on a
public HTTPRoute. The BFF applies no market-order rate limit. If the
gateway rate-limit service or its Redis is unavailable, Envoy fails closed
with HTTP 500 before forwarding placement. The BFF Redis continues to
store OIDC token state only.

## Alternatives

- Move browser OIDC to Envoy and give the BFF bearer tokens. This would
  replace the existing server-side token and login/callback/logout model, a
  larger security and product change than the rate-limit experiment needs.
- Trust an IP address or browser-provided subject. Neither identifies a
  player reliably; the latter is spoofable.
- Keep the BFF limit as a second layer. This complicates the effective
  request budget and hides gateway failure; this study deployment accepts
  the same market limit in hybrid local development.

## Consequences and follow-up

The k3d placement path uses BFF session and CSRF validation, Envoy external
authorization, the gateway rate-limit service, and gateway Redis. A deliberately
invalid order admitted by Envoy still reaches Core and returns 400; that status
does not mean the edge limit failed. Only Envoy emits market-limit 429 responses.
The gateway rate-limit Redis and BFF session Redis remain separate. Core keeps
its own bearer-token validation, agency authorization, and transactional rules.

The k3d resilience check verifies that complete gateway Redis loss causes
local Envoy 500 responses with rate_limiter_error and no upstream market
request. Two Envoy proxies share a per-user budget through gateway Redis.
The lab now uses three Redis/Sentinel Pods with quorum and Pod anti-affinity;
a single primary failure can briefly cause fail-closed 500s during election
and client reconnection. This is not production availability on one computer.
See [ADR 0006](0006-k3d-gateway-redis-sentinel.md) for the lab HA choice and
remaining hardening. Hybrid development now uses this same Envoy limit.
