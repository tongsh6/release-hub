#!/bin/bash
set -e

BASE="http://localhost:8080/api/v1"

command -v curl >/dev/null 2>&1 || { echo >&2 "curl required"; exit 1; }
command -v python3 >/dev/null 2>&1 || { echo >&2 "python3 required"; exit 1; }

echo "Checking health..."
for i in {1..30}; do
  curl -s "$BASE/../actuator/health" >/dev/null && break
  echo "Waiting ($i/30) ..."
  sleep 2
done

echo "Login..."
TOKEN=$(curl -s -X POST "$BASE/auth/login" -H "Content-Type: application/json" -d '{"username":"admin","password":"admin"}' | grep -o '"token":"[^"]*' | cut -d'"' -f4)
if [ -z "$TOKEN" ]; then
  echo "Login failed"; exit 1
fi
AUTH="Authorization: Bearer $TOKEN"

echo "Ping..."
curl -s -H "$AUTH" "$BASE/ping" | python3 -m json.tool

echo "Settings: gitlab/naming/ref/blocking..."
curl -s -X POST "$BASE/settings/gitlab" -H "$AUTH" -H "Content-Type: application/json" -d '{"baseUrl":"https://gitlab.example.com","token":"abcd1234"}' | python3 -m json.tool
curl -s -X GET "$BASE/settings/gitlab" -H "$AUTH" | python3 -m json.tool
curl -s -X POST "$BASE/settings/naming" -H "$AUTH" -H "Content-Type: application/json" -d '{"featureTemplate":"feature/%s","releaseTemplate":"release/%s"}' | python3 -m json.tool
curl -s -X GET "$BASE/settings/naming" -H "$AUTH" | python3 -m json.tool
curl -s -X POST "$BASE/settings/ref" -H "$AUTH" | python3 -m json.tool
curl -s -X GET "$BASE/settings/ref" -H "$AUTH" | python3 -m json.tool
curl -s -X POST "$BASE/settings/blocking" -H "$AUTH" -H "Content-Type: application/json" -d '{"defaultPolicy":"ALLOW"}' | python3 -m json.tool
curl -s -X GET "$BASE/settings/blocking" -H "$AUTH" | python3 -m json.tool

echo "Create release window..."
RW=$(curl -s -X POST "$BASE/release-windows" -H "$AUTH" -H "Content-Type: application/json" -d '{"windowKey":"WK-EX","name":"RW-EX"}')
echo "$RW" | python3 -m json.tool
ID=$(echo "$RW" | grep -o '"id":"[^"]*' | cut -d'"' -f4)
if [ -z "$ID" ]; then echo "Failed to create release window"; exit 1; fi

if [[ "$OSTYPE" == "darwin"* ]]; then
  START=$(date -u -v-60S +"%Y-%m-%dT%H:%M:%SZ")
  END=$(date -u -v+3600S +"%Y-%m-%dT%H:%M:%SZ")
else
  START=$(date -u -d "-60 seconds" +"%Y-%m-%dT%H:%M:%SZ")
  END=$(date -u -d "+3600 seconds" +"%Y-%m-%dT%H:%M:%SZ")
fi
curl -s -X PUT "$BASE/release-windows/$ID/window" -H "$AUTH" -H "Content-Type: application/json" -d "{\"startAt\":\"$START\",\"endAt\":\"$END\"}" | python3 -m json.tool
curl -s -X POST "$BASE/release-windows/$ID/freeze" -H "$AUTH" | python3 -m json.tool
curl -s -X POST "$BASE/release-windows/$ID/publish" -H "$AUTH" | python3 -m json.tool

echo "Create iterations..."
IT1=$(curl -s -X POST "$BASE/iterations" -H "$AUTH" -H "Content-Type: application/json" -d '{"name":"迭代1","description":"d","repoIds":["repo-1","repo-2"]}')
echo "$IT1" | python3 -m json.tool
IT1_KEY=$(echo "$IT1" | grep -o '"key":"[^"]*' | cut -d'"' -f4)
IT2=$(curl -s -X POST "$BASE/iterations" -H "$AUTH" -H "Content-Type: application/json" -d '{"name":"迭代2","description":"d","repoIds":["repo-1"]}')
echo "$IT2" | python3 -m json.tool
IT2_KEY=$(echo "$IT2" | grep -o '"key":"[^"]*' | cut -d'"' -f4)

echo "Attach iterations to window..."
curl -s -X POST "$BASE/windows/$ID/attach" -H "$AUTH" -H "Content-Type: application/json" -d "{\"iterationKeys\":[\"$IT1_KEY\",\"$IT2_KEY\"]}" | python3 -m json.tool
echo "Plan..."
curl -s -X GET "$BASE/windows/$ID/plan" -H "$AUTH" | python3 -m json.tool
echo "Dry-Plan..."
curl -s -X GET "$BASE/windows/$ID/dry-plan" -H "$AUTH" | python3 -m json.tool

echo "Orchestrate..."
RUN=$(curl -s -X POST "$BASE/windows/$ID/orchestrate" -H "$AUTH" -H "Content-Type: application/json" -d '{"repoIds":["repo-1"],"iterationKeys":[],"failFast":true,"operator":"tester"}')
echo "$RUN" | python3 -m json.tool
RUN_ID=$(echo "$RUN" | grep -o '"data":"[^"]*' | cut -d'"' -f4)
if [ -z "$RUN_ID" ]; then echo "Failed to start orchestrate"; exit 1; fi

echo "Run list and detail..."
curl -s -X GET "$BASE/runs" -H "$AUTH" | python3 -m json.tool
curl -s -X GET "$BASE/runs/$RUN_ID" -H "$AUTH" | python3 -m json.tool
echo "Export JSON/CSV..."
curl -s -X GET "$BASE/runs/$RUN_ID/export.json" -H "$AUTH" | python3 -m json.tool
curl -s -X GET "$BASE/runs/$RUN_ID/export.csv" -H "$AUTH" | head -n 5

echo "Retry one item..."
ITEM="RW-EX::repo-1::IT-1"
RETRY=$(curl -s -X POST "$BASE/runs/$RUN_ID/retry" -H "$AUTH" -H "Content-Type: application/json" -d "{\"items\":[\"$ITEM\"],\"operator\":\"tester2\"}")
echo "$RETRY" | python3 -m json.tool
RETRY_ID=$(echo "$RETRY" | grep -o '"data":"[^"]*' | cut -d'"' -f4)
curl -s -X GET "$BASE/runs/$RETRY_ID/export.json" -H "$AUTH" | python3 -m json.tool

echo "Extended verification succeeded."
