# Architecture Decision Records

This directory records significant architecture choices and their trade-offs.
An ADR documents a decision, not proof that every related feature is deployed.
Use the roadmap to track implementation.

Add a numbered NNNN-short-title.md file for each new decision. Include its
date, status, context, decision, alternatives, consequences, and follow-up
work. Keep accepted records for history; supersede one with a new ADR if the
decision changes.

## Decisions

| ADR | Status | Decision |
| --- | --- | --- |
| [0001](0001-envoy-gateway-for-k3d-ingress.md) | Accepted | Envoy Gateway for k3d ingress. |
| [0002](0002-market-order-rate-limit-in-bff.md) | Superseded by 0005 | Former shared BFF market-order limit. |
| [0003](0003-market-service-boundary.md) | Route family accepted; owner payload under review | Market-centric API and target service ownership. |
| [0004](0004-core-as-temporary-modular-monolith.md) | Accepted architectural direction; extraction pending | Keep Core temporarily while validating domain rules before service extraction. |
| [0005](0005-k3d-market-order-edge-auth.md) | Accepted and implemented in k3d | BFF-validated subject handoff to Envoy for the sole market-placement limit. |
