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
    echo "  Repo $name already exists (id=$existing), reusing"
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
  echo "  Created repo $name (id=$pid)"
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
# Clone, seed files, create feature branches, push via git
# ============================================================
echo ""
echo "=== Cloning repos and seeding via git ==="

GITLAB_HOST_PORT=$(echo "$GITLAB_URL" | sed 's|^https\?://||')
CLONE_BASE="http://oauth2:${E2E_TOKEN}@${GITLAB_HOST_PORT}/${TEST_USER}"
SEED_DIR="/tmp/e2e-seed-repos"
rm -rf "$SEED_DIR"
mkdir -p "$SEED_DIR"

CLONE_URL_REPO1="http://$GITLAB_HOST_PORT/$TEST_USER/seed-repo-1-maven.git"
CLONE_URL_REPO2="http://$GITLAB_HOST_PORT/$TEST_USER/seed-repo-2-maven-multi.git"
CLONE_URL_REPO3="http://$GITLAB_HOST_PORT/$TEST_USER/seed-repo-3-gradle.git"

# ---- Repo 1: Maven single module ----
echo "--- Repo 1: Maven single module ---"
git clone "$CLONE_BASE/seed-repo-1-maven.git" "$SEED_DIR/repo1" 2>&1 | tail -1
cd "$SEED_DIR/repo1"

mkdir -p src/main/java/com/e2e
cat > pom.xml << 'XMLEOF'
<?xml version="1.0" encoding="UTF-8"?>
<project xmlns="http://maven.apache.org/POM/4.0.0"
         xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
         xsi:schemaLocation="http://maven.apache.org/POM/4.0.0 http://maven.apache.org/xsd/maven-4.0.0.xsd">
    <modelVersion>4.0.0</modelVersion>
    <groupId>com.e2e</groupId>
    <artifactId>seed-repo-1</artifactId>
    <version>1.4.0</version>
    <dependencies>
        <dependency>
            <groupId>com.google.guava</groupId>
            <artifactId>guava</artifactId>
            <version>31.1-jre</version>
        </dependency>
    </dependencies>
</project>
XMLEOF

cat > src/main/java/com/e2e/App.java << 'JAVAEOF'
package com.e2e;

public class App {
    public static void main(String[] args) {
        System.out.println("Seed App v1.4.0");
    }
}
JAVAEOF

git add -A && git commit -m "seed: main branch baseline (v1.4.0)" --allow-empty 2>&1 | tail -1
git push origin main 2>&1 | tail -1

# feature/upgrade-guava
git checkout -b feature/upgrade-guava main
cat > pom.xml << 'XMLEOF'
<?xml version="1.0" encoding="UTF-8"?>
<project xmlns="http://maven.apache.org/POM/4.0.0"
         xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
         xsi:schemaLocation="http://maven.apache.org/POM/4.0.0 http://maven.apache.org/xsd/maven-4.0.0.xsd">
    <modelVersion>4.0.0</modelVersion>
    <groupId>com.e2e</groupId>
    <artifactId>seed-repo-1</artifactId>
    <version>1.5.0</version>
    <dependencies>
        <dependency>
            <groupId>com.google.guava</groupId>
            <artifactId>guava</artifactId>
            <version>33.0.0-jre</version>
        </dependency>
    </dependencies>
</project>
XMLEOF

git add pom.xml && git commit -m "feat: upgrade guava to 33.0.0, bump version to 1.5.0" 2>&1 | tail -1
git push origin feature/upgrade-guava 2>&1 | tail -1

# feature/add-logging
git checkout -b feature/add-logging main
cat > pom.xml << 'XMLEOF'
<?xml version="1.0" encoding="UTF-8"?>
<project xmlns="http://maven.apache.org/POM/4.0.0"
         xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
         xsi:schemaLocation="http://maven.apache.org/POM/4.0.0 http://maven.apache.org/xsd/maven-4.0.0.xsd">
    <modelVersion>4.0.0</modelVersion>
    <groupId>com.e2e</groupId>
    <artifactId>seed-repo-1</artifactId>
    <version>1.4.1</version>
    <dependencies>
        <dependency>
            <groupId>com.google.guava</groupId>
            <artifactId>guava</artifactId>
            <version>31.1-jre</version>
        </dependency>
        <dependency>
            <groupId>org.slf4j</groupId>
            <artifactId>slf4j-api</artifactId>
            <version>2.0.9</version>
        </dependency>
    </dependencies>
</project>
XMLEOF

cat > src/main/java/com/e2e/Logger.java << 'JAVAEOF'
package com.e2e;

public class AppLogger {
    public static void info(String msg) {
        System.out.println("[INFO] " + msg);
    }
}
JAVAEOF

git add -A && git commit -m "feat: add slf4j logging, bump version to 1.4.1" 2>&1 | tail -1
git push origin feature/add-logging 2>&1 | tail -1

echo "  Repo 1 done"

# ---- Repo 2: Maven multi-module ----
echo "--- Repo 2: Maven multi-module ---"
git clone "$CLONE_BASE/seed-repo-2-maven-multi.git" "$SEED_DIR/repo2" 2>&1 | tail -1
cd "$SEED_DIR/repo2"

mkdir -p lib/src/main/java/com/e2e/lib
cat > lib/src/main/java/com/e2e/lib/LibUtil.java << 'JAVAEOF'
package com.e2e.lib;

public class LibUtil {
    public static String greet(String name) {
        return "Hello, " + name + "!";
    }
}
JAVAEOF

git add -A && git commit -m "seed: add LibUtil.java" --allow-empty 2>&1 | tail -1
git push origin main 2>&1 | tail -1

# feature/update-lib
git checkout -b feature/update-lib main
cat > pom.xml << 'XMLEOF'
<?xml version="1.0" encoding="UTF-8"?>
<project xmlns="http://maven.apache.org/POM/4.0.0"
         xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
         xsi:schemaLocation="http://maven.apache.org/POM/4.0.0 http://maven.apache.org/xsd/maven-4.0.0.xsd">
    <modelVersion>4.0.0</modelVersion>
    <groupId>com.e2e</groupId>
    <artifactId>seed-repo-2-parent</artifactId>
    <version>2.2.0</version>
    <packaging>pom</packaging>
    <modules><module>lib</module></modules>
</project>
XMLEOF

cat > lib/pom.xml << 'XMLEOF'
<?xml version="1.0" encoding="UTF-8"?>
<project xmlns="http://maven.apache.org/POM/4.0.0"
         xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
         xsi:schemaLocation="http://maven.apache.org/POM/4.0.0 http://maven.apache.org/xsd/maven-4.0.0.xsd">
    <modelVersion>4.0.0</modelVersion>
    <parent>
        <groupId>com.e2e</groupId>
        <artifactId>seed-repo-2-parent</artifactId>
        <version>2.2.0</version>
    </parent>
    <artifactId>seed-repo-2-lib</artifactId>
</project>
XMLEOF

cat > lib/src/main/java/com/e2e/lib/LibUtil.java << 'JAVAEOF'
package com.e2e.lib;

public class LibUtil {
    public static String greet(String name) {
        return "Hello, " + name + "! (v2.2.0)";
    }

    public static int add(int a, int b) {
        return a + b;
    }
}
JAVAEOF

git add -A && git commit -m "feat: bump version to 2.2.0, add LibUtil.add()" 2>&1 | tail -1
git push origin feature/update-lib 2>&1 | tail -1

echo "  Repo 2 done"

# ---- Repo 3: Gradle ----
echo "--- Repo 3: Gradle ---"
git clone "$CLONE_BASE/seed-repo-3-gradle.git" "$SEED_DIR/repo3" 2>&1 | tail -1
cd "$SEED_DIR/repo3"

mkdir -p src/main/java/com/e2e/gradle
cat > build.gradle << 'GRADLEOF'
plugins {
    id("java")
}

group = "com.e2e"
version = "3.0.0"

repositories {
    mavenCentral()
}

dependencies {
    testImplementation("org.junit.jupiter:junit-jupiter:5.10.0")
}
GRADLEOF

cat > src/main/java/com/e2e/gradle/GradleApp.java << 'JAVAEOF'
package com.e2e.gradle;

public class GradleApp {
    public static void main(String[] args) {
        System.out.println("Gradle App v3.0.0");
    }
}
JAVAEOF

git add -A && git commit -m "seed: add build.gradle and GradleApp.java" --allow-empty 2>&1 | tail -1
git push origin main 2>&1 | tail -1

# feature/kotlin-support
git checkout -b feature/kotlin-support main
cat > gradle.properties << 'PROPEOF'
version=3.1.0
PROPEOF

cat > build.gradle << 'GRADLEOF'
plugins {
    id("java")
    id("org.jetbrains.kotlin.jvm") version "1.9.20"
}

group = "com.e2e"
version = "3.1.0"

repositories {
    mavenCentral()
}

dependencies {
    testImplementation("org.junit.jupiter:junit-jupiter:5.10.0")
    implementation("org.jetbrains.kotlin:kotlin-stdlib:1.9.20")
}
GRADLEOF

cat > src/main/java/com/e2e/gradle/GradleApp.java << 'JAVAEOF'
package com.e2e.gradle;

public class GradleApp {
    public static void main(String[] args) {
        System.out.println("Gradle App v3.1.0 (+Kotlin support)");
    }
}
JAVAEOF

git add -A && git commit -m "feat: add Kotlin plugin, bump version to 3.1.0" 2>&1 | tail -1
git push origin feature/kotlin-support 2>&1 | tail -1

echo "  Repo 3 done"

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
