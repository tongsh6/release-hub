#!/bin/bash
# 获取已创建的 VersionPolicy ID（通过日志）

echo "=== 获取 VersionPolicy ID ==="
echo ""

# 从日志中提取 VersionPolicy ID
POLICY_IDS=$(tail -200 /tmp/releasehub-backend-local.log | grep -i "created versionpolicy" | sed -n 's/.*Created VersionPolicy: \([^ ]*\).*/\1/p' | head -4)

if [ -z "$POLICY_IDS" ]; then
    echo "❌ 未找到 VersionPolicy ID，请检查服务是否正常启动"
    exit 1
fi

echo "找到的 VersionPolicy IDs:"
echo "$POLICY_IDS" | nl -w2 -s'. '
echo ""

# 保存第一个 MINOR policy ID（通常是第二个）
MINOR_POLICY_ID=$(echo "$POLICY_IDS" | sed -n '2p')
if [ -n "$MINOR_POLICY_ID" ]; then
    echo "使用 MINOR Policy ID: $MINOR_POLICY_ID"
    echo "export POLICY_ID=$MINOR_POLICY_ID" > /tmp/version_policy_id.env
    echo "✅ 已保存到: /tmp/version_policy_id.env"
else
    echo "⚠️  未找到 MINOR Policy ID"
fi
