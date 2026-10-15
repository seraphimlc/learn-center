#!/usr/bin/env bash
set -euo pipefail
ROOT="$(cd "$(dirname "$0")/.." && pwd)"
cd "$ROOT"
docker compose up -d
if [[ -f /home/box/agent-data/secrets/xyb2b-llm.env ]]; then
  set -a; source /home/box/agent-data/secrets/xyb2b-llm.env; set +a
fi
export UPLOAD_DIR="$ROOT/uploads"
mkdir -p "$UPLOAD_DIR"
cd "$ROOT/backend"
./mvnw -q -DskipTests spring-boot:run &
echo $! > /tmp/vlp-backend.pid
cd "$ROOT/frontend-user"
npm run dev -- --host 0.0.0.0 --port 5173 &
echo $! > /tmp/vlp-user.pid
cd "$ROOT/frontend-admin"
npm run dev -- --host 0.0.0.0 --port 5174 &
echo $! > /tmp/vlp-admin.pid
echo "Started. Backend :8080  User :5173  Admin :5174"
