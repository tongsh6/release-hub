#!/bin/bash
# 批量版本更新功能测试脚本

set -e

BASE_URL="http://localhost:8080/api/v1"
TEST_REPO_PATH_BASE="/tmp/test-repo-batch"

echo "=== 批量版本更新功能测试 ==="
echo ""

# 1. 加载测试数据
if [ ! -f /tmp/test_data.env ]; then
    echo "❌ 测试数据文件不存在，请先运行 create_test_data.sh"
    exit 1
fi

source /tmp/test_data.env
echo "✅ 测试数据已加载"
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

# 3. 创建多个测试仓库
echo "3. 创建测试仓库..."
REPO_IDS=()
for i in 1 2; do
    REPO_RESPONSE=$(curl -s -X POST "$BASE_URL/repositories" \
      -H "Content-Type: application/json" \
      -H "Authorization: Bearer $TOKEN" \
      -d "{
        \"projectId\": \"test-project-$(date +%s)\",
        \"gitlabProjectId\": $((123456 + i)),
        \"name\": \"test-repo-batch-$i\",
        \"cloneUrl\": \"git@gitlab.com:test/repo-batch-$i.git\",
        \"defaultBranch\": \"main\",
        \"monoRepo\": false
      }")
    
    if echo "$REPO_RESPONSE" | python3 -c "import sys, json; data=json.load(sys.stdin); exit(0 if data.get('success') else 1)" 2>/dev/null; then
        REPO_ID=$(echo "$REPO_RESPONSE" | python3 -c "import sys, json; print(json.load(sys.stdin)['data']['id'])" 2>/dev/null || echo "")
        if [ -n "$REPO_ID" ]; then
            REPO_IDS+=("$REPO_ID")
            echo "✅ 仓库 $i 创建成功: $REPO_ID"
        fi
    else
        echo "❌ 仓库 $i 创建失败"
    fi
done

if [ ${#REPO_IDS[@]} -eq 0 ]; then
    echo "❌ 没有成功创建任何仓库"
    exit 1
fi

echo ""

# 4. 为每个仓库创建测试 pom.xml
echo "4. 创建测试 pom.xml 文件..."
for i in "${!REPO_IDS[@]}"; do
    REPO_PATH="$TEST_REPO_PATH_BASE-$((i+1))"
    mkdir -p "$REPO_PATH"
    cat > "$REPO_PATH/pom.xml" << EOF
<?xml version="1.0" encoding="UTF-8"?>
<project xmlns="http://maven.apache.org/POM/4.0.0"
         xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
         xsi:schemaLocation="http://maven.apache.org/POM/4.0.0 
         https://maven.apache.org/xsd/maven-4.0.0.xsd">
    <modelVersion>4.0.0</modelVersion>
    <groupId>com.test</groupId>
    <artifactId>test-project-batch-$((i+1))</artifactId>
    <version>1.0.0</version>
    <name>Test Project Batch $((i+1))</name>
</project>
EOF
    echo "✅ 创建 pom.xml: $REPO_PATH/pom.xml"
done
echo ""

# 5. 测试批量版本更新 API
echo "5. 测试批量版本更新 API..."
TARGET_VERSION="2.0.0"

# 构建批量更新请求
BATCH_REQUEST="{
  \"targetVersion\": \"$TARGET_VERSION\",
  \"repositories\": ["

for i in "${!REPO_IDS[@]}"; do
    if [ $i -gt 0 ]; then
        BATCH_REQUEST+=","
    fi
    REPO_PATH="$TEST_REPO_PATH_BASE-$((i+1))"
    BATCH_REQUEST+="
    {
      \"repoId\": \"${REPO_IDS[$i]}\",
      \"buildTool\": \"MAVEN\",
      \"repoPath\": \"$REPO_PATH\",
      \"pomPath\": \"$REPO_PATH/pom.xml\"
    }"
done

BATCH_REQUEST+="
  ]
}"

echo "请求内容:"
echo "$BATCH_REQUEST" | python3 -m json.tool
echo ""

BATCH_RESPONSE=$(curl -s -X POST "$BASE_URL/release-windows/$WINDOW_ID/execute/batch-version-update" \
  -H "Content-Type: application/json" \
  -H "Authorization: Bearer $TOKEN" \
  -d "$BATCH_REQUEST")

echo "Response:"
echo "$BATCH_RESPONSE" | python3 -m json.tool 2>/dev/null || echo "$BATCH_RESPONSE"
echo ""

# 检查响应
if echo "$BATCH_RESPONSE" | python3 -c "import sys, json; data=json.load(sys.stdin); exit(0 if data.get('success') else 1)" 2>/dev/null; then
    echo "✅ 批量版本更新 API 调用成功"
    RUN_ID=$(echo "$BATCH_RESPONSE" | python3 -c "import sys, json; print(json.load(sys.stdin)['data']['runId'])" 2>/dev/null || echo "")
    if [ -n "$RUN_ID" ]; then
        echo "   Run ID: $RUN_ID"
    fi
else
    echo "❌ 批量版本更新 API 调用失败"
    exit 1
fi
echo ""

# 6. 验证文件是否已更新
echo "6. 验证 pom.xml 文件是否已更新..."
sleep 2  # 等待文件更新

for i in "${!REPO_IDS[@]}"; do
    REPO_PATH="$TEST_REPO_PATH_BASE-$((i+1))"
    POM_FILE="$REPO_PATH/pom.xml"
    
    if [ -f "$POM_FILE" ]; then
        VERSION=$(grep '<version>' "$POM_FILE" | head -1 | sed 's/.*<version>\(.*\)<\/version>.*/\1/')
        echo "   仓库 $((i+1)): $VERSION"
        if [ "$VERSION" = "$TARGET_VERSION" ]; then
            echo "   ✅ 版本更新成功"
        else
            echo "   ⚠️  版本未更新（当前: $VERSION，期望: $TARGET_VERSION）"
        fi
    else
        echo "   ❌ pom.xml 文件不存在: $POM_FILE"
    fi
done
echo ""

# 7. 查询 Run 详情
if [ -n "$RUN_ID" ]; then
    echo "7. 查询 Run 详情..."
    RUN_DETAIL_RESPONSE=$(curl -s "$BASE_URL/runs/$RUN_ID/export.json" \
      -H "Authorization: Bearer $TOKEN")
    
    echo "Run 详情摘要:"
    echo "$RUN_DETAIL_RESPONSE" | python3 -c "
import sys, json
data = json.load(sys.stdin)
print(f\"Run ID: {data.get('runId')}\")
print(f\"Run Type: {data.get('runType')}\")
print(f\"Items Count: {len(data.get('items', []))}\")
for i, item in enumerate(data.get('items', []), 1):
    print(f\"  Item {i}: {item.get('finalResult')} - {item.get('repo')}\")
" 2>/dev/null || echo "$RUN_DETAIL_RESPONSE" | head -30
    echo ""
fi

echo "=== 测试完成 ==="
