#!/bin/bash
# MongoDB initialization script for local development
# This script:
# 1. Sets up the keyfile with correct permissions in tmpfs
# 2. Starts MongoDB WITHOUT keyfile to allow initialization
# 3. Waits for MongoDB and lets Docker entrypoint create root user
# 4. Initializes the replica set
# 5. Loads seed data
# 6. Restarts MongoDB WITH keyfile for secure replica set
# 7. Brings MongoDB to the foreground

set -euo pipefail

# ==============================================================================
# STEP 1: Keyfile setup for replica set authentication
# ==============================================================================
echo "[1/8] Setting up keyfile for replica set authentication..."
KEYFILE_SRC="/run/mongo-keyfile-src/keyfile.key"
KEYFILE_DEST="/run/mongo-keyfile/keyfile.key"

[ -s "$KEYFILE_SRC" ] || { echo "ERROR: Missing keyfile at $KEYFILE_SRC"; exit 69; }
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
# STEP 3: Start MongoDB (with or without keyfile based on initialization status)
# ==============================================================================
if [ "$FIRST_RUN" = true ]; then
  echo "[3/8] Starting MongoDB WITHOUT keyfile for initialization..."
  # Let docker-entrypoint.sh handle user creation via MONGO_INITDB_ROOT_* env vars
  /usr/local/bin/docker-entrypoint.sh mongod \
    --replSet rs0 \
    --bind_ip_all \
    --port 27017 &
else
  echo "[3/8] Starting MongoDB WITH keyfile..."
  /usr/local/bin/docker-entrypoint.sh mongod \
    --replSet rs0 \
    --keyFile "$KEYFILE_DEST" \
    --bind_ip_all \
    --port 27017 &
fi

MONGOD_PID=$!
echo "✓ MongoDB started with PID $MONGOD_PID"

# ==============================================================================
# STEP 4: Wait for MongoDB to be ready
# ==============================================================================
echo "[4/8] Waiting for MongoDB to accept connections..."
until mongosh --quiet --eval "db.adminCommand('ping')" >/dev/null 2>&1; do
  if ! kill -0 $MONGOD_PID 2>/dev/null; then
    echo "ERROR: MongoDB process died during startup"
    exit 1
  fi
  echo "  MongoDB not ready yet, retrying in 2s..."
  sleep 2
done
echo "✓ MongoDB is accepting connections"

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
  echo "[7/8] Loading seed data from mongo-init.js..."
  mongosh -u "$MONGO_INITDB_ROOT_USERNAME" -p "$MONGO_INITDB_ROOT_PASSWORD" --eval "load('/docker-entrypoint-initdb.d/mongo-init.js')"
  echo "✓ Seed data loaded successfully"

  # Mark as initialized
  touch /data/db/.mongodb_initialized
  echo "✓ Marked MongoDB as initialized"

  # ==============================================================================
  # STEP 8: Restart with keyfile for security
  # ==============================================================================
  echo "[8/8] Restarting MongoDB with keyfile for secure replica set..."
  echo "  Stopping MongoDB..."
  kill -SIGTERM $MONGOD_PID
  wait $MONGOD_PID || true

  echo "  Starting MongoDB with keyfile..."
  /usr/local/bin/docker-entrypoint.sh mongod \
    --replSet rs0 \
    --keyFile "$KEYFILE_DEST" \
    --bind_ip_all \
    --port 27017 &

  MONGOD_PID=$!
  echo "✓ MongoDB restarted with keyfile (PID $MONGOD_PID)"
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
