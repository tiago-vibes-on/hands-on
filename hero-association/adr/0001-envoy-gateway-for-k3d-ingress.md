# ADR 0001: Envoy Gateway for k3d ingress

- Date: 2026-09-27
- Status: Accepted for k3d ingress; market limiting covered by ADR 0002

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
Envoy proxy replicas. Market order placement is instead limited by the BFF,
which knows the authenticated user and shares Redis state across replicas;
see [ADR 0002](0002-market-order-rate-limit-in-bff.md).

## Alternatives considered

- **Traefik for both environments:** fewer gateway products to operate.
  Traefik also supports rate limiting, including Redis-backed distributed
  limits in current versions. It is a viable alternative; Envoy Gateway was
  chosen for the k3d lab's Gateway API policy model and existing deployment.
- **Caddy for both environments:** familiar from the first local stack, but
  its documented rate-limit module is non-standard and would add plugin or
  custom-build maintenance for this Kubernetes use case.
- **Gateway market limiter:** would require a trusted identity handoff because
  Envoy cannot infer the user from an opaque BFF cookie. ADR 0002 selects the
  shared BFF limiter for this rule.

## Consequences and follow-up

- Envoy remains the k3d ingress and does not inspect browser identity for the
  market limit. The BFF's implementation and availability behavior are recorded
  in [ADR 0002](0002-market-order-rate-limit-in-bff.md).

## References

- [Envoy Gateway global rate limiting](https://gateway.envoyproxy.io/docs/tasks/traffic/global-rate-limit/)
- [Envoy Gateway rate-limit API](https://gateway.envoyproxy.io/docs/api/extension_types/)
- [Traefik rate limiting](https://doc.traefik.io/traefik/reference/routing-configuration/http/middlewares/ratelimit/)
- [Caddy rate-limit module](https://caddyserver.com/docs/json/apps/http/servers/routes/handle/rate_limit/rate)
