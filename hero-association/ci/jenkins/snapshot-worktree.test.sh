#!/usr/bin/env bash
set -euo pipefail

script_dir="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")" && pwd)"
test_dir="$(mktemp -d)"
trap 'rm -rf -- "$test_dir"' EXIT
fixture="$test_dir/fixture"
snapshot="$test_dir/snapshot"
mkdir -p "$fixture"
git -C "$fixture" init --quiet
git -C "$fixture" config core.autocrlf false
git -C "$fixture" config user.name 'Snapshot Test'
git -C "$fixture" config user.email 'snapshot-test@example.invalid'
printf 'ignored.txt\n' > "$fixture/.gitignore"
printf 'old\n' > "$fixture/changed.txt"
printf 'remove me\n' > "$fixture/deleted.txt"
printf '#!/bin/sh\nexit 0\n' > "$fixture/executable.sh"
chmod +x "$fixture/executable.sh"
ln -s changed.txt "$fixture/link.txt"
git -C "$fixture" add .
git -C "$fixture" commit --quiet -m seed
revision="$(git -C "$fixture" rev-parse HEAD)"

printf 'new content\n' > "$fixture/changed.txt"
rm -- "$fixture/deleted.txt"
printf 'untracked\n' > "$fixture/new.txt"
printf 'ignored\n' > "$fixture/ignored.txt"

"$script_dir/snapshot-worktree.sh" "$fixture" "$snapshot"
[[ "$(git -C "$snapshot" rev-parse HEAD)" == "$revision" ]]
[[ "$(cat "$snapshot/changed.txt")" == 'new content' ]]
[[ ! -e "$snapshot/deleted.txt" ]]
[[ "$(cat "$snapshot/new.txt")" == 'untracked' ]]
[[ ! -e "$snapshot/ignored.txt" ]]
[[ -x "$snapshot/executable.sh" ]]
[[ "$(readlink "$snapshot/link.txt")" == 'changed.txt' ]]
[[ -n "$(git -C "$snapshot" status --porcelain --untracked-files=normal)" ]]
printf 'Snapshot worktree regression test passed.\n'
