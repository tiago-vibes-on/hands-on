#!/usr/bin/env bash
set -euo pipefail

script_dir="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")" && pwd)"
kubeconfig_path="$script_dir/.kubeconfig"
secret_dir="$script_dir/secrets"
manifest_dir="$script_dir/../k8s/expedition"
namespace=hero-association
secret_name=hero-association-expedition-credentials

if [[ $# -ne 0 ]]; then
  printf 'Usage: %s\n' "$0" >&2
  exit 2
fi
if [[ ! -f "$kubeconfig_path" ]]; then
  printf 'Missing %s. Create the k3d cluster first.\n' "$kubeconfig_path" >&2
  exit 1
fi
export KUBECONFIG="$kubeconfig_path"
if [[ "$(kubectl config current-context)" != 'k3d-hero-association' ]]; then
  printf 'Refusing to stage Expedition outside the hero-association k3d context.\n' >&2
  exit 1
fi

keys=(
  HERO_ASSOCIATION_EXPEDITION_RABBITMQ_PASSWORD
  HERO_ASSOCIATION_EXPEDITION_WORKER_RABBITMQ_PASSWORD
  HERO_ASSOCIATION_CORE_SETTLEMENT_RABBITMQ_PASSWORD
  HERO_ASSOCIATION_EXPEDITION_CORE_SERVICE_KEY
  HERO_ASSOCIATION_EXPEDITION_BFF_SERVICE_KEY
)

umask 077
mkdir -p "$secret_dir"
if kubectl -n "$namespace" get secret "$secret_name" >/dev/null 2>&1; then
  for key in "${keys[@]}"; do
    if [[ ! -s "$secret_dir/$key" ]]; then
      printf 'Missing local %s while the k3d secret exists; refusing to rotate broker credentials.\n' "$secret_dir/$key" >&2
      exit 1
    fi
    if ! kubectl -n "$namespace" get secret "$secret_name" \
      -o "jsonpath={.data.$key}" | base64 --decode | cmp -s "$secret_dir/$key" -; then
      printf 'Local %s differs from the k3d secret; refusing to rotate broker credentials.\n' "$key" >&2
      exit 1
    fi
  done
else
  for key in "${keys[@]}"; do
    if [[ ! -s "$secret_dir/$key" ]]; then
      openssl rand -hex 32 | tr -d '\n' > "$secret_dir/$key"
    fi
  done
fi

kubectl apply -f "$script_dir/../k8s/base/namespace.yaml"
secret_args=()
for key in "${keys[@]}"; do
  secret_args+=("--from-file=$secret_dir/$key")
done
kubectl -n "$namespace" create secret generic "$secret_name" \
  "${secret_args[@]}" --dry-run=client -o yaml | kubectl apply -f -
kubectl -n "$namespace" create configmap hero-association-expedition-rabbitmq-setup \
  --from-file=prepare-expedition.mjs="$script_dir/../../backend/rabbitmq/prepare-expedition.mjs" \
  --dry-run=client -o yaml | kubectl apply -f -

kubectl apply -f "$manifest_dir/redis.yaml"
kubectl apply -f "$manifest_dir/rabbitmq.yaml"
kubectl -n "$namespace" rollout status statefulset/redis-expedition --timeout=5m
kubectl -n "$namespace" rollout status statefulset/rabbitmq-expedition --timeout=5m

job_name="$(kubectl create -f "$manifest_dir/rabbitmq-setup-job.yaml" -o jsonpath='{.metadata.name}')"
if ! kubectl -n "$namespace" wait --for=condition=complete "job/$job_name" --timeout=4m; then
  kubectl -n "$namespace" logs "job/$job_name" --all-containers=true || true
  exit 1
fi
kubectl -n "$namespace" logs "job/$job_name" --all-containers=true

expedition_existed=false
if kubectl -n "$namespace" get deployment/expedition >/dev/null 2>&1; then
  expedition_existed=true
fi
kubectl apply -f "$manifest_dir/expedition.yaml"
if [[ "$expedition_existed" == true ]]; then
  kubectl -n "$namespace" rollout restart deployment/expedition
fi
kubectl -n "$namespace" rollout status deployment/expedition --timeout=5m
kubectl -n "$namespace" get statefulset/redis-expedition statefulset/rabbitmq-expedition deployment/expedition service/expedition
printf 'Expedition is staged privately; Core, BFF, frontend, and gateway routing were not changed.\n'
