#!/bin/bash
# MongoDB initialization script for local development
# This script:
# 1. Sets up the keyfile with correct permissions in tmpfs
# 2. Starts MongoDB through official entrypoint with keyfile
# 3. Lets official first-run bootstrap create root user + run init scripts
# 4. Waits for MongoDB and initializes replica set on first run
# 5. Marks first-run initialization as complete
# 6. Brings MongoDB to the foreground

set -euo pipefail

# Keep mongosh state in /tmp so it does not try to write under /data/db.
export HOME="/tmp/mongosh-home"
export XDG_CONFIG_HOME="$HOME/.config"
export MONGOSH_HISTORY_FILE="/tmp/mongosh-history"
mkdir -p "$HOME" "$XDG_CONFIG_HOME"

# ==============================================================================
# STEP 1: Keyfile setup for replica set authentication
# ==============================================================================
echo "[1/8] Setting up keyfile for replica set authentication..."
KEYFILE_SRC="/run/mongo-keyfile-src/keyfile.key"
KEYFILE_DEST="/run/mongo-keyfile/keyfile.key"

[ -f "$KEYFILE_SRC" ] && [ -s "$KEYFILE_SRC" ] || { echo "ERROR: Missing keyfile at $KEYFILE_SRC"; exit 69; }
cp "$KEYFILE_SRC" "$KEYFILE_DEST" || { echo "ERROR: Failed to copy keyfile to container tmpfs"; exit 67; }
chmod 400 "$KEYFILE_DEST" || { echo "ERROR: Failed to set permission 400 on $KEYFILE_DEST"; exit 42; }
chown mongodb:mongodb "$KEYFILE_DEST" || { echo "ERROR: Failed to make mongodb owner of $KEYFILE_DEST"; exit 34; }

echo "✓ Keyfile configured successfully"

# ==============================================================================
# STEP 2: Check if this is first-time setup
# ==============================================================================
FIRST_RUN=false
if [ ! -f /data/db/.mongodb_initialized ]; then
  echo "[2/8] First run detected - will initialize MongoDB..."
  FIRST_RUN=true
else
  echo "[2/8] MongoDB already initialized - starting with keyfile..."
fi

# ==============================================================================
# STEP 3: Start MongoDB through official entrypoint
# ==============================================================================
if [ "$FIRST_RUN" = true ]; then
  echo "[3/8] Starting first-run bootstrap with keyfile..."
else
  echo "[3/8] Starting MongoDB WITH keyfile..."
fi

/usr/local/bin/docker-entrypoint.sh mongod \
  --replSet rs0 \
  --keyFile "$KEYFILE_DEST" \
  --bind_ip_all \
  --port 27017 &

MONGOD_PID=$!
echo "✓ MongoDB launcher started (PID $MONGOD_PID)"

# ==============================================================================
# STEP 4: Wait for MongoDB to be ready
# ==============================================================================
echo "[4/8] Waiting for MongoDB to accept authenticated connections..."
until mongosh --quiet \
  -u "$MONGO_INITDB_ROOT_USERNAME" \
  -p "$MONGO_INITDB_ROOT_PASSWORD" \
  --authenticationDatabase admin \
  --eval "db.adminCommand('ping')" >/dev/null 2>&1; do
  if ! kill -0 $MONGOD_PID 2>/dev/null; then
    echo "ERROR: MongoDB process died during startup"
    exit 1
  fi
  echo "  MongoDB not ready yet, retrying in 2s..."
  sleep 2
done
echo "✓ MongoDB is accepting authenticated connections"

# ==============================================================================
# STEP 5: Initialize replica set (only on first run)
# ==============================================================================
if [ "$FIRST_RUN" = true ]; then
  echo "[5/8] Initializing replica set..."
  mongosh -u "$MONGO_INITDB_ROOT_USERNAME" -p "$MONGO_INITDB_ROOT_PASSWORD" --authenticationDatabase admin --eval "
    try {
      rs.conf();
      print('✓ Replica set already initialized');
    } catch(err) {
      print('  Replica set not initialized, creating...');
      rs.initiate({
        _id: 'rs0',
        members: [{_id: 0, host: 'mongo-db:27017'}]
      });
      print('✓ Replica set initialized successfully');
    }
  "
else
  echo "[5/8] Skipping replica set initialization (already done)"
fi

# ==============================================================================
# STEP 6: Wait for replica set to be ready
# ==============================================================================
echo "[6/8] Waiting for replica set to be fully ready..."
until mongosh -u "$MONGO_INITDB_ROOT_USERNAME" -p "$MONGO_INITDB_ROOT_PASSWORD" --quiet --eval "rs.status().ok" 2>/dev/null | grep -q 1; do
  echo "  Replica set not ready yet, retrying in 2s..."
  sleep 2
done
echo "✓ Replica set is ready"

# ==============================================================================
# STEP 7: Load seed data (only on first run)
# ==============================================================================
if [ "$FIRST_RUN" = true ]; then
  echo "[7/8] Seed data already loaded by docker-entrypoint init scripts"

  # Mark as initialized
  touch /data/db/.mongodb_initialized
  echo "✓ Marked MongoDB as initialized"

  echo "[8/8] MongoDB is already running with keyfile"
else
  echo "[7/8] Skipping seed data load (already done)"
  echo "[8/8] MongoDB already running with keyfile"
fi

# ==============================================================================
# FINAL: MongoDB is ready
# ==============================================================================
echo ""
echo "=========================================="
echo "MongoDB initialization complete!"
echo "Replica set: rs0"
echo "Listening on: 0.0.0.0:27017"
echo "Authentication: enabled (keyfile)"
echo "=========================================="
echo ""

# Wait for the MongoDB process
wait $MONGOD_PID
