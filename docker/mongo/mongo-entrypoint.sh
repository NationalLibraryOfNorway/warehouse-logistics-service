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

LOG_PREFIX="[mongo-db-entrypoint]"
SCRIPT_LOG_FILE="/tmp/mongo-entrypoint.log"

touch "$SCRIPT_LOG_FILE"
chmod 600 "$SCRIPT_LOG_FILE"

# Mirror all stderr output (entrypoint + init script stderr) into a dedicated file.
exec 3>&2
exec 2> >(tee -a "$SCRIPT_LOG_FILE" >&3)

log() {
  printf "%s %s\n" "$LOG_PREFIX" "$*" >&2
}

log_block() {
  printf "%s #####################################################################\n" "$LOG_PREFIX" >&2
  printf "%s %s\n" "$LOG_PREFIX" "$*" >&2
  printf "%s #####################################################################\n" "$LOG_PREFIX" >&2
}

# Keep mongosh state in /tmp so it does not try to write under /data/db.
export HOME="/tmp/mongosh-home"
export XDG_CONFIG_HOME="$HOME/.config"
export MONGOSH_HISTORY_FILE="/tmp/mongosh-history"
mkdir -p "$HOME" "$XDG_CONFIG_HOME"


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
cp "$KEYFILE_SRC" "$KEYFILE_DEST" || { log "ERROR: Failed to copy keyfile to container tmpfs"; exit 67; }
chmod 400 "$KEYFILE_DEST" || { log "ERROR: Failed to set permission 400 on $KEYFILE_DEST"; exit 42; }
chown mongodb:mongodb "$KEYFILE_DEST" || { log "ERROR: Failed to make mongodb owner of $KEYFILE_DEST"; exit 34; }

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
until mongosh --quiet \
  -u "$MONGO_INITDB_ROOT_USERNAME" \
  -p "$MONGO_INITDB_ROOT_PASSWORD" \
  --authenticationDatabase admin \
  --eval "db.adminCommand('ping')" >/dev/null 2>&1; do

  if ! kill -0 $MONGOD_PID 2>/dev/null; then
    log "ERROR: MongoDB process died during startup"
    exit 1
  fi

  log "  MongoDB not ready yet, retrying in 2s..."
  sleep 2
done
log "✓ MongoDB is accepting authenticated connections"


# ==============================================================================
# STEP 4: Ensure replica set configuration exists (idempotent)
# ==============================================================================


log "[4/5] Ensuring replica set is initialized..."

mongosh -u "$MONGO_INITDB_ROOT_USERNAME" -p "$MONGO_INITDB_ROOT_PASSWORD" --authenticationDatabase admin --eval "
  try {
    rs.conf();
    print('${LOG_PREFIX} ✓ Replica set already initialized');
  } catch(err) {
    print('${LOG_PREFIX}   Replica set not initialized, creating...');
    rs.initiate({
      _id: 'rs0',
      members: [{_id: 0, host: 'mongo-db:27017'}]
    });
    print('${LOG_PREFIX} ✓ Replica set initialized successfully');
  }
"


# ==============================================================================
# STEP 5: Wait for replica set to be ready
# ==============================================================================

log "[5/5] Waiting for replica set to be fully ready..."

until mongosh --quiet \
  -u "$MONGO_INITDB_ROOT_USERNAME" \
  -p "$MONGO_INITDB_ROOT_PASSWORD" \
  --authenticationDatabase admin \
  --eval "rs.status().ok" 2>/dev/null | grep -q 1; do

  if ! kill -0 $MONGOD_PID 2>/dev/null; then
    log "ERROR: MongoDB process died during replica set initialization"
    exit 1
  fi

  log "  Replica set not ready yet, retrying in 2s..."
  sleep 2
done
log "✓ Replica set is ready"


log "MongoDB is running with keyfile"


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
