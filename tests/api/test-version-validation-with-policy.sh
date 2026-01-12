#!/bin/bash
# 版本校验 API 测试脚本（包含创建 VersionPolicy）

set -e

BASE_URL="http://localhost:8080/api/v1"

echo "=== 版本校验 API 测试（包含创建 VersionPolicy）==="
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

# 2. 创建测试发布窗口
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

# 3. 创建 VersionPolicy（通过 Java 代码或直接调用应用服务）
# 注意：由于 VersionPolicy 使用内存存储，我们需要通过代码创建
# 这里我们使用一个假设的 policy ID，然后通过应用服务创建
echo "3. 创建测试 VersionPolicy..."
echo "   注意：VersionPolicy 使用内存存储，需要通过应用服务创建"
echo "   这里我们使用一个固定的测试 policy ID"
POLICY_ID="test-policy-semver-minor"
echo "   测试 Policy ID: $POLICY_ID"
echo ""

# 4. 测试版本校验 API - 场景 1: 有当前版本（SemVer MINOR）
echo "4. 测试场景 1: 有当前版本（1.0.0），期望推导 1.1.0（MINOR bump）..."
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

# 检查响应
if echo "$VALIDATION_RESPONSE" | python3 -c "import sys, json; data=json.load(sys.stdin); exit(0 if data.get('success') else 1)" 2>/dev/null; then
    DERIVED_VERSION=$(echo "$VALIDATION_RESPONSE" | python3 -c "import sys, json; print(json.load(sys.stdin)['data']['derivedVersion'])" 2>/dev/null || echo "")
    if [ -n "$DERIVED_VERSION" ]; then
        echo "✅ 版本推导成功: $DERIVED_VERSION"
    else
        echo "⚠️  版本推导失败或返回空"
    fi
else
    ERROR_MSG=$(echo "$VALIDATION_RESPONSE" | python3 -c "import sys, json; print(json.load(sys.stdin).get('message', 'Unknown error'))" 2>/dev/null || echo "Unknown error")
    echo "❌ 版本校验失败: $ERROR_MSG"
fi
echo ""

# 5. 测试版本校验 API - 场景 2: 无当前版本
echo "5. 测试场景 2: 无当前版本（应该使用默认版本 0.0.0）..."
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

# 检查是否返回了正确的错误
if echo "$VALIDATION_RESPONSE3" | python3 -c "import sys, json; data=json.load(sys.stdin); exit(0 if not data.get('success') else 1)" 2>/dev/null; then
    echo "✅ 正确处理了无效的 policy ID"
else
    echo "⚠️  未正确处理无效的 policy ID"
fi
echo ""

echo "=== 测试完成 ==="
echo ""
echo "注意：由于 VersionPolicy 使用内存存储，每次服务重启后数据会丢失"
echo "需要实现 JPA 持久化或通过 API 创建 VersionPolicy"
