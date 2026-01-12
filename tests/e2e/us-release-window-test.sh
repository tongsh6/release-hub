#!/bin/bash
# ============================================================
# US-RW: 发布窗口管理用户故事测试
# ============================================================

# 不使用 set -e，让测试继续运行
# set -e

source "$(dirname "$0")/test_utils.sh"

# ============================================================
# US-RW-001: 创建发布窗口
# ============================================================

test_us_rw_001() {
    log_section "US-RW-001: 创建发布窗口"
    
    echo "场景 1: 创建带有效 key 和 name 的发布窗口"
    echo "  Given 我已登录系统"
    echo "  When  我创建发布窗口 windowKey='RW-001' name='测试窗口'"
    echo "  Then  创建成功，状态为 DRAFT"
    
    local key="RW-$(date +%s)"
    local response=$(api_post "/release-windows" "{
        \"windowKey\": \"$key\",
        \"name\": \"测试发布窗口\"
    }")
    
    local id=$(json_get "$response" ".data.id")
    local status=$(json_get "$response" ".data.status")
    
    assert_not_empty "$id" "  ✓ 创建成功，获取到 ID"
    assert_equals "DRAFT" "$status" "  ✓ 初始状态为 DRAFT"
    
    # 保存 ID 供后续测试使用
    echo "$id" > /tmp/test_window_id
    echo "$key" > /tmp/test_window_key
    
    echo ""
    echo "场景 2: 创建后可配置时间窗口"
    echo "  Given 发布窗口已创建"
    echo "  When  我配置 startAt 和 endAt"
    echo "  Then  配置成功"
    
    local start_at=$(date -u +"%Y-%m-%dT%H:%M:%SZ")
    local end_at=$(date -u -v+7d +"%Y-%m-%dT%H:%M:%SZ" 2>/dev/null || date -u -d "+7 days" +"%Y-%m-%dT%H:%M:%SZ")
    
    response=$(api_put "/release-windows/$id/window" "{
        \"startAt\": \"$start_at\",
        \"endAt\": \"$end_at\"
    }")
    
    local new_start=$(json_get "$response" ".data.startAt")
    assert_not_empty "$new_start" "  ✓ 时间配置成功"
    
    echo ""
    echo "场景 3: startAt 必须早于 endAt"
    echo "  Given 发布窗口已创建"
    echo "  When  我配置 startAt > endAt"
    echo "  Then  返回错误"
    
    response=$(api_put "/release-windows/$id/window" "{
        \"startAt\": \"$end_at\",
        \"endAt\": \"$start_at\"
    }")
    
    local code=$(json_get "$response" "code")
    if [ "$code" != "OK" ] && [ "$code" != "0" ]; then
        log_success "  ✓ 时间校验正确，拒绝无效配置"
    else
        log_fail "  ✗ 应该拒绝 startAt > endAt"
    fi
}

# ============================================================
# US-RW-002: 发布窗口状态流转
# ============================================================

test_us_rw_002() {
    log_section "US-RW-002: 发布窗口状态流转"
    
    local id=$(cat /tmp/test_window_id 2>/dev/null || echo "")
    if [ -z "$id" ]; then
        log_skip "无测试窗口 ID，跳过状态流转测试"
        return
    fi
    
    echo "场景 1: DRAFT → PUBLISHED"
    echo "  Given 窗口状态为 DRAFT 且已配置时间"
    echo "  When  我点击发布"
    echo "  Then  状态变更为 PUBLISHED"
    
    local response=$(api_post "/release-windows/$id/publish" "{}")
    local status=$(json_get "$response" ".data.status")
    
    assert_equals "PUBLISHED" "$status" "  ✓ 状态变更为 PUBLISHED"
    
    echo ""
    echo "场景 2: 已发布后可执行版本更新"
    echo "  Given 窗口状态为 PUBLISHED"
    echo "  Then  版本更新 API 可用"
    
    # 版本更新 API 可用性测试
    response=$(api_post "/release-windows/$id/execute/version-update" "{
        \"repoId\": \"test-repo\",
        \"targetVersion\": \"1.0.0\",
        \"buildTool\": \"MAVEN\",
        \"repoPath\": \"/tmp/test\"
    }")
    
    # 即使失败（仓库不存在），API 也应该可调用
    local code=$(json_get "$response" "code")
    if [ -n "$code" ]; then
        log_success "  ✓ 版本更新 API 可调用"
    fi
}

# ============================================================
# US-RW-003: 冻结/解冻发布窗口
# ============================================================

test_us_rw_003() {
    log_section "US-RW-003: 冻结/解冻发布窗口"
    
    local id=$(cat /tmp/test_window_id 2>/dev/null || echo "")
    if [ -z "$id" ]; then
        log_skip "无测试窗口 ID，跳过冻结测试"
        return
    fi
    
    echo "场景 1: 冻结发布窗口"
    echo "  Given 窗口未冻结"
    echo "  When  我点击冻结"
    echo "  Then  窗口变为冻结状态"
    
    local response=$(api_post "/release-windows/$id/freeze" "{}")
    local frozen=$(json_get "$response" ".data.frozen")
    
    if [ "$frozen" = "True" ] || [ "$frozen" = "true" ]; then
        log_success "  ✓ 窗口已冻结"
    else
        log_fail "  ✗ 冻结失败"
    fi
    
    echo ""
    echo "场景 2: 解冻发布窗口"
    echo "  Given 窗口已冻结"
    echo "  When  我点击解冻"
    echo "  Then  窗口恢复未冻结状态"
    
    response=$(api_post "/release-windows/$id/unfreeze" "{}")
    frozen=$(json_get "$response" ".data.frozen")
    
    if [ "$frozen" = "False" ] || [ "$frozen" = "false" ]; then
        log_success "  ✓ 窗口已解冻"
    else
        log_fail "  ✗ 解冻失败"
    fi
}

# ============================================================
# 主函数
# ============================================================

main() {
    echo ""
    echo "╔════════════════════════════════════════════════════════════╗"
    echo "║      US-RW: 发布窗口管理用户故事测试                        ║"
    echo "╚════════════════════════════════════════════════════════════╝"
    echo ""
    
    # 初始化
    init_test
    
    # 登录
    login
    
    # 执行测试
    test_us_rw_001
    test_us_rw_002
    test_us_rw_003
    
    # 报告
    print_summary
}

main "$@"
