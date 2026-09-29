#!/usr/bin/env bash
set -euo pipefail

script_dir="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")" && pwd)"
[[ -r "$script_dir/agent.env" ]] || { printf 'Run setup-local.sh first\n' >&2; exit 1; }
[[ -r "$script_dir/.agent-secret" && -r "$script_dir/agent.jar" ]] ||
  { printf 'Run enroll-agent.sh first\n' >&2; exit 1; }
set -a
source "$script_dir/agent.env"
set +a
export PATH="$(dirname "$JENKINS_AGENT_JAVA_BIN"):$(dirname "$JENKINS_AGENT_NODE_BIN"):$(dirname "$K3D_BIN"):$PATH"
mkdir -p "$JENKINS_AGENT_WORKDIR"
exec "$JENKINS_AGENT_JAVA_BIN" -jar "$script_dir/agent.jar" \
  -url http://127.0.0.1:15180/ \
  -secret "@$script_dir/.agent-secret" \
  -name wsl-local \
  -webSocket \
  -workDir "$JENKINS_AGENT_WORKDIR"
