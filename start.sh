#!/usr/bin/env bash

set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
FRONTEND_DIR="$ROOT_DIR/frontend"
BACKEND_DIR="$ROOT_DIR/backend"
SERVICE_NAME="${SERVICE_NAME:-mirad-upload}"

echo "==> Mirad Upload start script"
echo "Project root: $ROOT_DIR"
echo "Service name: $SERVICE_NAME"

if ! command -v npm >/dev/null 2>&1; then
  echo "Error: npm is not installed"
  exit 1
fi

if ! command -v mvn >/dev/null 2>&1; then
  echo "Error: mvn is not installed"
  exit 1
fi

if ! command -v systemctl >/dev/null 2>&1; then
  echo "Error: systemctl is not available"
  exit 1
fi

echo "==> Building frontend"
cd "$FRONTEND_DIR"
if [ ! -d node_modules ]; then
  npm install
fi
npm run build

echo "==> Packaging backend"
cd "$BACKEND_DIR"
mvn clean package -DskipTests

echo "==> Restarting systemd service"
sudo systemctl restart "$SERVICE_NAME"

echo "==> Service status"
sudo systemctl --no-pager --full status "$SERVICE_NAME" || true

echo "==> Done"
echo "Frontend dist: $FRONTEND_DIR/dist"
echo "Backend jar: $BACKEND_DIR/target/mirad-upload-backend-1.0.0.jar"
