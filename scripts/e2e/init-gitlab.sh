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

echo "=== Acquiring root OAuth token ==="
ROOT_TOKEN=$(curl -s -X POST "$GITLAB_URL/oauth/token" \
  -d "grant_type=password&username=root&password=$ROOT_PASS" \
  | python3 -c "import sys,json; print(json.load(sys.stdin)['access_token'])")
echo "ROOT_TOKEN acquired (${ROOT_TOKEN:0:8}...)"

echo "=== Ensuring E2E test user exists (idempotent) ==="
EXISTING_USER=$(curl -s -H "Authorization: Bearer $ROOT_TOKEN" \
  "$GITLAB_URL/api/v4/users?username=$TEST_USER" | python3 -c "
import sys,json
users = json.load(sys.stdin)
print(users[0]['id'] if users else '')
" 2>/dev/null)

if [ -n "$EXISTING_USER" ]; then
  echo "User $TEST_USER already exists (id=$EXISTING_USER)"
  USER_ID=$EXISTING_USER
else
  CREATE_USER_RESP=$(curl -s --request POST \
    --header "Authorization: Bearer $ROOT_TOKEN" \
    --header "Content-Type: application/json" \
    --data "{\"name\":\"$TEST_USER\",\"username\":\"$TEST_USER\",\"password\":\"$TEST_PASS\",\"email\":\"$TEST_USER@e2e.test\",\"skip_confirmation\":true}" \
    "$GITLAB_URL/api/v4/users")
  USER_ID=$(echo "$CREATE_USER_RESP" | python3 -c "import sys,json; print(json.load(sys.stdin)['id'])")
  echo "User created (id=$USER_ID)"
fi

echo "=== Creating impersonation token for $TEST_USER ==="
EXPIRES_AT="2027-05-01"

echo "Revoking old e2e-seed tokens..."
EXISTING_TOKENS=$(curl -s -H "Authorization: Bearer $ROOT_TOKEN" \
  "$GITLAB_URL/api/v4/users/$USER_ID/impersonation_tokens" | python3 -c "
import sys,json
for t in json.load(sys.stdin):
    if t.get('name') in ('e2e-seed', 'e2e-pat'):
        print(t['id'])
" 2>/dev/null)
for tid in $EXISTING_TOKENS; do
  curl -s -o /dev/null --request DELETE \
    --header "Authorization: Bearer $ROOT_TOKEN" \
    "$GITLAB_URL/api/v4/users/$USER_ID/impersonation_tokens/$tid"
  echo "  Revoked token id=$tid"
done

TOKEN_RESPONSE=$(curl -s --request POST \
  --header "Authorization: Bearer $ROOT_TOKEN" \
  --header "Content-Type: application/json" \
  --data "{\"name\":\"e2e-seed\",\"scopes\":[\"api\",\"read_repository\",\"write_repository\"],\"expires_at\":\"$EXPIRES_AT\"}" \
  "$GITLAB_URL/api/v4/users/$USER_ID/impersonation_tokens")

E2E_TOKEN=$(echo "$TOKEN_RESPONSE" | python3 -c "
import sys,json
d = json.load(sys.stdin)
if 'token' not in d:
    sys.stderr.write(f'Token creation failed: {d}\n')
    sys.exit(1)
print(d['token'])
")
echo "E2E_TOKEN=${E2E_TOKEN:0:8}..."

# ============================================================
# Create / reuse seed repositories via API, then seed via git
# ============================================================
echo ""
echo "=== Creating seed repositories under $TEST_USER namespace ==="

create_repo() {
  local name=$1
  local existing
  existing=$(curl -s -H "PRIVATE-TOKEN: $E2E_TOKEN" \
    "$GITLAB_URL/api/v4/projects?search=$name&owned=true" | python3 -c "
import sys,json
for p in json.load(sys.stdin or '[]'):
    if p.get('name') == '$name': print(p['id']); break
" 2>/dev/null)

  if [ -n "$existing" ]; then
    echo "  Repo $name already exists (id=$existing), reusing" >&2
    echo "$existing"
    return
  fi

  local resp
  resp=$(curl -s --request POST \
    --header "Content-Type: application/json" \
    --header "PRIVATE-TOKEN: $E2E_TOKEN" \
    --data "{\"name\":\"$name\",\"visibility\":\"private\",\"initialize_with_readme\":true}" \
    "$GITLAB_URL/api/v4/projects")
  local pid
  pid=$(echo "$resp" | python3 -c "import sys,json; print(json.load(sys.stdin)['id'])")
  echo "  Created repo $name (id=$pid)" >&2
  echo "$pid"
}

REPO1_ID=$(create_repo "seed-repo-1-maven")
REPO2_ID=$(create_repo "seed-repo-2-maven-multi")
REPO3_ID=$(create_repo "seed-repo-3-gradle")

# Unprotect main branch so e2e-user can push directly
echo ""
echo "=== Unprotecting main branches ==="
for rid in $REPO1_ID $REPO2_ID $REPO3_ID; do
  curl -s -o /dev/null -w "  Repo $rid: HTTP %{http_code}\n" --request DELETE \
    --header "Authorization: Bearer $ROOT_TOKEN" \
    "$GITLAB_URL/api/v4/projects/$rid/protected_branches/main"
done

# ============================================================
# Seed repository files and feature branches via API
# ============================================================
echo ""
echo "=== Seeding repo files and feature branches via API ==="

GITLAB_HOST_PORT=$(echo "$GITLAB_URL" | sed 's|^https\?://||')
CLONE_URL_REPO1="http://$GITLAB_HOST_PORT/$TEST_USER/seed-repo-1-maven.git"
CLONE_URL_REPO2="http://$GITLAB_HOST_PORT/$TEST_USER/seed-repo-2-maven-multi.git"
CLONE_URL_REPO3="http://$GITLAB_HOST_PORT/$TEST_USER/seed-repo-3-gradle.git"

# Helper: create a file in a repo via GitLab API (base64 encoding to avoid JSON escaping issues)
api_create_file() {
  local pid=$1 branch=$2 path=$3 content=$4 msg=$5
  local encoded_path=$(echo -n "$path" | python3 -c "import sys,urllib.parse; print(urllib.parse.quote(sys.stdin.read()))")
  local b64_content=$(echo -n "$content" | base64)
  curl -s -o /dev/null -w "%{http_code}" --request POST \
    -H "PRIVATE-TOKEN: $E2E_TOKEN" \
    -H "Content-Type: application/json" \
    -d "{\"branch\":\"$branch\",\"encoding\":\"base64\",\"content\":\"$b64_content\",\"commit_message\":\"$msg\"}" \
    "$GITLAB_URL/api/v4/projects/$pid/repository/files/$encoded_path"
}

# Helper: create a branch via API
api_create_branch() {
  local pid=$1 branch=$2 ref=$3
  curl -s -o /dev/null -w "%{http_code}" --request POST \
    -H "PRIVATE-TOKEN: $E2E_TOKEN" \
    -H "Content-Type: application/json" \
    -d "{\"branch\":\"$branch\",\"ref\":\"$ref\"}" \
    "$GITLAB_URL/api/v4/projects/$pid/repository/branches"
}

# Check if repo already has content (non-empty tree) — idempotent guard
repo_has_content() {
  local pid=$1
  local count=$(curl -s -H "PRIVATE-TOKEN: $E2E_TOKEN" \
    "$GITLAB_URL/api/v4/projects/$pid/repository/tree?ref=main&per_page=1" | \
    python3 -c "import sys,json; data=json.load(sys.stdin); print(len(data) if isinstance(data, list) else 0)" 2>/dev/null)
  [ -n "$count" ] && [ "$count" -gt 0 ]
}

# ---- Repo 1: Maven single module ----
echo "--- Repo 1: Maven single module ---"
if repo_has_content "$REPO1_ID"; then
  echo "  Repo 1 already has content, skipping seed"
else
  # Main branch: pom.xml v1.4.0
  api_create_file "$REPO1_ID" "main" "pom.xml" \
    '<?xml version="1.0" encoding="UTF-8"?><project xmlns="http://maven.apache.org/POM/4.0.0"><modelVersion>4.0.0</modelVersion><groupId>com.e2e</groupId><artifactId>seed-repo-1</artifactId><version>1.4.0</version></project>' \
    "seed: add pom.xml v1.4.0" > /dev/null

  # feature/upgrade-guava: pom.xml v1.5.0
  api_create_branch "$REPO1_ID" "feature/upgrade-guava" "main" > /dev/null
  api_create_file "$REPO1_ID" "feature/upgrade-guava" "pom.xml" \
    '<?xml version="1.0" encoding="UTF-8"?><project xmlns="http://maven.apache.org/POM/4.0.0"><modelVersion>4.0.0</modelVersion><groupId>com.e2e</groupId><artifactId>seed-repo-1</artifactId><version>1.5.0</version></project>' \
    "feat: bump to 1.5.0" > /dev/null

  # feature/add-logging: pom.xml v1.4.1
  api_create_branch "$REPO1_ID" "feature/add-logging" "main" > /dev/null
  api_create_file "$REPO1_ID" "feature/add-logging" "pom.xml" \
    '<?xml version="1.0" encoding="UTF-8"?><project xmlns="http://maven.apache.org/POM/4.0.0"><modelVersion>4.0.0</modelVersion><groupId>com.e2e</groupId><artifactId>seed-repo-1</artifactId><version>1.4.1</version></project>' \
    "feat: bump to 1.4.1" > /dev/null

  echo "  Repo 1 seeded (3 branches)"
fi

# ---- Repo 2: Maven multi-module ----
echo "--- Repo 2: Maven multi-module ---"
if repo_has_content "$REPO2_ID"; then
  echo "  Repo 2 already has content, skipping seed"
else
  api_create_file "$REPO2_ID" "main" "pom.xml" \
    '<?xml version="1.0" encoding="UTF-8"?><project xmlns="http://maven.apache.org/POM/4.0.0"><modelVersion>4.0.0</modelVersion><groupId>com.e2e</groupId><artifactId>seed-repo-2-parent</artifactId><version>2.1.0</version><packaging>pom</packaging></project>' \
    "seed: add parent pom v2.1.0" > /dev/null

  api_create_branch "$REPO2_ID" "feature/update-lib" "main" > /dev/null
  api_create_file "$REPO2_ID" "feature/update-lib" "pom.xml" \
    '<?xml version="1.0" encoding="UTF-8"?><project xmlns="http://maven.apache.org/POM/4.0.0"><modelVersion>4.0.0</modelVersion><groupId>com.e2e</groupId><artifactId>seed-repo-2-parent</artifactId><version>2.2.0</version><packaging>pom</packaging></project>' \
    "feat: bump to 2.2.0" > /dev/null

  echo "  Repo 2 seeded (2 branches)"
fi

# ---- Repo 3: Gradle ----
echo "--- Repo 3: Gradle ---"
if repo_has_content "$REPO3_ID"; then
  echo "  Repo 3 already has content, skipping seed"
else
  api_create_file "$REPO3_ID" "main" "build.gradle" \
    'plugins { id("java") }
group = "com.e2e"
version = "3.0.0"' \
    "seed: add build.gradle v3.0.0" > /dev/null

  api_create_branch "$REPO3_ID" "feature/kotlin-support" "main" > /dev/null
  api_create_file "$REPO3_ID" "feature/kotlin-support" "gradle.properties" \
    "version=3.1.0" \
    "feat: add gradle.properties v3.1.0" > /dev/null
  api_create_file "$REPO3_ID" "feature/kotlin-support" "build.gradle" \
    'plugins { id("java") }
group = "com.e2e"
version = "3.1.0"' \
    "feat: bump build.gradle to 3.1.0" > /dev/null

  echo "  Repo 3 seeded (2 branches)"
fi

# ============================================================
# Summary & export
# ============================================================
echo ""
echo "=== GitLab initialization complete ==="
echo "GitLab URL:      $GITLAB_URL"
echo "Test User:       $TEST_USER"
echo "Personal Token:  $E2E_TOKEN"
echo ""
echo "Repo 1 (Maven):  $CLONE_URL_REPO1"
echo "  main:                  v1.4.0 (pom.xml + App.java)"
echo "  feature/upgrade-guava: v1.5.0 (guava 33.0.0) — 1 diff"
echo "  feature/add-logging:   v1.4.1 (+slf4j + Logger.java) — 2 diffs"
echo ""
echo "Repo 2 (Multi):  $CLONE_URL_REPO2"
echo "  main:              v2.1.0 (parent + lib module)"
echo "  feature/update-lib:  v2.2.0 (LibUtil.add()) — 3 diffs"
echo ""
echo "Repo 3 (Gradle): $CLONE_URL_REPO3"
echo "  main:                 v3.0.0 (java plugin)"
echo "  feature/kotlin-support: v3.1.0 (+kotlin plugin) — 3 diffs"

cat > /tmp/e2e-gitlab.env << EOF
E2E_GITLAB_URL=$GITLAB_URL
E2E_GITLAB_TOKEN=$E2E_TOKEN
E2E_GITLAB_USER=$TEST_USER
E2E_REPO1_CLONE_URL=$CLONE_URL_REPO1
E2E_REPO2_CLONE_URL=$CLONE_URL_REPO2
E2E_REPO3_CLONE_URL=$CLONE_URL_REPO3
EOF
echo ""
echo "Env persisted to /tmp/e2e-gitlab.env"
