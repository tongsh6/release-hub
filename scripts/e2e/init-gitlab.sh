#!/bin/bash
set -e

GITLAB_URL="${GITLAB_URL:-http://localhost:9080}"
ROOT_PASS="${ROOT_PASS:-releasehub123}"
TEST_USER="${TEST_USER:-e2e-user}"
TEST_PASS="${TEST_PASS:-e2e-pass123}"

echo "=== Waiting for GitLab to be ready ==="
for i in $(seq 1 60); do
  if curl -s -o /dev/null -w "%{http_code}" "$GITLAB_URL/users/sign_in" | grep -q "200"; then
    echo "GitLab is ready"
    break
  fi
  echo "Waiting... ($i/60)"
  sleep 5
done

echo "=== Acquiring root OAuth token (GitLab 17 disabled basic auth on /api/v4) ==="
ROOT_TOKEN=$(curl -s -X POST "$GITLAB_URL/oauth/token" \
  -d "grant_type=password&username=root&password=$ROOT_PASS" \
  | grep -o '"access_token":"[^"]*"' | head -1 | cut -d'"' -f4)
if [ -z "$ROOT_TOKEN" ]; then
  echo "ERROR: Failed to acquire root OAuth token. Check ROOT_PASS."
  exit 1
fi
echo "ROOT_TOKEN acquired (${ROOT_TOKEN:0:8}...)"

echo "=== Creating E2E test user (idempotent) ==="
EXISTING_USER=$(curl -s -H "Authorization: Bearer $ROOT_TOKEN" \
  "$GITLAB_URL/api/v4/users?username=$TEST_USER" | grep -o '"id":[0-9]*' | head -1 | cut -d: -f2)
if [ -n "$EXISTING_USER" ]; then
  echo "User $TEST_USER already exists (id=$EXISTING_USER)"
  USER_ID=$EXISTING_USER
else
  CREATE_USER_RESP=$(curl -s --request POST \
    --header "Authorization: Bearer $ROOT_TOKEN" \
    --header "Content-Type: application/json" \
    --data "{\"name\":\"$TEST_USER\",\"username\":\"$TEST_USER\",\"password\":\"$TEST_PASS\",\"email\":\"$TEST_USER@e2e.test\",\"skip_confirmation\":true}" \
    "$GITLAB_URL/api/v4/users")
  USER_ID=$(echo "$CREATE_USER_RESP" | grep -o '"id":[0-9]*' | head -1 | cut -d: -f2)
  if [ -z "$USER_ID" ]; then
    echo "ERROR: Failed to create user. Response: $CREATE_USER_RESP"
    exit 1
  fi
  echo "User created (id=$USER_ID)"
fi

echo "=== Creating impersonation token for $TEST_USER (admin-only API) ==="
EXPIRES_AT=$(date -v+365d +%Y-%m-%d 2>/dev/null || date -d "+365 days" +%Y-%m-%d)
TOKEN_RESPONSE=$(curl -s --request POST \
  --header "Authorization: Bearer $ROOT_TOKEN" \
  --header "Content-Type: application/json" \
  --data "{\"name\":\"e2e-pat\",\"scopes\":[\"api\",\"read_repository\",\"write_repository\"],\"expires_at\":\"$EXPIRES_AT\"}" \
  "$GITLAB_URL/api/v4/users/$USER_ID/impersonation_tokens")

E2E_TOKEN=$(echo "$TOKEN_RESPONSE" | grep -o '"token":"[^"]*"' | head -1 | cut -d'"' -f4)
echo "E2E_TOKEN=${E2E_TOKEN:0:8}..."

if [ -z "$E2E_TOKEN" ]; then
  echo "ERROR: Failed to create impersonation token. Response: $TOKEN_RESPONSE"
  exit 1
fi

echo "=== Creating seed repositories (via root OAuth) ==="
# Use root token instead of impersonation — more reliable for project creation
create_repo() {
  local name=$1
  local resp=$(curl -s --request POST \
    --header "Content-Type: application/json" \
    --header "Authorization: Bearer $ROOT_TOKEN" \
    --data "{\"name\":\"$name\",\"visibility\":\"private\",\"initialize_with_readme\":false}" \
    "$GITLAB_URL/api/v4/projects")
  local pid=$(echo "$resp" | grep -o '"id":[0-9]*' | head -1 | cut -d: -f2)
  if [ -z "$pid" ]; then
    echo "WARNING: Failed to create repo $name. Response: $(echo $resp | head -c 200)"
  fi
  echo "$pid"
}

REPO1_ID=$(create_repo "seed-repo-1-maven")
REPO2_ID=$(create_repo "seed-repo-2-maven-multi")
REPO3_ID=$(create_repo "seed-repo-3-gradle")

echo "Repo IDs: REPO1=$REPO1_ID, REPO2=$REPO2_ID, REPO3=$REPO3_ID"

echo "=== Pushing seed files to repos ==="
push_file() {
  local repo_id=$1
  local file_path=$2
  local content=$3
  local encoded=$(echo -n "$content" | base64)
  curl -s --request POST \
    --header "PRIVATE-TOKEN: $E2E_TOKEN" \
    --header "Content-Type: application/json" \
    --data "{\"branch\":\"main\",\"content\":\"$encoded\",\"commit_message\":\"init: seed $file_path\",\"encoding\":\"base64\"}" \
    "$GITLAB_URL/api/v4/projects/$repo_id/repository/files/$(echo -n "$file_path" | sed 's/\//%2F/g')"
}

# seed-repo-1: Maven 单模块 pom.xml (version=1.4.0)
push_file "$REPO1_ID" "pom.xml" '<?xml version="1.0" encoding="UTF-8"?>
<project xmlns="http://maven.apache.org/POM/4.0.0"
         xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
         xsi:schemaLocation="http://maven.apache.org/POM/4.0.0 http://maven.apache.org/xsd/maven-4.0.0.xsd">
    <modelVersion>4.0.0</modelVersion>
    <groupId>com.e2e</groupId>
    <artifactId>seed-repo-1</artifactId>
    <version>1.4.0</version>
</project>'

# seed-repo-2: Maven 多模块 (version=2.1.0)
push_file "$REPO2_ID" "pom.xml" '<?xml version="1.0" encoding="UTF-8"?>
<project xmlns="http://maven.apache.org/POM/4.0.0"
         xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
         xsi:schemaLocation="http://maven.apache.org/POM/4.0.0 http://maven.apache.org/xsd/maven-4.0.0.xsd">
    <modelVersion>4.0.0</modelVersion>
    <groupId>com.e2e</groupId>
    <artifactId>seed-repo-2-parent</artifactId>
    <version>2.1.0</version>
    <packaging>pom</packaging>
    <modules><module>lib</module></modules>
</project>'

push_file "$REPO2_ID" "lib/pom.xml" '<?xml version="1.0" encoding="UTF-8"?>
<project xmlns="http://maven.apache.org/POM/4.0.0"
         xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
         xsi:schemaLocation="http://maven.apache.org/POM/4.0.0 http://maven.apache.org/xsd/maven-4.0.0.xsd">
    <modelVersion>4.0.0</modelVersion>
    <parent>
        <groupId>com.e2e</groupId>
        <artifactId>seed-repo-2-parent</artifactId>
        <version>2.1.0</version>
    </parent>
    <artifactId>seed-repo-2-lib</artifactId>
</project>'

# seed-repo-3: Gradle (version=3.0.0)
push_file "$REPO3_ID" "gradle.properties" 'version=3.0.0'

echo ""
echo "=== GitLab initialization complete ==="
echo "GitLab URL:      $GITLAB_URL"
echo "Test User:       $TEST_USER"
echo "Personal Token:  $E2E_TOKEN"
echo "Repo 1 (Maven):  http://$TEST_USER:$TEST_PASS@gitlab.local/$TEST_USER/seed-repo-1-maven.git (version=1.4.0)"
echo "Repo 2 (Multi):  http://$TEST_USER:$TEST_PASS@gitlab.local/$TEST_USER/seed-repo-2-maven-multi.git (version=2.1.0)"
echo "Repo 3 (Gradle): http://$TEST_USER:$TEST_PASS@gitlab.local/$TEST_USER/seed-repo-3-gradle.git (version=3.0.0)"

# Export for downstream scripts
export E2E_GITLAB_URL="$GITLAB_URL"
export E2E_GITLAB_TOKEN="$E2E_TOKEN"
export E2E_GITLAB_USER="$TEST_USER"

# Persist to file so test processes can source these values
cat > /tmp/e2e-gitlab.env << EOF
E2E_GITLAB_URL=$GITLAB_URL
E2E_GITLAB_TOKEN=$E2E_TOKEN
E2E_GITLAB_USER=$TEST_USER
EOF
echo "Token persisted to /tmp/e2e-gitlab.env"
