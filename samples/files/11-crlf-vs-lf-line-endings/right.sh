#!/usr/bin/env bash
set -euo pipefail

APP_ENV="staging"
PORT=8080

echo "Starting service in $APP_ENV on port $PORT"
exec ./bin/server --port "$PORT"
