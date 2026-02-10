#!/bin/bash

# 1. Create Release Window
echo "Creating Release Window..."
create_response=$(curl -s -X POST http://localhost:8080/api/release-windows \
  -H "Content-Type: application/json" \
  -d '{"name": "R-2025-W01"}')
echo "Response: $create_response"

# Extract ID using simple grep/cut (assuming standard JSON format)
id=$(echo $create_response | grep -o '"id":"[^"]*' | cut -d'"' -f4)

if [ -z "$id" ]; then
  echo "Failed to extract ID"
  exit 1
fi

echo "Created ID: $id"

# 2. Get Release Window
echo -e "\nGetting Release Window..."
curl -s -X GET http://localhost:8080/api/release-windows/$id | python3 -m json.tool

# 3. Submit
echo -e "\n\nSubmitting..."
curl -s -X POST http://localhost:8080/api/release-windows/$id/submit | python3 -m json.tool

# 4. Release
echo -e "\n\nReleasing..."
curl -s -X POST http://localhost:8080/api/release-windows/$id/release | python3 -m json.tool

# 5. Close
echo -e "\n\nClosing..."
curl -s -X POST http://localhost:8080/api/release-windows/$id/close | python3 -m json.tool

# 6. List
echo -e "\n\nListing all..."
curl -s -X GET http://localhost:8080/api/release-windows | python3 -m json.tool

# 7. Final Verify
echo -e "\n\nFinal Status Check..."
curl -s -X GET http://localhost:8080/api/release-windows/$id | python3 -m json.tool
