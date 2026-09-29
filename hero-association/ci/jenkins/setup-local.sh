#!/usr/bin/env bash
set -euo pipefail

script_dir="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")" && pwd)"
project_dir="$(cd -- "$script_dir/../.." && pwd)"
kubeconfig="${HERO_ASSOCIATION_K3D_KUBECONFIG:-$project_dir/deploy/k3d/.kubeconfig}"
k3d_bin="${K3D_BIN:-$(command -v k3d || true)}"
if [[ -z "$k3d_bin" ]]; then
  k3d_bin="$project_dir/deploy/k3d/.tools/k3d"
fi
agent_workdir="${JENKINS_AGENT_WORKDIR:-${XDG_STATE_HOME:-$HOME/.local/state}/hero-association-jenkins/agent}"

for tool in docker kubectl java node npm git openssl curl; do
  command -v "$tool" >/dev/null || { printf 'Missing required tool: %s\n' "$tool" >&2; exit 1; }
done
[[ -r "$kubeconfig" ]] || { printf 'Missing readable k3d kubeconfig: %s\n' "$kubeconfig" >&2; exit 1; }
[[ -x "$k3d_bin" ]] || { printf 'Missing executable k3d binary: %s\n' "$k3d_bin" >&2; exit 1; }
[[ "$(kubectl --kubeconfig "$kubeconfig" config current-context)" == k3d-hero-association ]] ||
  { printf 'Kubeconfig must select k3d-hero-association\n' >&2; exit 1; }
[[ "$(node -p 'process.versions.node.split(".")[0]')" == 24 ]] ||
  { printf 'Node.js 24 is required for this local pipeline\n' >&2; exit 1; }
java -version 2>&1 | grep -Eq 'version "25([."]|$)' ||
  { printf 'Java 25 is required for this local pipeline\n' >&2; exit 1; }
docker info >/dev/null

for value in "$kubeconfig" "$k3d_bin" "$agent_workdir"; do
  [[ "$value" != *[[:space:]]* ]] ||
    { printf 'Jenkins local paths cannot contain whitespace: %s\n' "$value" >&2; exit 1; }
done

umask 077
mkdir -p "$agent_workdir"
if [[ ! -e "$script_dir/.env" ]]; then
  admin_password="$(openssl rand -hex 24)"
  printf 'JENKINS_ADMIN_PASSWORD=%s\nJENKINS_AGENT_WORKDIR=%s\n' "$admin_password" "$agent_workdir" > "$script_dir/.env"
  printf 'Created private controller settings at %s\n' "$script_dir/.env"
else
  printf 'Keeping existing controller settings at %s\n' "$script_dir/.env"
fi

if [[ ! -e "$script_dir/agent.env" ]]; then
  {
    printf 'HERO_ASSOCIATION_K3D_KUBECONFIG=%q\n' "$(realpath "$kubeconfig")"
    printf 'K3D_BIN=%q\n' "$(realpath "$k3d_bin")"
    printf 'JENKINS_AGENT_WORKDIR=%q\n' "$agent_workdir"
    printf 'JENKINS_AGENT_JAVA_BIN=%q\n' "$(command -v java)"
    printf 'JENKINS_AGENT_NODE_BIN=%q\n' "$(command -v node)"
    printf 'TESTCONTAINERS_HOST_OVERRIDE=host.docker.internal\n'
  } > "$script_dir/agent.env"
  printf 'Created private WSL agent settings at %s\n' "$script_dir/agent.env"
else
  printf 'Keeping existing WSL agent settings at %s\n' "$script_dir/agent.env"
fi
printf 'Next: docker compose -f %s/compose.yaml up --detach --build\n' "$script_dir"
