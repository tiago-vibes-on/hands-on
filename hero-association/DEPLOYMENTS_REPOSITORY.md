# Shared deployments repository plan

Status: proposed and deferred. Nothing has moved yet. Track implementation at
the end of [the roadmap](ROADMAP.md).

## Repositories and visibility

- `raydowgames/hero-association`: public game source, tests, Dockerfiles,
  local development, and documentation.
- `raydowgames/deployments`: one public source of truth for the shared
  Kubernetes deployment, including infrastructure and every game's non-secret
  deployment manifests.
- `raydowgames/<other-game>`: private game source and images unless that game
  is intentionally opened.

Hero Association is currently a subdirectory of `hands-on`. Creating its own
repository means extracting that directory, preferably with relevant Git
history, rather than transferring the whole `hands-on` repository. Review the
extracted history for secrets and unrelated files before publishing it.

There is no planned `private-deployments` repository. A public deployment repo
will disclose game names, hostnames, image references, versions, and some
topology. Revisit this choice before adding a game whose existence or
deployment metadata must remain confidential. Private image access and source
code are independent of public deployment manifests.

## One cluster, shared services

Use one local k3d cluster for all games. `raydowgames/deployments` owns the
single Envoy Gateway, Istio installation, Keycloak instance, and OpenTelemetry,
Prometheus, Loki, Tempo, and Grafana stack. Game workloads have separate
namespaces, data, credentials, and deployment settings. Their routes attach
to the shared gateway; no game installs another copy of platform services.

One Keycloak instance does not itself provide a shared Raydow account. That
requires a planned common realm, distinct game clients, and game-specific
authorization. Migration of Hero Association's current realm is separate
work. Shared telemetry must retain game identity and restrict access to
private-game data.

Organize the proposed repository by shared definitions and target environment:

```text
bootstrap/k3d/          Create the local cluster and prerequisites
infrastructure/         Shared platform definitions and reusable bases
apps/<game>/            Game deployment definitions, not source code
clusters/local-k3d/     Composition and settings for the current cluster
```

Add another `clusters/<environment>/` only when it exists. Normal Docker
Compose development and independently buildable Hero Association backends
remain supported outside this Kubernetes deployment plan.

## Security and deployment boundaries

- Never commit passwords, tokens, client secrets, database credentials,
  kubeconfigs, TLS private keys, or registry credentials to any repository.
  Commit only references or safe templates and provision secret values
  separately.
- Keep private-game images in an access-controlled registry. Public image
  references do not grant image-pull access.
- Do not run untrusted public pull requests with cluster deployment
  credentials. Promote reviewed, trusted revisions; separate cluster-wide
  platform privileges from game-scoped deployment privileges.
- Enforce per-game RBAC, service accounts, network controls, resource
  budgets, and data isolation. Namespaces alone do not provide full
  isolation. The shared platform is a common failure domain, acceptable for
  the local lab but requiring review before production.

## Deferred migration sequence

1. Inventory existing k3d, Kubernetes, and Jenkins files; assign each
   resource one owner: platform or Hero Association application.
2. Extract `raydowgames/hero-association` and create
   `raydowgames/deployments`. Preserve supported local development.
3. Move shared cluster setup and game deployment definitions to
   `deployments` without creating duplicate managers of a resource. Keep
   secret values outside Git.
4. Retarget build and deploy jobs with least privilege. Verify builds,
   browser E2E, market k6, routing, authentication, observability, and
   rollback before retiring the old k3d deployment paths.
