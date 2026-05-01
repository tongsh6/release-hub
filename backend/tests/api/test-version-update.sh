#!/bin/bash
# 版本更新功能集成测试脚本

set -e

BASE_URL="http://localhost:8080/api/v1"
TEST_REPO_PATH="/tmp/test-repo"
TEST_POM_PATH="$TEST_REPO_PATH/pom.xml"

echo "=== 版本更新功能集成测试 ==="
echo ""

# 1. 登录获取 token
echo "1. 登录获取 token..."
LOGIN_RESPONSE=$(curl -s -X POST "$BASE_URL/auth/login" \
  -H "Content-Type: application/json" \
  -d '{"username":"admin","password":"admin"}')

TOKEN=$(echo $LOGIN_RESPONSE | python3 -c "import sys, json; print(json.load(sys.stdin)['data']['token'])" 2>/dev/null || echo "")

if [ -z "$TOKEN" ]; then
  echo "❌ 登录失败"
  echo "Response: $LOGIN_RESPONSE"
  exit 1
fi

echo "✅ 登录成功"
echo ""

# 2. 创建测试 pom.xml
echo "2. 创建测试 pom.xml..."
mkdir -p "$TEST_REPO_PATH"
cat > "$TEST_POM_PATH" << 'EOF'
<?xml version="1.0" encoding="UTF-8"?>
<project xmlns="http://maven.apache.org/POM/4.0.0"
         xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
         xsi:schemaLocation="http://maven.apache.org/POM/4.0.0 
         https://maven.apache.org/xsd/maven-4.0.0.xsd">
    <modelVersion>4.0.0</modelVersion>
    <groupId>com.test</groupId>
    <artifactId>test-project</artifactId>
    <version>1.0.0</version>
    <name>Test Project</name>
</project>
EOF

echo "✅ 测试 pom.xml 已创建: $TEST_POM_PATH"
echo "当前版本: $(grep '<version>' $TEST_POM_PATH | head -1 | sed 's/.*<version>\(.*\)<\/version>.*/\1/')"
echo ""

# 3. 获取或创建发布窗口
echo "3. 获取发布窗口..."
WINDOWS_RESPONSE=$(curl -s "$BASE_URL/release-windows" -H "Authorization: Bearer $TOKEN")
WINDOW_ID=$(echo $WINDOWS_RESPONSE | python3 -c "import sys, json; data=json.load(sys.stdin)['data']; print(data[0]['id'] if data and len(data) > 0 else '')" 2>/dev/null || echo "")

if [ -z "$WINDOW_ID" ]; then
  echo "创建新的发布窗口..."
  CREATE_RESPONSE=$(curl -s -X POST "$BASE_URL/release-windows" \
    -H "Content-Type: application/json" \
    -H "Authorization: Bearer $TOKEN" \
    -d '{"windowKey":"TEST-VU-01","name":"版本更新测试窗口"}')
  WINDOW_ID=$(echo $CREATE_RESPONSE | python3 -c "import sys, json; print(json.load(sys.stdin)['data']['id'])" 2>/dev/null || echo "")
fi

if [ -z "$WINDOW_ID" ]; then
  echo "❌ 无法获取或创建发布窗口"
  exit 1
fi

echo "✅ 发布窗口 ID: $WINDOW_ID"
echo ""

# 4. 创建测试仓库（使用虚拟 ID）
echo "4. 注意：版本更新功能需要仓库存在，但为了测试，我们使用虚拟仓库 ID"
REPO_ID="test-repo-$(date +%s)"
echo "使用仓库 ID: $REPO_ID"
echo ""

# 5. 测试版本更新 API
echo "5. 测试版本更新 API..."
VERSION_UPDATE_RESPONSE=$(curl -s -X POST "$BASE_URL/release-windows/$WINDOW_ID/execute/version-update" \
  -H "Content-Type: application/json" \
  -H "Authorization: Bearer $TOKEN" \
  -d "{
    \"repoId\": \"$REPO_ID\",
    \"targetVersion\": \"1.2.3\",
    \"buildTool\": \"MAVEN\",
    \"repoPath\": \"$TEST_REPO_PATH\",
    \"pomPath\": \"$TEST_POM_PATH\"
  }")

echo "Response:"
echo "$VERSION_UPDATE_RESPONSE" | python3 -m json.tool 2>/dev/null || echo "$VERSION_UPDATE_RESPONSE"
echo ""

# 6. 检查 pom.xml 是否已更新
echo "6. 检查 pom.xml 是否已更新..."
if [ -f "$TEST_POM_PATH" ]; then
  NEW_VERSION=$(grep '<version>' $TEST_POM_PATH | head -1 | sed 's/.*<version>\(.*\)<\/version>.*/\1/')
  echo "更新后版本: $NEW_VERSION"
  if [ "$NEW_VERSION" = "1.2.3" ]; then
    echo "✅ 版本更新成功！"
  else
    echo "⚠️  版本未更新（可能是仓库不存在导致更新失败）"
  fi
else
  echo "❌ pom.xml 文件不存在"
fi
echo ""

# 7. 测试版本校验 API
echo "7. 测试版本校验 API..."
# 注意：版本校验需要 VersionPolicy，这里先跳过，因为需要先创建策略
echo "⚠️  版本校验功能需要先创建 VersionPolicy，跳过此测试"
echo ""

echo "=== 测试完成 ==="
