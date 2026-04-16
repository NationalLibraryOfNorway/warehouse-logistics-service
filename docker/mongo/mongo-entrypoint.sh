#!/bin/bash
# MongoDB initialization script for local development
# This script:
# 1. Sets up the keyfile with correct permissions in tmpfs
# 2. Starts MongoDB through official entrypoint with keyfile
# 3. Lets official first-run bootstrap create root user + run init scripts
# 4. Waits for MongoDB and ensures replica set is initialized (idempotent)
# 5. Waits for replica set readiness
# 6. Brings MongoDB to the foreground

set -euo pipefail

# Define retry parameters for waiting loops
RETRY_INTERVAL_SECONDS="2"
MAX_WAIT_SECONDS="60"

# Define log variables and log file
LOG_PREFIX="[mongo-db-entrypoint]"
SCRIPT_LOG_FILE="/tmp/mongo-entrypoint.log"
touch "$SCRIPT_LOG_FILE"
chmod 600 "$SCRIPT_LOG_FILE"

# Keep mongosh state in /tmp so it does not try to write under /data/db.
export HOME="/tmp/mongosh-home"
export XDG_CONFIG_HOME="$HOME/.config"
export MONGOSH_HISTORY_FILE="/tmp/mongosh-history"
mkdir -p "$HOME" "$XDG_CONFIG_HOME"

# Mirror all stderr output (entrypoint + init script stderr) into a dedicated file.
exec 3>&2
exec 2> >(tee -a "$SCRIPT_LOG_FILE" >&3)


# Helper functions for logging
log() {
  printf "%s %s\n" "$LOG_PREFIX" "$*" >&2
}

log_block() {
  printf "%s #####################################################################\n" "$LOG_PREFIX" >&2
  printf "%s %s\n" "$LOG_PREFIX" "$*" >&2
  printf "%s #####################################################################\n" "$LOG_PREFIX" >&2
}

# Function to check if mongod process is still running while waiting for conditions
ensure_mongod_running() {
  local context="$1"
  if ! kill -0 "$MONGOD_PID" 2>/dev/null; then
    log "ERROR: MongoDB process died while waiting for ${context}"
    exit 1
  fi
}

# Function to wait for a mongosh eval command to succeed, with retries and timeout
wait_for_mongosh_eval() {
  local label="$1"
  local eval_js="$2"
  local elapsed=0

  while true; do
    if mongosh --quiet \
      -u "$MONGO_INITDB_ROOT_USERNAME" \
      -p "$MONGO_INITDB_ROOT_PASSWORD" \
      --authenticationDatabase admin \
      --eval "$eval_js" >/dev/null 2>&1; then
      log "✓ ${label}"
      return 0
    fi

    ensure_mongod_running "$label"

    if [ "$elapsed" -ge "$MAX_WAIT_SECONDS" ]; then
      log "ERROR: Timed out waiting for ${label} after ${MAX_WAIT_SECONDS}s"
      exit 70
    fi

    log "  ${label} not ready yet, retrying in ${RETRY_INTERVAL_SECONDS}s... (${elapsed}/${MAX_WAIT_SECONDS}s)"
    sleep "$RETRY_INTERVAL_SECONDS"
    elapsed=$((elapsed + RETRY_INTERVAL_SECONDS))
  done
}

# Function to handle termination signals and forward them to mongod for graceful shutdown
handle_termination() {
  log "Received termination signal, forwarding to mongod..."
  if [ -n "${MONGOD_PID:-}" ] && kill -0 "$MONGOD_PID" 2>/dev/null; then
    kill -TERM "$MONGOD_PID" 2>/dev/null || true
    wait "$MONGOD_PID" || true
  fi
  exit 143
}

# Set up traps for termination signals to ensure graceful shutdown
trap handle_termination TERM INT


log ""
log_block "Starting MongoDB initialization..."
log ""


# ==============================================================================
# STEP 1: Keyfile setup for replica set authentication
# ==============================================================================


log "[1/5] Setting up keyfile for replica set authentication..."
KEYFILE_SRC="/run/mongo-keyfile-src/keyfile.key"
KEYFILE_DEST="/run/mongo-keyfile/keyfile.key"

[ -f "$KEYFILE_SRC" ] && [ -s "$KEYFILE_SRC" ] || { log "ERROR: Missing keyfile at $KEYFILE_SRC"; exit 69; }
cp "$KEYFILE_SRC" "$KEYFILE_DEST"              || { log "ERROR: Failed to copy keyfile to container tmpfs"; exit 67; }
chmod 400 "$KEYFILE_DEST"                      || { log "ERROR: Failed to set permission 400 on $KEYFILE_DEST"; exit 42; }
chown mongodb:mongodb "$KEYFILE_DEST"          || { log "ERROR: Failed to make mongodb owner of $KEYFILE_DEST"; exit 34; }

log "✓ Keyfile configured successfully"


# ==============================================================================
# STEP 2: Start MongoDB through official entrypoint
# ==============================================================================


log "[2/5] Starting MongoDB WITH keyfile..."

/usr/local/bin/docker-entrypoint.sh mongod \
  --replSet rs0 \
  --keyFile "$KEYFILE_DEST" \
  --bind_ip_all \
  --port 27017 &

MONGOD_PID=$!
log "✓ MongoDB launcher started (PID $MONGOD_PID)"


# ==============================================================================
# STEP 3: Wait for MongoDB to be ready
# ==============================================================================


log "[3/5] Waiting for MongoDB to accept authenticated connections..."
wait_for_mongosh_eval "MongoDB authenticated ping" "quit(db.adminCommand('ping').ok === 1 ? 0 : 1)"


# ==============================================================================
# STEP 4: Ensure replica set configuration exists (idempotent)
# ==============================================================================


log "[4/5] Ensuring replica set is initialized..."

# Wait for the real mongod (with --replSet) to be ready, then initialize the replica set if needed.
# Step 3's ping may have succeeded against the bootstrap mongod (no replSet, no auth) before it
# shut down. By the time we reach here, the real mongod may still be starting up, so we retry.
wait_for_mongosh_eval "Replica set initialization" "
  try {
    rs.conf();
    quit(0);
  } catch(err) {
    if (err.codeName === 'NotYetInitialized' || err.code === 94) {
      rs.initiate({ _id: 'rs0', members: [{ _id: 0, host: 'mongo-db:27017' }] });
      quit(0);
    }
    quit(1);
  }
"
log "✓ Replica set initialized"


# ==============================================================================
# STEP 5: Wait for replica set to be ready
# ==============================================================================

log "[5/5] Waiting for replica set to be fully ready..."

wait_for_mongosh_eval "Replica set readiness" "try { const s = rs.status(); quit(s.ok === 1 ? 0 : 1); } catch (err) { quit(1); }"

log "MongoDB is running with replica set 'rs0' and is ready to accept connections!"


# ==============================================================================
# FINAL: MongoDB is ready
# ==============================================================================


log ""
log_block "MongoDB initialization complete!"
log "Replica set: rs0"
log "Listening on: 0.0.0.0:27017"
log "Authentication: enabled (keyfile)"
log ""

# Wait for the MongoDB process
wait $MONGOD_PID
