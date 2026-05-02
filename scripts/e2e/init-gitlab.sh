#!/bin/bash
set -e

GITLAB_URL="${GITLAB_URL:-http://localhost:9080}"
ROOT_PASS="${ROOT_PASS:-releasehub123}"
TEST_USER="${TEST_USER:-e2e-user}"
TEST_PASS="${TEST_PASS:-e2e-pass123}"

echo "=== Waiting for GitLab to be ready ==="
for i in $(seq 1 60); do
  if curl -s -o /dev/null -w "%{http_code}" "$GITLAB_URL/-/health" | grep -q "200"; then
    echo "GitLab is ready"
    break
  fi
  echo "Waiting... ($i/60)"
  sleep 5
done

echo "=== Creating E2E test user ==="
CREATE_USER_RESP=$(curl -s --request POST \
  --header "Content-Type: application/json" \
  --data "{\"name\":\"$TEST_USER\",\"username\":\"$TEST_USER\",\"password\":\"$TEST_PASS\",\"email\":\"$TEST_USER@e2e.test\",\"skip_confirmation\":true}" \
  "$GITLAB_URL/api/v4/users" \
  --user "root:$ROOT_PASS")

echo "User creation response: $CREATE_USER_RESP"

echo "=== Creating personal access token for test user ==="
TOKEN_RESPONSE=$(curl -s --request POST \
  --header "Content-Type: application/json" \
  --data '{"name":"e2e-pat","scopes":["api","read_repository","write_repository"]}' \
  "$GITLAB_URL/api/v4/personal_access_tokens" \
  --user "$TEST_USER:$TEST_PASS")

E2E_TOKEN=$(echo "$TOKEN_RESPONSE" | grep -o '"token":"[^"]*"' | head -1 | cut -d'"' -f4)
echo "E2E_TOKEN=$E2E_TOKEN"

if [ -z "$E2E_TOKEN" ]; then
  echo "ERROR: Failed to create personal access token. Response: $TOKEN_RESPONSE"
  exit 1
fi

echo "=== Creating seed repositories ==="
create_repo() {
  local name=$1
  local resp=$(curl -s --request POST \
    --header "Content-Type: application/json" \
    --header "PRIVATE-TOKEN: $E2E_TOKEN" \
    --data "{\"name\":\"$name\",\"visibility\":\"private\",\"initialize_with_readme\":false}" \
    "$GITLAB_URL/api/v4/projects")
  echo "$resp" | grep -o '"id":[0-9]*' | head -1 | cut -d: -f2
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
