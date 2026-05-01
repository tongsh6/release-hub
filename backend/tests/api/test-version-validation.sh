#!/bin/bash
# 版本校验 API 测试脚本

set -e

BASE_URL="http://localhost:8080/api/v1"

echo "=== 版本校验 API 测试 ==="
echo ""

# 1. 登录获取 token
echo "1. 登录获取 token..."
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

# 2. 创建测试发布窗口（用于 API 路径）
echo "2. 创建测试发布窗口..."
WINDOW_RESPONSE=$(curl -s -X POST "$BASE_URL/release-windows" \
  -H "Content-Type: application/json" \
  -H "Authorization: Bearer $TOKEN" \
  -d '{
    "windowKey": "TEST-VALIDATION-'$(date +%s)'",
    "name": "版本校验测试窗口"
  }')

if echo "$WINDOW_RESPONSE" | python3 -c "import sys, json; data=json.load(sys.stdin); exit(0 if data.get('success') else 1)" 2>/dev/null; then
  WINDOW_ID=$(echo "$WINDOW_RESPONSE" | python3 -c "import sys, json; print(json.load(sys.stdin)['data']['id'])" 2>/dev/null || echo "")
  if [ -n "$WINDOW_ID" ]; then
    echo "✅ 发布窗口创建成功: $WINDOW_ID"
  else
    echo "❌ 无法获取发布窗口 ID"
    exit 1
  fi
else
  echo "❌ 发布窗口创建失败"
  exit 1
fi
echo ""

# 3. 创建测试 VersionPolicy（如果 API 存在）
echo "3. 检查 VersionPolicy API..."
# 注意：VersionPolicy API 可能不存在，我们使用一个假设的 policy ID
POLICY_ID="test-policy-$(date +%s)"
echo "   使用测试 Policy ID: $POLICY_ID"
echo "   注意：如果 VersionPolicy API 不存在，测试将失败"
echo ""

# 4. 测试版本校验 API - 场景 1: 有当前版本
echo "4. 测试场景 1: 有当前版本（1.0.0）..."
VALIDATION_RESPONSE=$(curl -s -X POST "$BASE_URL/release-windows/$WINDOW_ID/validate" \
  -H "Content-Type: application/json" \
  -H "Authorization: Bearer $TOKEN" \
  -d "{
    \"policyId\": \"$POLICY_ID\",
    \"currentVersion\": \"1.0.0\"
  }")

echo "Response:"
echo "$VALIDATION_RESPONSE" | python3 -m json.tool 2>/dev/null || echo "$VALIDATION_RESPONSE"
echo ""

# 5. 测试版本校验 API - 场景 2: 无当前版本
echo "5. 测试场景 2: 无当前版本..."
VALIDATION_RESPONSE2=$(curl -s -X POST "$BASE_URL/release-windows/$WINDOW_ID/validate" \
  -H "Content-Type: application/json" \
  -H "Authorization: Bearer $TOKEN" \
  -d "{
    \"policyId\": \"$POLICY_ID\"
  }")

echo "Response:"
echo "$VALIDATION_RESPONSE2" | python3 -m json.tool 2>/dev/null || echo "$VALIDATION_RESPONSE2"
echo ""

# 6. 测试版本校验 API - 场景 3: 无效的 policy ID
echo "6. 测试场景 3: 无效的 policy ID..."
VALIDATION_RESPONSE3=$(curl -s -X POST "$BASE_URL/release-windows/$WINDOW_ID/validate" \
  -H "Content-Type: application/json" \
  -H "Authorization: Bearer $TOKEN" \
  -d '{
    "policyId": "non-existent-policy-id",
    "currentVersion": "1.0.0"
  }')

echo "Response:"
echo "$VALIDATION_RESPONSE3" | python3 -m json.tool 2>/dev/null || echo "$VALIDATION_RESPONSE3"
echo ""

echo "=== 测试完成 ==="
echo ""
echo "注意：如果 VersionPolicy API 不存在，需要先创建 VersionPolicy 才能进行完整测试"
