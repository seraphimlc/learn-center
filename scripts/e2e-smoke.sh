#!/usr/bin/env bash
set -euo pipefail
API="${API:-http://localhost:8080}"

echo "== health =="
curl -sf "$API/actuator/health" | grep -q UP

echo "== register =="
UNAME="e2e$(date +%s)"
REG=$(curl -sf -X POST "$API/api/auth/register" -H 'Content-Type: application/json' \
  -d "{\"username\":\"$UNAME\",\"password\":\"Pass@123\",\"email\":\"$UNAME@t.com\",\"nickname\":\"E2E\"}")
TOKEN=$(python3 -c "import json,sys; print(json.load(sys.stdin)['data']['token'])" <<<"$REG")

echo "== home =="
curl -sf "$API/api/public/home" | grep -q hotCourses

echo "== courses =="
COURSES=$(curl -sf "$API/api/public/courses")
COURSE_ID=$(python3 -c "import json,sys; d=json.load(sys.stdin)['data']['list']; print(d[0]['id'] if d else '')" <<<"$COURSES")
test -n "$COURSE_ID"

DETAIL=$(curl -sf "$API/api/public/courses/$COURSE_ID" -H "Authorization: Bearer $TOKEN")
LESSON_ID=$(python3 -c "import json,sys; print(json.load(sys.stdin)['data']['lessons'][0]['id'])" <<<"$DETAIL")

echo "== progress =="
curl -sf -X POST "$API/api/learn/progress" -H "Authorization: Bearer $TOKEN" -H 'Content-Type: application/json' \
  -d "{\"lessonId\":$LESSON_ID,\"positionSeconds\":15,\"deltaSeconds\":10,\"heartbeat\":true}" | grep -q positionSeconds

echo "== admin login =="
ADMIN=$(curl -sf -X POST "$API/api/auth/login" -H 'Content-Type: application/json' \
  -d '{"username":"admin","password":"Admin@123"}')
ATOKEN=$(python3 -c "import json,sys; print(json.load(sys.stdin)['data']['token'])" <<<"$ADMIN")
curl -sf "$API/api/admin/dashboard" -H "Authorization: Bearer $ATOKEN" | grep -q userCount

echo "== ai analysis (may degrade) =="
curl -sf "$API/api/ai/analysis" -H "Authorization: Bearer $TOKEN" | grep -q summary

echo "E2E SMOKE OK"
