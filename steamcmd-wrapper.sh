#!/usr/bin/env bash
set -euo pipefail

STEAMCMD_REAL="/home/steam/steamcmd/steamcmd.sh"
LOCKFILE="/var/lock/steamcmd-wrapper.lock"
LOGDIR="/root/Steam/logs"
CMDLOG="$LOGDIR/steamcmd-commands.log"

mkdir -p "$LOGDIR"

# Acquire exclusive lock to prevent concurrent steamcmd execution
exec 9>"$LOCKFILE"
flock -x 9 || { echo "Failed to acquire lock" >&2; exit 4; }

# Log command for debugging
echo "===== $(date -u '+%Y-%m-%d %H:%M:%S UTC') =====" >> "$CMDLOG"
echo "Command: $STEAMCMD_REAL $*" >> "$CMDLOG"

# Pre-update steamcmd to prevent version mismatches
"$STEAMCMD_REAL" +quit >/dev/null 2>&1 || true

# Run actual command
"$STEAMCMD_REAL" "$@" 2>&1
EXIT_CODE=$?

echo "Exit code: $EXIT_CODE" >> "$CMDLOG"
echo "" >> "$CMDLOG"

exit $EXIT_CODE
