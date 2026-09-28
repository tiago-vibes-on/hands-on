#!/usr/bin/env bash
set -euo pipefail

script_dir="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")" && pwd)"
export KUBECONFIG="$script_dir/.kubeconfig"
namespace=hero-association
deployment=core-concurrency-test
combat_id=019c4c00-0050-7000-8000-000000000001
quest_id=019c4c00-0003-7000-8000-000000000001
hero_id=019c4c00-0010-7000-8000-000000000004
database="ha_concurrency_$(openssl rand -hex 4)"
database_created=false
deployment_created=false
bootstrap_job=
read_load_pid=
read_load_output=

if [[ $# -ne 0 ]]; then
  printf 'Usage: %s\n' "$0" >&2
  exit 2
fi
if [[ ! -f "$KUBECONFIG" || "$(kubectl config current-context)" != k3d-hero-association ]]; then
  printf 'The isolated hero-association k3d kubeconfig is required.\n' >&2
  exit 1
fi
if kubectl -n "$namespace" get deployment "$deployment" >/dev/null 2>&1; then
  printf 'Refusing to replace an existing %s deployment.\n' "$deployment" >&2
  exit 1
fi

query() {
  kubectl -n "$namespace" exec deployment/postgres-core -c postgres -- \
    psql -X -q -v ON_ERROR_STOP=1 -U hero_association -d "$database" -At -c "$1"
}

admin_query() {
  kubectl -n "$namespace" exec deployment/postgres-core -c postgres -- \
    psql -X -q -v ON_ERROR_STOP=1 -U hero_association -d hero_association -At -c "$1"
}

cleanup() {
  result=$?
  trap - EXIT
  if (( result != 0 )) && [[ "$deployment_created" == true ]]; then
    kubectl -n "$namespace" get pods -l "app=$deployment" -o wide >&2 || true
    kubectl -n "$namespace" logs deployment/"$deployment" -c core --tail=80 >&2 || true
  fi
  if [[ -n "$read_load_pid" ]] && kill -0 "$read_load_pid" 2>/dev/null; then
    kill "$read_load_pid" 2>/dev/null || true
    wait "$read_load_pid" 2>/dev/null || true
  fi
  if [[ -n "$read_load_output" ]]; then
    rm -f -- "$read_load_output"
  fi
  if [[ "$deployment_created" == true ]]; then
    if ! kubectl -n "$namespace" delete deployment "$deployment" --wait=true --timeout=2m >&2; then
      result=1
    fi
  fi
  if [[ -n "$bootstrap_job" ]]; then
    if ! kubectl -n "$namespace" delete job "$bootstrap_job" --ignore-not-found=true --wait=false >&2; then
      result=1
    fi
  fi
  if [[ "$database_created" == true ]]; then
    if ! admin_query "DROP DATABASE \"$database\" WITH (FORCE)" >&2; then
      result=1
    fi
  fi
  exit "$result"
}
trap cleanup EXIT

if [[ "$(admin_query "SELECT count(*) FROM pg_database WHERE datname = '$database'")" != 0 ]]; then
  printf 'Refusing to reuse existing database %s.\n' "$database" >&2
  exit 1
fi
printf 'Creating isolated test database %s.\n' "$database"
admin_query "CREATE DATABASE \"$database\""
database_created=true

bootstrap_job="$(kubectl set env --local -f "$script_dir/../k8s/backend/core-db-bootstrap.yaml" \
  "QUARKUS_DATASOURCE_JDBC_URL=jdbc:postgresql://postgres-core:5432/$database" -o yaml \
  | kubectl create -f - -o jsonpath='{.metadata.name}')"
if ! kubectl -n "$namespace" wait --for=condition=complete "job/$bootstrap_job" --timeout=5m; then
  kubectl -n "$namespace" logs "job/$bootstrap_job" --all-containers=true >&2 || true
  exit 1
fi

if [[ "$(query "SELECT status || '|' || next_event_sequence FROM quest_combat WHERE id = '$combat_id'")" != 'IN_PROGRESS|0' ]]; then
  printf 'The seeded combat is not in its initial state.\n' >&2
  exit 1
fi
combat_start="$(query "SELECT last_synchronized_at FROM quest_combat WHERE id = '$combat_id'")"
query "UPDATE quest_combatant SET max_health = 20000, current_health = 20000 WHERE combat_id = '$combat_id' AND team = 'CREATURES'" >/dev/null

printf 'Starting two Core Pods against the same test database.\n'
kubectl set env --local -f "$script_dir/../k8s/backend/core-concurrency-test.yaml" \
  "QUARKUS_DATASOURCE_JDBC_URL=jdbc:postgresql://postgres-core:5432/$database" -o yaml \
  | kubectl create -f - >/dev/null
deployment_created=true
kubectl -n "$namespace" rollout status "deployment/$deployment" --timeout=5m
kubectl -n "$namespace" wait --for=condition=Ready pod -l "app=$deployment" --timeout=5m >/dev/null
mapfile -t pod_nodes < <(kubectl -n "$namespace" get pods -l "app=$deployment" \
  -o jsonpath='{range .items[*]}{.spec.nodeName}{"\n"}{end}')
if [[ "${#pod_nodes[@]}" -ne 2 || "${pod_nodes[0]}" == "${pod_nodes[1]}" ]]; then
  printf 'Expected two Ready Core Pods on distinct k3d nodes.\n' >&2
  exit 1
fi
printf 'Two Pods are Ready on %s and %s.\n' "${pod_nodes[0]}" "${pod_nodes[1]}"
read_load_output="$(mktemp)"
printf 'Starting 20 read-only PostgreSQL transactions/second on the temporary database.\n'
kubectl -n "$namespace" exec -i deployment/postgres-core -c postgres -- \
  pgbench -n -c 4 -j 2 -T 180 -R 20 -P 30 -U hero_association \
  -d "$database" -f /dev/stdin >"$read_load_output" 2>&1 <<SQL &
SELECT count(*) FROM quest_combat_event WHERE combat_id = '$combat_id';
SELECT status, current_time_milliseconds FROM quest_combat WHERE id = '$combat_id';
SELECT current_health, current_mana FROM hero WHERE id = '$hero_id';
SQL
read_load_pid=$!

verify_recovery() {
  local replica_count="$1"
  local baseline recovery
  baseline="$(query "UPDATE hero SET current_health = 0, current_mana = 0, last_resource_synchronized_at = clock_timestamp() WHERE id = '$hero_id' RETURNING last_resource_synchronized_at")"
  if [[ -z "$baseline" ]]; then
    printf 'Failed to prepare the recovering hero.\n' >&2
    exit 1
  fi
  sleep 12
  recovery="$(query "WITH elapsed AS (SELECT current_health, current_mana, floor(extract(epoch FROM (last_resource_synchronized_at - '$baseline'::timestamptz)))::int AS seconds FROM hero WHERE id = '$hero_id') SELECT CASE WHEN seconds >= 5 AND current_health = LEAST(300, 10 * seconds) AND current_mana = LEAST(50, 2 * seconds) THEN 'PASS' ELSE 'FAIL' END || '|seconds=' || seconds || '|health=' || current_health || '|mana=' || current_mana FROM elapsed")"
  printf 'Shared recovery with %s Pods: %s\n' "$replica_count" "$recovery"
  if [[ "$recovery" != PASS\|* ]]; then
    exit 1
  fi
}
verify_recovery 2

combat_state() {
  query "WITH events AS (SELECT count(*) AS total, count(DISTINCT sequence_number) AS unique_count, min(sequence_number) AS first_sequence, max(sequence_number) AS last_sequence FROM quest_combat_event WHERE combat_id = '$combat_id') SELECT CASE WHEN c.status = 'IN_PROGRESS' AND e.total > 0 AND e.total = e.unique_count AND e.total = e.last_sequence - e.first_sequence + 1 AND c.next_event_sequence = e.last_sequence + 1 AND abs(c.current_time_milliseconds - floor(extract(epoch FROM (c.last_synchronized_at - '$combat_start'::timestamptz)) * 1000)) < 1000 THEN 'PASS' ELSE 'FAIL' END || '|status=' || c.status || '|events=' || e.total || '|time_ms=' || c.current_time_milliseconds FROM quest_combat c CROSS JOIN events e WHERE c.id = '$combat_id'"
}
state="$(combat_state)"
printf 'Shared combat with 2 Pods: %s\n' "$state"
if [[ "$state" != PASS\|* ]]; then
  exit 1
fi

scale_and_verify() {
  local replica_count="$1"
  local previous_time current_time ready_count node_count state
  previous_time="$(query "SELECT current_time_milliseconds FROM quest_combat WHERE id = '$combat_id'")"
  printf 'Scaling temporary Core deployment to %s Pods.\n' "$replica_count"
  kubectl -n "$namespace" scale "deployment/$deployment" --replicas="$replica_count" >/dev/null
  kubectl -n "$namespace" rollout status "deployment/$deployment" --timeout=5m
  kubectl -n "$namespace" wait --for=condition=Ready pod -l "app=$deployment" --timeout=5m >/dev/null
  ready_count="$(kubectl -n "$namespace" get deployment "$deployment" -o jsonpath='{.status.readyReplicas}')"
  node_count="$(kubectl -n "$namespace" get pods -l "app=$deployment" -o jsonpath='{range .items[*]}{.spec.nodeName}{"\n"}{end}' | sort -u | wc -l)"
  if [[ "$ready_count" != "$replica_count" || "$node_count" -lt 3 ]]; then
    printf 'Expected %s Ready Core Pods spread across all three k3d nodes (got %s Pods on %s nodes).\n' "$replica_count" "$ready_count" "$node_count" >&2
    exit 1
  fi
  verify_recovery "$replica_count"
  current_time="$(query "SELECT current_time_milliseconds FROM quest_combat WHERE id = '$combat_id'")"
  state="$(combat_state)"
  printf 'Shared combat with %s Pods: %s\n' "$replica_count" "$state"
  if [[ "$state" != PASS\|* || "$current_time" -le "$previous_time" ]]; then
    exit 1
  fi
}
scale_and_verify 4
scale_and_verify 8

before_restart="$(query "SELECT current_time_milliseconds FROM quest_combat WHERE id = '$combat_id'")"
old_pod="$(kubectl -n "$namespace" get pods -l "app=$deployment" -o jsonpath='{.items[0].metadata.name}')"
printf 'Restarting %s during active combat.\n' "$old_pod"
kubectl -n "$namespace" delete pod "$old_pod" --wait=false >/dev/null
kubectl -n "$namespace" wait --for=delete "pod/$old_pod" --timeout=2m >/dev/null
kubectl -n "$namespace" rollout status "deployment/$deployment" --timeout=5m
sleep 6
after_restart="$(query "SELECT current_time_milliseconds FROM quest_combat WHERE id = '$combat_id'")"
state="$(combat_state)"
printf 'Shared combat after restart: %s\n' "$state"
if [[ "$state" != PASS\|* || "$after_restart" -le "$before_restart" ]]; then
  exit 1
fi

if ! kill -0 "$read_load_pid" 2>/dev/null; then
  cat "$read_load_output" >&2
  printf 'The database read load ended before quest resolution.\n' >&2
  exit 1
fi
printf 'Reducing the test Trolls to one health to trigger quest resolution.\n'
query "UPDATE quest_combatant SET current_health = 1 WHERE combat_id = '$combat_id' AND team = 'CREATURES'" >/dev/null
resolved=false
for attempt in {1..8}; do
  sleep 5
  if [[ "$(query "SELECT status FROM quest WHERE id = '$quest_id'")" == COMPLETED ]]; then
    resolved=true
    break
  fi
done
if [[ "$resolved" != true ]]; then
  printf 'Quest did not resolve within 40 seconds.\n' >&2
  exit 1
fi

resolution="$(query "WITH events AS (SELECT count(*) AS total, count(DISTINCT sequence_number) AS unique_count, min(sequence_number) AS first_sequence, max(sequence_number) AS last_sequence FROM quest_combat_event WHERE combat_id = '$combat_id') SELECT CASE WHEN q.status = 'COMPLETED' AND q.finished_at IS NOT NULL AND q.party_id IS NULL AND q.creatures_defeated = q.creatures_required AND c.status = 'HERO_VICTORY' AND e.total = e.unique_count AND e.total = e.last_sequence - e.first_sequence + 1 AND c.next_event_sequence = e.last_sequence + 1 AND (SELECT count(*) FROM hero WHERE party_id = '019c4c00-0002-7000-8000-000000000001' AND activity = 'TRAINING') = 3 THEN 'PASS' ELSE 'FAIL' END || '|quest=' || q.status || '|combat=' || c.status || '|events=' || e.total FROM quest q JOIN quest_combat c ON c.quest_id = q.id CROSS JOIN events e WHERE q.id = '$quest_id'")"
printf 'Quest resolution: %s\n' "$resolution"
if [[ "$resolution" != PASS\|* ]]; then
  exit 1
fi

resolved_snapshot() {
  query "SELECT q.status || '|' || q.finished_at || '|' || COALESCE(q.party_id::text, 'none') || '|' || c.status || '|' || c.next_event_sequence || '|' || c.current_time_milliseconds || '|' || (SELECT count(*) FROM quest_combat_event WHERE combat_id = c.id) FROM quest q JOIN quest_combat c ON c.quest_id = q.id WHERE q.id = '$quest_id'"
}
snapshot="$(resolved_snapshot)"
old_pod="$(kubectl -n "$namespace" get pods -l "app=$deployment" -o jsonpath='{.items[0].metadata.name}')"
printf 'Restarting %s after quest resolution.\n' "$old_pod"
kubectl -n "$namespace" delete pod "$old_pod" --wait=false >/dev/null
kubectl -n "$namespace" wait --for=delete "pod/$old_pod" --timeout=2m >/dev/null
kubectl -n "$namespace" rollout status "deployment/$deployment" --timeout=5m
sleep 7
if [[ "$(resolved_snapshot)" != "$snapshot" ]]; then
  printf 'A completed quest changed after the Pod restart.\n' >&2
  exit 1
fi
if ! wait "$read_load_pid"; then
  cat "$read_load_output" >&2
  printf 'The concurrent database read load failed.\n' >&2
  exit 1
fi
read_load_pid=
cat "$read_load_output"
if ! rg -q '^number of failed transactions: 0 ' "$read_load_output" ||
   ! rg -q '^number of transactions actually processed: [1-9][0-9]{3,}$' "$read_load_output"; then
  printf 'The concurrent database read load had failures or processed fewer than 1,000 transactions.\n' >&2
  exit 1
fi
printf 'PASS: 2→4→8 Core Pods recovered once, progressed combat without duplicate events under concurrent reads, resolved the quest once, and preserved its result through restarts.\n'
