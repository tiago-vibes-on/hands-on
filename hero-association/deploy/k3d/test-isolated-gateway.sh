#!/usr/bin/env bash
set -euo pipefail

script_dir="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")" && pwd)"
project_dir="$(cd -- "$script_dir/../.." && pwd)"
kubeconfig="${HERO_ASSOCIATION_K3D_KUBECONFIG:-$script_dir/.kubeconfig}"
certificate_dir="$project_dir/tls/certs"
namespace="hero-association-e2e-probe-$$"
app_host=app.e2e.heroassociation.test

if [[ ! -f "$kubeconfig" || ! -s "$certificate_dir/local-ca.crt" ]]; then
  printf 'Missing k3d kubeconfig or local CA certificate.\n' >&2
  exit 1
fi
if [[ "$(kubectl --kubeconfig "$kubeconfig" config current-context)" != 'k3d-hero-association' ]]; then
  printf 'Refusing to test outside the hero-association k3d context.\n' >&2
  exit 1
fi

created=false
cleanup() {
  if [[ "$created" == true ]]; then
    kubectl --kubeconfig "$kubeconfig" delete namespace "$namespace" --wait=true --timeout=3m >&2 ||
      printf 'Remove the leftover probe namespace manually: %s\n' "$namespace" >&2
  fi
}
trap cleanup EXIT

kubectl --kubeconfig "$kubeconfig" create namespace "$namespace"
created=true
kubectl --kubeconfig "$kubeconfig" label namespace "$namespace" \
  heroassociation.test/e2e-gateway=true
kubectl --kubeconfig "$kubeconfig" -n "$namespace" apply -f - <<'YAML'
apiVersion: apps/v1
kind: Deployment
metadata:
  name: frontend
spec:
  replicas: 1
  selector:
    matchLabels:
      app: frontend
  template:
    metadata:
      labels:
        app: frontend
    spec:
      containers:
        - name: frontend
          image: hero-association-frontend:k3d
          imagePullPolicy: Never
          ports:
            - name: http
              containerPort: 80
---
apiVersion: v1
kind: Service
metadata:
  name: frontend
spec:
  selector:
    app: frontend
  ports:
    - name: http
      port: 80
      targetPort: http
---
apiVersion: gateway.networking.k8s.io/v1
kind: HTTPRoute
metadata:
  name: frontend
spec:
  parentRefs:
    - name: hero-association
      namespace: hero-association
      sectionName: e2e-app
  hostnames:
    - app.e2e.heroassociation.test
  rules:
    - backendRefs:
        - name: frontend
          port: 80
YAML

kubectl --kubeconfig "$kubeconfig" -n "$namespace" rollout status deployment/frontend --timeout=3m
kubectl --kubeconfig "$kubeconfig" -n "$namespace" wait \
  --for=jsonpath='{.status.parents[0].conditions[?(@.type=="Accepted")].status}'=True \
  httproute/frontend --timeout=2m

gateway_ip="$(docker inspect k3d-hero-association-serverlb --format \
  '{{(index .NetworkSettings.Networks "k3d-hero-association").IPAddress}}')"
if [[ ! "$gateway_ip" =~ ^([0-9]{1,3}\.){3}[0-9]{1,3}$ ]]; then
  printf 'Unable to resolve the k3d Gateway IPv4 address.\n' >&2
  exit 1
fi

playwright_version="$(node -p "require('$project_dir/e2e/package-lock.json').packages['node_modules/@playwright/test'].version")"
playwright_image="mcr.microsoft.com/playwright:v${playwright_version}-noble"
for host in "$app_host" heroassociation.test; do
  docker run --rm --network k3d-hero-association \
    --add-host "$host:$gateway_ip" \
    --mount "type=bind,src=$certificate_dir/local-ca.crt,dst=/tmp/local-ca.crt,readonly" \
    "$playwright_image" \
    curl --silent --show-error --fail --max-time 15 \
      --cacert /tmp/local-ca.crt "https://$host/" | grep -q 'Hero Association'
done

printf 'Isolated E2E route and daily app both served trusted HTTPS through Envoy.\n'
