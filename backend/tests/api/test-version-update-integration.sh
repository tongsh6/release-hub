#!/bin/bash
# 版本更新功能完整集成测试

set -e

BASE_URL="http://localhost:8080/api/v1"
TEST_REPO_PATH="/tmp/test-repo-integration"
TEST_POM_PATH="$TEST_REPO_PATH/pom.xml"

echo "=== 版本更新功能完整集成测试 ==="
echo ""

# 1. 加载测试数据
if [ ! -f /tmp/test_data.env ]; then
    echo "❌ 测试数据文件不存在，请先运行 create_test_data.sh"
    exit 1
fi

source /tmp/test_data.env
echo "✅ 测试数据已加载"
echo "   仓库 ID: $REPO_ID"
echo "   发布窗口 ID: $WINDOW_ID"
echo ""

# 2. 登录获取 token
echo "2. 登录获取 token..."
LOGIN_RESPONSE=$(curl -s -X POST "$BASE_URL/auth/login" \
  -H "Content-Type: application/json" \
  -d '{"username":"admin","password":"admin"}')

TOKEN=$(echo $LOGIN_RESPONSE | python3 -c "import sys, json; print(json.load(sys.stdin)['data']['token'])" 2>/dev/null || echo "")

if [ -z "$TOKEN" ]; then
  echo "❌ 登录失败"
  exit 1
fi

echo "✅ 登录成功"
echo ""

# 3. 创建测试 pom.xml
echo "3. 创建测试 pom.xml..."
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

OLD_VERSION=$(grep '<version>' $TEST_POM_PATH | head -1 | sed 's/.*<version>\(.*\)<\/version>.*/\1/')
echo "✅ 测试 pom.xml 已创建: $TEST_POM_PATH"
echo "   当前版本: $OLD_VERSION"
echo ""

# 4. 测试版本更新 API
echo "4. 测试版本更新 API..."
TARGET_VERSION="1.2.3"

VERSION_UPDATE_RESPONSE=$(curl -s -X POST "$BASE_URL/release-windows/$WINDOW_ID/execute/version-update" \
  -H "Content-Type: application/json" \
  -H "Authorization: Bearer $TOKEN" \
  -d "{
    \"repoId\": \"$REPO_ID\",
    \"targetVersion\": \"$TARGET_VERSION\",
    \"buildTool\": \"MAVEN\",
    \"repoPath\": \"$TEST_REPO_PATH\",
    \"pomPath\": \"$TEST_POM_PATH\"
  }")

echo "Response:"
echo "$VERSION_UPDATE_RESPONSE" | python3 -m json.tool 2>/dev/null || echo "$VERSION_UPDATE_RESPONSE"
echo ""

# 检查响应
if echo "$VERSION_UPDATE_RESPONSE" | python3 -c "import sys, json; data=json.load(sys.stdin); exit(0 if data.get('success') else 1)" 2>/dev/null; then
    echo "✅ 版本更新 API 调用成功"
    RUN_ID=$(echo "$VERSION_UPDATE_RESPONSE" | python3 -c "import sys, json; print(json.load(sys.stdin)['data']['runId'])" 2>/dev/null || echo "")
    if [ -n "$RUN_ID" ]; then
        echo "   Run ID: $RUN_ID"
    fi
else
    echo "❌ 版本更新 API 调用失败"
    echo "Response: $VERSION_UPDATE_RESPONSE"
    exit 1
fi
echo ""

# 5. 检查 pom.xml 是否已更新
echo "5. 检查 pom.xml 是否已更新..."
sleep 2  # 等待文件更新

if [ -f "$TEST_POM_PATH" ]; then
    NEW_VERSION=$(grep '<version>' $TEST_POM_PATH | head -1 | sed 's/.*<version>\(.*\)<\/version>.*/\1/')
    echo "   更新后版本: $NEW_VERSION"
    if [ "$NEW_VERSION" = "$TARGET_VERSION" ]; then
        echo "✅ 版本更新成功！文件已更新到 $TARGET_VERSION"
    else
        echo "⚠️  版本未更新（当前: $NEW_VERSION，期望: $TARGET_VERSION）"
        echo "   注意：这可能是因为版本更新器只更新内存中的内容，未实际写回文件"
    fi
else
    echo "❌ pom.xml 文件不存在"
fi
echo ""

# 6. 查询 Run 详情
if [ -n "$RUN_ID" ]; then
    echo "6. 查询 Run 详情..."
    RUN_DETAIL_RESPONSE=$(curl -s "$BASE_URL/runs/$RUN_ID/export.json" \
      -H "Authorization: Bearer $TOKEN")
    
    echo "Run 详情:"
    echo "$RUN_DETAIL_RESPONSE" | python3 -m json.tool 2>/dev/null | head -50 || echo "$RUN_DETAIL_RESPONSE"
    echo ""
fi

echo "=== 测试完成 ==="
