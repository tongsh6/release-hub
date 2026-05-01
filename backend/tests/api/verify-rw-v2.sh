#!/bin/bash

# Ensure curl and python3 are available
command -v curl >/dev/null 2>&1 || { echo >&2 "curl required but not installed. Aborting."; exit 1; }
command -v python3 >/dev/null 2>&1 || { echo >&2 "python3 required but not installed. Aborting."; exit 1; }

BASE_URL="http://localhost:8080/api/v1/release-windows"
AUTH_URL="http://localhost:8080/api/v1/auth/login"

echo "Checking if service is up..."
# Ping check might fail 401 but curl returns 0. If it fails connection refused, it returns non-zero.
for i in {1..30}; do
    curl -s "http://localhost:8080/actuator/health" > /dev/null
    if [ $? -eq 0 ]; then
        echo "Service is up!"
        break
    fi
    echo "Waiting for service... ($i/30)"
    sleep 2
done

curl -v "http://localhost:8080/actuator/health" > /dev/null
if [ $? -ne 0 ]; then
    echo "Service is not reachable at localhost:8080. Please start it first."
    exit 1
fi

echo "Service is up. Logging in..."
token_response=$(curl -s -X POST $AUTH_URL \
  -H "Content-Type: application/json" \
  -d '{"username": "admin", "password": "admin"}')
token=$(echo $token_response | grep -o '"token":"[^"]*' | cut -d'"' -f4)

if [ -z "$token" ]; then
  echo "Login failed. Response: $token_response"
  exit 1
fi
echo "Logged in. Token acquired."

AUTH_HEADER="Authorization: Bearer $token"

# 1. Create Release Window
echo "1. Creating Release Window..."
create_response=$(curl -s -X POST $BASE_URL \
  -H "Content-Type: application/json" \
  -H "$AUTH_HEADER" \
  -d '{"windowKey":"RW-V2-TEST","name":"R-V2-TEST"}')
echo "Response: $create_response"

id=$(echo $create_response | grep -o '"id":"[^"]*' | cut -d'"' -f4)

if [ -z "$id" ]; then
  echo "Failed to extract ID"
  exit 1
fi
echo "Created ID: $id"

# 2. Submit
echo -e "\n2. Publishing..."
curl -s -X POST $BASE_URL/$id/publish -H "$AUTH_HEADER" | python3 -m json.tool

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
  -H "$AUTH_HEADER" \
  -d "{\"startAt\": \"$start_at\", \"endAt\": \"$end_at\"}" | python3 -m json.tool

# 4. Freeze
echo -e "\n4. Freezing..."
curl -s -X POST $BASE_URL/$id/freeze -H "$AUTH_HEADER" | python3 -m json.tool

# 5. Publish
echo -e "\n5. Publishing..."
curl -s -X POST $BASE_URL/$id/publish -H "$AUTH_HEADER" | python3 -m json.tool

# 6. Release (Should succeed)
echo -e "\n6. Releasing (Expect Success)..."
response=$(curl -s -X POST $BASE_URL/$id/release -H "$AUTH_HEADER")
echo "$response" | python3 -m json.tool
if echo "$response" | grep -q "\"status\":\"RELEASED\""; then
    echo "SUCCESS: Released successfully."
else
    echo "FAIL: Release failed."
    exit 1
fi

# 7. Close
echo -e "\n7. Closing..."
curl -s -X POST $BASE_URL/$id/close -H "$AUTH_HEADER" | python3 -m json.tool

echo -e "\nVerification V2 Completed Successfully!"
