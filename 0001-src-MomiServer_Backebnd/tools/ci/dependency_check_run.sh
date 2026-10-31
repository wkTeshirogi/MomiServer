#!/usr/bin/env bash
set -euo pipefail

if [ "$#" -lt 3 ] || [[ ! "$1" =~ ^[1-9][0-9]*$ ]] || [[ ! "$2" =~ ^[1-9][0-9]*$ ]]; then
  echo 'Usage: dependency_check_run.sh HEARTBEAT_SECONDS SHUTDOWN_GRACE_SECONDS COMMAND [ARG...]' >&2
  exit 2
fi
heartbeat_seconds=$1
shutdown_grace_seconds=$2
shift 2

# Non-interactive job control gives each owned background job its own process group.
set -m
command_pid=
heartbeat_pid=

cleanup() {
  local result=$?
  local attempt
  trap - EXIT INT TERM
  if [ -n "$heartbeat_pid" ]; then
    kill -TERM -- "-$heartbeat_pid" 2>/dev/null || true
    wait "$heartbeat_pid" 2>/dev/null || true
  fi
  if [ -n "$command_pid" ]; then
    kill -TERM -- "-$command_pid" 2>/dev/null || true
    for ((attempt = 0; attempt < shutdown_grace_seconds; attempt++)); do
      if ! kill -0 -- "-$command_pid" 2>/dev/null; then
        break
      fi
      sleep 1
    done
    kill -KILL -- "-$command_pid" 2>/dev/null || true
    wait "$command_pid" 2>/dev/null || true
  fi
  exit "$result"
}

heartbeat() {
  trap - EXIT INT TERM
  local sleeper=
  trap 'kill "$sleeper" 2>/dev/null || true; wait "$sleeper" 2>/dev/null || true; exit 0' TERM INT
  while true; do
    sleep "$heartbeat_seconds" &
    sleeper=$!
    wait "$sleeper" || return
    TZ=UTC printf '[%(%Y-%m-%dT%H:%M:%SZ)T] Dependency-Check heartbeat: waiting for analysis; this is not download progress.\n' -1
  done
}

trap cleanup EXIT
trap 'exit 130' INT
trap 'exit 143' TERM

"$@" &
command_pid=$!
heartbeat &
heartbeat_pid=$!

if wait "$command_pid"; then
  exit 0
else
  exit "$?"
fi
