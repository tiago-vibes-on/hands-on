#!/usr/bin/env bash
set -euo pipefail

if [[ $# -ne 2 ]]; then
  printf 'Usage: %s <source-repository> <new-snapshot-directory>\n' "$0" >&2
  exit 2
fi

source_repo="$(realpath -- "$1")"
snapshot_repo="$(realpath -m -- "$2")"
[[ -d "$source_repo" ]] || { printf 'Missing source repository: %s\n' "$source_repo" >&2; exit 1; }
[[ "$(git -C "$source_repo" rev-parse --show-toplevel)" == "$source_repo" ]] ||
  { printf 'Source must be the Git repository root: %s\n' "$source_repo" >&2; exit 1; }
[[ ! -e "$snapshot_repo" ]] || { printf 'Snapshot already exists: %s\n' "$snapshot_repo" >&2; exit 1; }
case "$snapshot_repo/" in
  "$source_repo/"*) printf 'Snapshot must be outside the source repository\n' >&2; exit 1 ;;
esac

source_revision="$(git -C "$source_repo" rev-parse HEAD)"
git clone --quiet --local --no-hardlinks --no-checkout "$source_repo" "$snapshot_repo"
[[ "$(git -C "$snapshot_repo" rev-parse HEAD)" == "$source_revision" ]] ||
  { printf 'Source HEAD changed during snapshot\n' >&2; exit 1; }
git -C "$snapshot_repo" read-tree HEAD

file_list="$(mktemp)"
trap 'rm -f -- "$file_list"' EXIT
while IFS= read -r -d '' relative_path; do
  source_path="$source_repo/$relative_path"
  if [[ -d "$source_path" && ! -L "$source_path" ]]; then
    printf 'Submodule or directory entry is not supported: %s\n' "$relative_path" >&2
    exit 1
  fi
  if [[ -e "$source_path" || -L "$source_path" ]]; then
    printf '%s\0' "$relative_path" >> "$file_list"
  fi
done < <(git -C "$source_repo" ls-files -z --cached --others --exclude-standard)

rsync -a --from0 --files-from="$file_list" "$source_repo/" "$snapshot_repo/"
printf 'Snapshot HEAD: %s\n' "$source_revision"
printf 'Snapshot files: %s\n' "$(tr -cd '\0' < "$file_list" | wc -c)"
