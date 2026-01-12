#!/bin/bash
# 创建测试数据种子脚本
# 用于版本更新功能测试

set -e

BASE_URL="http://localhost:8080/api/v1"
LOG_FILE="/tmp/create_test_data.log"

echo "=== 创建测试数据 ===" | tee "$LOG_FILE"
echo ""

# 1. 登录获取 token
echo "1. 登录获取 token..." | tee -a "$LOG_FILE"
LOGIN_RESPONSE=$(curl -s -X POST "$BASE_URL/auth/login" \
  -H "Content-Type: application/json" \
  -d '{"username":"admin","password":"admin"}')

TOKEN=$(echo $LOGIN_RESPONSE | python3 -c "import sys, json; print(json.load(sys.stdin)['data']['token'])" 2>/dev/null || echo "")

if [ -z "$TOKEN" ]; then
  echo "❌ 登录失败" | tee -a "$LOG_FILE"
  echo "Response: $LOGIN_RESPONSE" | tee -a "$LOG_FILE"
  exit 1
fi

echo "✅ 登录成功" | tee -a "$LOG_FILE"
echo ""

# 2. 创建测试项目（如果项目 API 存在）
echo "2. 创建测试项目..." | tee -a "$LOG_FILE"
PROJECT_ID=""

# 尝试使用项目 API（如果存在）
PROJECT_RESPONSE=$(curl -s -X POST "$BASE_URL/projects" \
  -H "Content-Type: application/json" \
  -H "Authorization: Bearer $TOKEN" \
  -d '{"name":"测试项目","description":"用于版本更新功能测试"}' 2>&1 || echo "")

if echo "$PROJECT_RESPONSE" | python3 -c "import sys, json; data=json.load(sys.stdin); exit(0 if data.get('success') else 1)" 2>/dev/null; then
  PROJECT_ID=$(echo "$PROJECT_RESPONSE" | python3 -c "import sys, json; print(json.load(sys.stdin)['data']['id'])" 2>/dev/null || echo "")
  if [ -n "$PROJECT_ID" ]; then
    echo "✅ 项目创建成功: $PROJECT_ID" | tee -a "$LOG_FILE"
  else
    echo "⚠️  项目 API 可能不存在，使用默认项目 ID" | tee -a "$LOG_FILE"
    PROJECT_ID="test-project-$(date +%s)"
  fi
else
  echo "⚠️  项目 API 不存在或失败，使用默认项目 ID" | tee -a "$LOG_FILE"
  PROJECT_ID="test-project-$(date +%s)"
fi
echo ""

# 3. 创建测试仓库
echo "3. 创建测试仓库..." | tee -a "$LOG_FILE"
REPO_RESPONSE=$(curl -s -X POST "$BASE_URL/repositories" \
  -H "Content-Type: application/json" \
  -H "Authorization: Bearer $TOKEN" \
  -d "{
    \"projectId\": \"$PROJECT_ID\",
    \"gitlabProjectId\": 123456,
    \"name\": \"test-repo-$(date +%s)\",
    \"cloneUrl\": \"git@gitlab.com:test/repo.git\",
    \"defaultBranch\": \"main\",
    \"monoRepo\": false
  }")

if echo "$REPO_RESPONSE" | python3 -c "import sys, json; data=json.load(sys.stdin); exit(0 if data.get('success') else 1)" 2>/dev/null; then
  REPO_ID=$(echo "$REPO_RESPONSE" | python3 -c "import sys, json; print(json.load(sys.stdin)['data']['id'])" 2>/dev/null || echo "")
  if [ -n "$REPO_ID" ]; then
    echo "✅ 仓库创建成功: $REPO_ID" | tee -a "$LOG_FILE"
  else
    echo "❌ 无法获取仓库 ID" | tee -a "$LOG_FILE"
    echo "Response: $REPO_RESPONSE" | tee -a "$LOG_FILE"
    exit 1
  fi
else
  echo "❌ 仓库创建失败" | tee -a "$LOG_FILE"
  echo "Response: $REPO_RESPONSE" | tee -a "$LOG_FILE"
  exit 1
fi
echo ""

# 4. 创建测试发布窗口
echo "4. 创建测试发布窗口..." | tee -a "$LOG_FILE"
WINDOW_RESPONSE=$(curl -s -X POST "$BASE_URL/release-windows" \
  -H "Content-Type: application/json" \
  -H "Authorization: Bearer $TOKEN" \
  -d "{
    \"windowKey\": \"TEST-VU-$(date +%s)\",
    \"name\": \"版本更新测试窗口\"
  }")

if echo "$WINDOW_RESPONSE" | python3 -c "import sys, json; data=json.load(sys.stdin); exit(0 if data.get('success') else 1)" 2>/dev/null; then
  WINDOW_ID=$(echo "$WINDOW_RESPONSE" | python3 -c "import sys, json; print(json.load(sys.stdin)['data']['id'])" 2>/dev/null || echo "")
  if [ -n "$WINDOW_ID" ]; then
    echo "✅ 发布窗口创建成功: $WINDOW_ID" | tee -a "$LOG_FILE"
  else
    echo "❌ 无法获取发布窗口 ID" | tee -a "$LOG_FILE"
    echo "Response: $WINDOW_RESPONSE" | tee -a "$LOG_FILE"
    exit 1
  fi
else
  echo "❌ 发布窗口创建失败" | tee -a "$LOG_FILE"
  echo "Response: $WINDOW_RESPONSE" | tee -a "$LOG_FILE"
  exit 1
fi
echo ""

# 5. 输出测试数据信息
echo "=== 测试数据创建完成 ===" | tee -a "$LOG_FILE"
echo "" | tee -a "$LOG_FILE"
echo "项目 ID: $PROJECT_ID" | tee -a "$LOG_FILE"
echo "仓库 ID: $REPO_ID" | tee -a "$LOG_FILE"
echo "发布窗口 ID: $WINDOW_ID" | tee -a "$LOG_FILE"
echo "" | tee -a "$LOG_FILE"

# 6. 保存到文件供后续使用
cat > /tmp/test_data.env << EOF
PROJECT_ID=$PROJECT_ID
REPO_ID=$REPO_ID
WINDOW_ID=$WINDOW_ID
TOKEN=$TOKEN
EOF

echo "测试数据已保存到: /tmp/test_data.env" | tee -a "$LOG_FILE"
echo "使用方式: source /tmp/test_data.env" | tee -a "$LOG_FILE"
echo "" | tee -a "$LOG_FILE"
echo "=== 完成 ===" | tee -a "$LOG_FILE"
