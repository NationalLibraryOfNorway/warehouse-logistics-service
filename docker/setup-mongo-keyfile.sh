#!/usr/bin/env bash
set -euo pipefail

KEYFILE="docker/mongo/secrets/keyfile.key"
KEYFILE_DIR="$(dirname "$KEYFILE")"
KEYFILE_RANDOM_BYTES=756

if ! command -v openssl >/dev/null 2>&1; then
  echo "openssl is required to generate Mongo keyfile but was not found in PATH"
  exit 41
fi

mkdir -p "$KEYFILE_DIR" || {
  echo "Failed to create Mongo keyfile directory: $KEYFILE_DIR"
  exit 46
}

if [[ ! -e "$KEYFILE" ]]; then
  echo "Mongo keyfile not found: $KEYFILE"
  echo "Generating keyfile..."
  openssl rand -base64 "$KEYFILE_RANDOM_BYTES" > "$KEYFILE" || {
    echo "Failed to generate keyfile: $KEYFILE"
    exit 45
  }
  chmod 640 "$KEYFILE" || {
    echo "Failed to set permission 640 on $KEYFILE"
    exit 47
  }
  echo "Generated keyfile: $KEYFILE"
fi

if [[ ! -f "$KEYFILE" ]]; then
  echo "Mongo keyfile path exists but is not a regular file: $KEYFILE"
  exit 43
fi

if [[ ! -s "$KEYFILE" ]]; then
  echo "Mongo keyfile is empty: $KEYFILE"
  exit 44
fi

if [[ ! -r "$KEYFILE" ]]; then
  echo "Mongo keyfile is not readable: $KEYFILE"
  exit 48
fi

echo "Mongo keyfile ready: $KEYFILE"
