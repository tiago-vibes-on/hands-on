# ADR 0001: Envoy Gateway for k3d ingress

- Date: 2026-09-27
- Status: Accepted for k3d ingress; market limiting covered by ADR 0005

## Context

The isolated k3d lab needs one browser-facing gateway for the frontend, BFF,
and Keycloak. BFF and Game Core will scale independently. The market also needs
a limit on placing buy and sell orders: **5 requests per second per
authenticated user**, shared across that user's sessions and across all
gateway and BFF replicas. This is not 5 requests per second for all players
combined.

The market-order POST `/api/v1/market/orders` accepts either BUY or SELL.
Both sides consume the same per-user budget, even if a manager belongs to
multiple agencies. Order-book reads and order cancellation are outside this
particular limit. Rate limiting does not replace Core's market authorization,
transaction rules, or safeguards against duplicate orders.

## Decision

Use **Envoy Gateway** as the sole public ingress in k3d and keep K3s's bundled
Traefik disabled there. Retain **Traefik** for the separate Docker Compose
development workflow. Caddy was the earlier local-edge implementation; this
ADR does not require changing the normal local edge.

Envoy Gateway suits the Kubernetes lab because its Gateway API HTTPRoute and
BackendTrafficPolicy resources support distributed global rate limits across
Envoy proxy replicas. The current market-order limit is enforced by Envoy
Gateway after BFF session validation; see
[ADR 0005](0005-k3d-market-order-edge-auth.md).

## Alternatives considered

- **Traefik for both environments:** fewer gateway products to operate.
  Traefik also supports rate limiting, including Redis-backed distributed
  limits in current versions. It is a viable alternative; Envoy Gateway was
  chosen for the k3d lab's Gateway API policy model and existing deployment.
- **Caddy for both environments:** familiar from the first local stack, but
  its documented rate-limit module is non-standard and would add plugin or
  custom-build maintenance for this Kubernetes use case.
- **BFF market limiter:** initially selected in ADR 0002. It was superseded
  after adding a trusted identity handoff for Envoy in ADR 0005.

## Consequences and follow-up

- Envoy remains the k3d ingress and enforces the per-user market limit using
  a BFF-validated subject. Its outage behavior is recorded in
  [ADR 0005](0005-k3d-market-order-edge-auth.md).

## References

- [Envoy Gateway global rate limiting](https://gateway.envoyproxy.io/docs/tasks/traffic/global-rate-limit/)
- [Envoy Gateway rate-limit API](https://gateway.envoyproxy.io/docs/api/extension_types/)
- [Traefik rate limiting](https://doc.traefik.io/traefik/reference/routing-configuration/http/middlewares/ratelimit/)
- [Caddy rate-limit module](https://caddyserver.com/docs/json/apps/http/servers/routes/handle/rate_limit/rate)
