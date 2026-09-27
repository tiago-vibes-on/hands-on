# ADR 0001: Envoy Gateway for k3d ingress

- Date: 2026-09-27
- Status: Accepted for k3d ingress; market rate limit not implemented

## Context

The isolated k3d lab needs one browser-facing gateway for the frontend, BFF,
and Keycloak. BFF and Game Core will scale independently. The market also needs
a limit on placing buy and sell orders: **5 requests per second per
authenticated user**, shared across that user's sessions and across all
gateway and BFF replicas. This is not 5 requests per second for all players
combined.

The existing POST /api/v1/agencies/{agencyId}/market-orders accepts either BUY
or SELL. Both sides consume the same per-user budget, even if a manager belongs
to multiple agencies. Order-book reads and order cancellation are outside this
particular limit. Rate limiting does not replace Core's market authorization,
transaction rules, or safeguards against duplicate orders.

## Decision

Use **Envoy Gateway** as the sole public ingress in k3d and keep K3s's bundled
Traefik disabled there. Retain **Traefik** for the separate Docker Compose
development workflow. Caddy was the earlier local-edge implementation; this
ADR does not require changing the normal local edge.

Envoy Gateway suits the Kubernetes lab because its Gateway API HTTPRoute and
BackendTrafficPolicy resources support distributed global rate limits across
Envoy proxy replicas. For market order placement, the intended bucket is
**per authenticated user across the deployment**: not per proxy, session, IP,
agency, or all users together. An over-limit request should receive HTTP 429.

The market policy is **planned, not deployed**. The current public route sends
/api to the BFF, which authenticates an opaque browser session cookie. Envoy
cannot safely infer a user ID merely by reading that cookie, and must never
trust a browser-supplied user-ID header. Before selecting users at the gateway,
provide a verified, stable identity there (for example, through a trusted
external-authorization integration). Strip any client-supplied identity header
and set it only in trusted infrastructure. If that proves disproportionate,
enforce the same per-user budget in the BFF with shared Redis state; keep the
k3d ingress decision and record the limiter-location change in a later ADR.

## Alternatives considered

- **Traefik for both environments:** fewer gateway products to operate.
  Traefik also supports rate limiting, including Redis-backed distributed
  limits in current versions. It is a viable alternative; Envoy Gateway was
  chosen for the k3d lab's Gateway API policy model and existing deployment.
- **Caddy for both environments:** familiar from the first local stack, but
  its documented rate-limit module is non-standard and would add plugin or
  custom-build maintenance for this Kubernetes use case.
- **Only a BFF limiter:** it can read the authenticated user directly and may
  be simpler for this rule, but needs shared state across BFF replicas. It
  remains the fallback if trusted identity at the edge is too costly.

## Consequences and follow-up

- Envoy Gateway's global rate-limit service needs shared state (Redis).
  Decide isolation from the existing BFF session Redis, availability, and
  fail-open/fail-closed behavior before enabling enforcement.
- Configure a market-order-specific route/policy; do not throttle other /api
  calls or Keycloak. Confirm BUY and SELL share one user bucket. Envoy's
  default policy bucket can be per route, so check the shared rule semantics
  if order placement spans multiple routes.
- Define the precise one-second window and burst behavior. Test HTTP 429 with
  two users and concurrent requests across multiple gateway and BFF replicas:
  one user's limit must not throttle another user.
- Keep the [roadmap](../ROADMAP.md) task open until identity, policy, Redis,
  and multi-replica tests are complete.

## References

- [Envoy Gateway global rate limiting](https://gateway.envoyproxy.io/docs/tasks/traffic/global-rate-limit/)
- [Envoy Gateway rate-limit API](https://gateway.envoyproxy.io/docs/api/extension_types/)
- [Traefik rate limiting](https://doc.traefik.io/traefik/reference/routing-configuration/http/middlewares/ratelimit/)
- [Caddy rate-limit module](https://caddyserver.com/docs/json/apps/http/servers/routes/handle/rate_limit/rate)
