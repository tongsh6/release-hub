#!/bin/bash
# 创建测试 VersionPolicy 数据（直接插入数据库）

set -e

echo "=== 创建测试 VersionPolicy ==="
echo ""

# 创建 SemVer 策略（MAJOR bump）
POLICY_ID_1=$(python3 -c "import uuid; print(str(uuid.uuid4()))")
POLICY_NAME_1="SemVer MAJOR"
SCHEME_1="SEMVER"
BUMP_RULE_1="MAJOR"

# 创建 SemVer 策略（MINOR bump）
POLICY_ID_2=$(python3 -c "import uuid; print(str(uuid.uuid4()))")
POLICY_NAME_2="SemVer MINOR"
SCHEME_2="SEMVER"
BUMP_RULE_2="MINOR"

# 创建 SemVer 策略（PATCH bump）
POLICY_ID_3=$(python3 -c "import uuid; print(str(uuid.uuid4()))")
POLICY_NAME_3="SemVer PATCH"
SCHEME_3="SEMVER"
BUMP_RULE_3="PATCH"

# 创建日期版本策略
POLICY_ID_4=$(python3 -c "import uuid; print(str(uuid.uuid4()))")
POLICY_NAME_4="Date Version"
SCHEME_4="DATE"
BUMP_RULE_4="NONE"

NOW=$(date -u +"%Y-%m-%d %H:%M:%S+00")

echo "创建 VersionPolicy 数据..."
docker exec postgres psql -U release_hub -d release_hub << EOF
-- 注意：如果 version_policy 表不存在，需要先创建表
-- 这里假设表已存在，如果不存在会报错

INSERT INTO version_policy (id, name, scheme, bump_rule, created_at, updated_at, version)
VALUES 
  ('$POLICY_ID_1', '$POLICY_NAME_1', '$SCHEME_1', '$BUMP_RULE_1', '$NOW', '$NOW', 0),
  ('$POLICY_ID_2', '$POLICY_NAME_2', '$SCHEME_2', '$BUMP_RULE_2', '$NOW', '$NOW', 0),
  ('$POLICY_ID_3', '$POLICY_NAME_3', '$SCHEME_3', '$BUMP_RULE_3', '$NOW', '$NOW', 0),
  ('$POLICY_ID_4', '$POLICY_NAME_4', '$SCHEME_4', '$BUMP_RULE_4', '$NOW', '$NOW', 0)
ON CONFLICT (id) DO NOTHING;
EOF

if [ $? -eq 0 ]; then
  echo "✅ VersionPolicy 创建成功"
  echo ""
  echo "Policy IDs:"
  echo "  SemVer MAJOR: $POLICY_ID_1"
  echo "  SemVer MINOR: $POLICY_ID_2"
  echo "  SemVer PATCH: $POLICY_ID_3"
  echo "  Date Version: $POLICY_ID_4"
  echo ""
  echo "保存到环境变量文件..."
  cat > /tmp/version_policy.env << EOF
POLICY_ID_MAJOR=$POLICY_ID_1
POLICY_ID_MINOR=$POLICY_ID_2
POLICY_ID_PATCH=$POLICY_ID_3
POLICY_ID_DATE=$POLICY_ID_4
EOF
  echo "✅ 已保存到: /tmp/version_policy.env"
else
  echo "❌ VersionPolicy 创建失败"
  echo "可能原因：version_policy 表不存在，需要先创建表结构"
  exit 1
fi
