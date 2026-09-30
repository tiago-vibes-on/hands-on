# Private Expedition staging in k3d

This is an opt-in staging lane for the new Combat/Expedition path. Staging
leaves the player API, BFF WebSocket, and Core settlement disabled. The
separate [integration step](EXPEDITION_INTEGRATION.md) enables those private
APIs. The current k3d frontend build enables the Map menu; ordinary local
development keeps it hidden. Existing Core Quest combat remains available
while the [cutover](../../COMBAT_EXPEDITION_PLAN.md) is completed.

## Requirements and build

Start the existing k3d lab using the [main k3d runbook](README.md), including
Keycloak, Core, BFF, and Istio. From `hero-association/deploy/k3d`, make the
pinned `.tools/k3d` available if `k3d` is not already on `PATH`:

```bash
export PATH="$PWD/.tools:$PATH"
./build-backend-images.sh expedition
./stage-expedition.sh
```

The first command runs the Expedition and shared combat-engine Maven tests,
builds `hero-association-expedition:k3d`, and imports it to k3d. The second
refuses any kubeconfig context other than `k3d-hero-association`; it creates
stable, ignored local secrets, dedicated persistent Redis and RabbitMQ, a
one-time broker setup Job, and the private Expedition Deployment and Service.
Rerun both commands after a source change. The normal
`./build-backend-images.sh` default remains Core and BFF only.

The broker setup Job creates the durable settlement/acknowledgment topology
and grants Core and Expedition separate, narrow RabbitMQ permissions. It is
safe to rerun; the generated Job name is unique and Kubernetes cleans up old
finished Jobs after one hour. The five credential files remain in the ignored
`secrets/` directory. If the cluster Secret exists but local files are missing
or differ, the staging script stops rather than silently rotating the broker
administrator password against a persistent RabbitMQ volume. Do not commit
the files or delete the Redis/RabbitMQ PVCs while an Expedition may be active.

## Verify staging

```bash
KUBECONFIG="$PWD/.kubeconfig" kubectl -n hero-association get \
  statefulset/redis-expedition statefulset/rabbitmq-expedition deployment/expedition service/expedition
KUBECONFIG="$PWD/.kubeconfig" kubectl -n hero-association exec \
  statefulset/rabbitmq-expedition -c rabbitmq -- rabbitmqctl list_permissions -p /
```

The runtime accounts should have `^$` configure access and one exact exchange
write/queue read permission each. The setup-only broker administrator has
broader permissions. These lab defaults are not a production RabbitMQ
security design: management API traffic is internal and unencrypted, and the
k3d PVCs use local storage rather than multi-zone durable storage.

The private Core admission policy allows only Expedition's service account and
its four required internal routes. Keycloak's live realm is synchronized in
the [integration step](EXPEDITION_INTEGRATION.md). Core still requires a
service key and authenticated player token for admission.

The authenticated k3d Map journey verifies the Phaser canvas, reconnect,
explicit Continue, and settlement after the browser leaves. The worker stores
one Redis fight timeline instead of replaying combat for each visual read;
see [the timeline design](../../COMBAT_TIMELINE.md). The 100/500/1,000
concurrent-fight load tests remain deferred until the player path is stable.
See the [integration runbook](EXPEDITION_INTEGRATION.md) for commands.
