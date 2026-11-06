#!/usr/bin/env bash
set -e

APP_ENV="development"
PORT=3000

echo "Starting service in $APP_ENV on port $PORT"
exec ./bin/server --port "$PORT"
