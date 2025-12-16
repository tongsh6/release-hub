#!/bin/bash

# Ensure curl and python3 are available
command -v curl >/dev/null 2>&1 || { echo >&2 "curl required but not installed. Aborting."; exit 1; }
command -v python3 >/dev/null 2>&1 || { echo >&2 "python3 required but not installed. Aborting."; exit 1; }

BASE_URL="http://localhost:8080/api/release-windows"

echo "Checking if service is up..."
curl -s "${BASE_URL/release-windows/ping}" > /dev/null
if [ $? -ne 0 ]; then
    echo "Service is not reachable at localhost:8080. Please start it first."
    exit 1
fi

echo "Service is up. Starting verification..."

# 1. Create Release Window
echo "1. Creating Release Window..."
create_response=$(curl -s -X POST $BASE_URL \
  -H "Content-Type: application/json" \
  -d '{"name": "R-V2-TEST"}')
echo "Response: $create_response"

id=$(echo $create_response | grep -o '"id":"[^"]*' | cut -d'"' -f4)

if [ -z "$id" ]; then
  echo "Failed to extract ID"
  exit 1
fi
echo "Created ID: $id"

# 2. Submit
echo -e "\n2. Submitting..."
curl -s -X POST $BASE_URL/$id/submit | python3 -m json.tool

# 3. Configure Window (Current time window)
echo -e "\n3. Configuring Window..."
# Get UTC timestamps for start (now-60s) and end (now+3600s)
if [[ "$OSTYPE" == "darwin"* ]]; then
    start_at=$(date -u -v-60S +"%Y-%m-%dT%H:%M:%SZ")
    end_at=$(date -u -v+3600S +"%Y-%m-%dT%H:%M:%SZ")
else
    start_at=$(date -u -d "-60 seconds" +"%Y-%m-%dT%H:%M:%SZ")
    end_at=$(date -u -d "+3600 seconds" +"%Y-%m-%dT%H:%M:%SZ")
fi

echo "Setting window: $start_at to $end_at"

curl -s -X PUT $BASE_URL/$id/window \
  -H "Content-Type: application/json" \
  -d "{\"startAt\": \"$start_at\", \"endAt\": \"$end_at\"}" | python3 -m json.tool

# 4. Freeze
echo -e "\n4. Freezing..."
curl -s -X POST $BASE_URL/$id/freeze | python3 -m json.tool

# 5. Attempt Release (Should fail due to Frozen)
echo -e "\n5. Attempting Release (Expect Failure)..."
response=$(curl -s -X POST $BASE_URL/$id/release)
echo "$response" | python3 -m json.tool

if echo "$response" | grep -q "RW_FROZEN"; then
    echo "SUCCESS: Release blocked by frozen state."
else
    echo "FAIL: Release should have been blocked."
    exit 1
fi

# 6. Unfreeze
echo -e "\n6. Unfreezing..."
curl -s -X POST $BASE_URL/$id/unfreeze | python3 -m json.tool

# 7. Release (Should succeed)
echo -e "\n7. Releasing (Expect Success)..."
response=$(curl -s -X POST $BASE_URL/$id/release)
echo "$response" | python3 -m json.tool

if echo "$response" | grep -q "\"status\":\"RELEASED\""; then
    echo "SUCCESS: Released successfully."
else
    echo "FAIL: Release failed."
    exit 1
fi

# 8. Close
echo -e "\n8. Closing..."
curl -s -X POST $BASE_URL/$id/close | python3 -m json.tool

echo -e "\nVerification V2 Completed Successfully!"
