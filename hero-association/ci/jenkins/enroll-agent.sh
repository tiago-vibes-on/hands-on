#!/usr/bin/env bash
set -euo pipefail

script_dir="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")" && pwd)"
[[ -r "$script_dir/.env" ]] || { printf 'Run setup-local.sh first\n' >&2; exit 1; }
set -a
source "$script_dir/.env"
set +a
jenkins_url="http://127.0.0.1:15180"
auth_config="$(mktemp)"
secret_tmp="$(mktemp "$script_dir/.agent-secret.XXXXXX")"
jar_tmp="$(mktemp "$script_dir/agent.jar.XXXXXX")"
cleanup() {
  rm -f -- "$auth_config" "$secret_tmp" "$jar_tmp"
}
trap cleanup EXIT
chmod 600 "$auth_config" "$secret_tmp" "$jar_tmp"
printf 'user = "admin:%s"\n' "$JENKINS_ADMIN_PASSWORD" > "$auth_config"

curl --fail --silent --show-error --retry 30 --retry-delay 2 --retry-connrefused "$jenkins_url/login" >/dev/null
curl --fail --silent --show-error --config "$auth_config" "$jenkins_url/computer/wsl-local/jenkins-agent.jnlp" |
  node -e 'let xml=""; process.stdin.on("data", chunk => xml += chunk); process.stdin.on("end", () => { const match = xml.match(new RegExp("<argument>([a-fA-F0-9]{64})</argument>")); if (!match) { console.error("Could not read the WSL agent secret from Jenkins"); process.exit(1); } process.stdout.write(match[1] + String.fromCharCode(10)); });' > "$secret_tmp"
curl --fail --silent --show-error --output "$jar_tmp" "$jenkins_url/jnlpJars/agent.jar"
chmod 600 "$secret_tmp" "$jar_tmp"
mv -f -- "$secret_tmp" "$script_dir/.agent-secret"
mv -f -- "$jar_tmp" "$script_dir/agent.jar"
printf 'Enrolled the WSL agent. Its connection secret stays in the ignored .agent-secret file.\n'
