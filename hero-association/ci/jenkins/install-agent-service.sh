#!/usr/bin/env bash
set -euo pipefail

script_dir="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")" && pwd)"
[[ -r "$script_dir/agent.env" && -r "$script_dir/.agent-secret" && -r "$script_dir/agent.jar" ]] ||
  { printf 'Run setup-local.sh and enroll-agent.sh first\n' >&2; exit 1; }
systemctl --user is-system-running >/dev/null ||
  { printf 'The WSL user systemd manager must be running\n' >&2; exit 1; }
unit_dir="${XDG_CONFIG_HOME:-$HOME/.config}/systemd/user"
unit_file="$unit_dir/hero-association-jenkins-agent.service"
install -d -m 700 "$unit_dir"
{
  printf '[Unit]\nDescription=Hero Association local Jenkins WSL build agent\nAfter=network-online.target\n\n'
  printf '[Service]\nType=simple\n'
  printf 'ExecStart=/usr/bin/bash "%s/run-agent.sh"\n' "$script_dir"
  printf 'Restart=always\nRestartSec=10\n\n'
  printf '[Install]\nWantedBy=default.target\n'
} > "$unit_file"
chmod 600 "$unit_file"
systemctl --user daemon-reload
systemctl --user disable hero-association-jenkins-agent.service
systemctl --user start hero-association-jenkins-agent.service
printf 'Installed and started %s for this session; automatic startup is disabled\n' "$unit_file"
