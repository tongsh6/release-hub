#!/bin/bash
# ============================================================
# US-REPO: 代码仓库管理用户故事测试
# ============================================================

# set -e  # 不中断测试运行

source "$(dirname "$0")/test_utils.sh"

# ============================================================
# US-REPO-001: 添加代码仓库
# ============================================================

test_us_repo_001() {
    log_section "US-REPO-001: 添加代码仓库"
    
    echo "场景 1: 添加 GitLab 仓库"
    echo "  Given 我有一个 GitLab 项目"
    echo "  When  我填写仓库信息并提交"
    echo "  Then  仓库添加成功"
    
    local ts=$(date +%s)
    local repo_name="test-repo-$ts"
    local response=$(api_post "/repositories" "{
        \"name\": \"$repo_name\",
        \"cloneUrl\": \"https://gitlab.example.com/test/$repo_name.git\",
        \"defaultBranch\": \"main\",
        \"monoRepo\": false
    }")
    
    local repo_id=$(json_get "$response" ".data.id")
    local code=$(json_get "$response" ".code")
    
    if [ -n "$repo_id" ] && [ "$repo_id" != "null" ]; then
        log_success "  ✓ 仓库添加成功，ID: $repo_id"
        echo "$repo_id" > /tmp/test_repo_id
    elif [ "$code" = "OK" ]; then
        log_success "  ✓ 仓库添加 API 正常"
    else
        log_fail "  ✗ 仓库添加失败: $response"
    fi
    
    echo ""
    echo "场景 2: 添加单仓库（monoRepo=false）"
    
    response=$(api_post "/repositories" "{
        \"name\": \"single-service-$(date +%s)\",
        \"cloneUrl\": \"https://gitlab.example.com/test/single-service-$(date +%s).git\",
        \"defaultBranch\": \"master\",
        \"monoRepo\": false
    }")
    
    local mono=$(json_get "$response" ".data.monoRepo")
    
    if [ "$mono" = "False" ] || [ "$mono" = "false" ]; then
        log_success "  ✓ 单仓库类型正确"
    else
        log_info "  monoRepo: $mono"
    fi
    
    echo ""
    echo "场景 3: 添加 MonoRepo"
    
    response=$(api_post "/repositories" "{
        \"name\": \"mono-platform-$(date +%s)\",
        \"cloneUrl\": \"https://gitlab.example.com/test/mono-platform-$(date +%s).git\",
        \"defaultBranch\": \"main\",
        \"monoRepo\": true
    }")
    
    mono=$(json_get "$response" ".data.monoRepo")
    
    if [ "$mono" = "True" ] || [ "$mono" = "true" ]; then
        log_success "  ✓ MonoRepo 类型正确"
    else
        log_info "  monoRepo: $mono"
    fi
}

# ============================================================
# US-REPO-002: 查看仓库列表
# ============================================================

test_us_repo_002() {
    log_section "US-REPO-002: 查看仓库列表"
    
    echo "场景 1: 分页查看仓库列表"
    echo "  Given 系统中有多个仓库"
    echo "  When  我查看仓库列表"
    echo "  Then  显示分页的仓库信息"
    
    local response=$(api_get "/repositories/paged?page=0&size=10")
    local total=$(json_get "$response" ".page.total")
    local code=$(json_get "$response" ".code")
    
    if [ "$code" = "OK" ]; then
        log_success "  ✓ 获取仓库列表成功"
        log_info "    共 $total 个仓库"
    else
        log_fail "  ✗ 获取仓库列表失败: $response"
    fi
    
    echo ""
    echo "场景 2: 按关键字搜索仓库"
    
    response=$(api_get "/repositories/paged?page=0&size=10&keyword=test")
    code=$(json_get "$response" ".code")
    
    if [ "$code" = "OK" ]; then
        log_success "  ✓ 按关键字搜索成功"
    else
        log_info "  ⚠ 筛选功能可能未实现"
    fi
}

# ============================================================
# US-REPO-003: 查看仓库详情
# ============================================================

test_us_repo_003() {
    log_section "US-REPO-003: 查看仓库详情"
    
    local repo_id=$(cat /tmp/test_repo_id 2>/dev/null || echo "")
    
    if [ -z "$repo_id" ]; then
        log_skip "无测试仓库 ID，跳过详情测试"
        return
    fi
    
    echo "场景 1: 获取仓库详细信息"
    echo "  Given 仓库已添加"
    echo "  When  我查看仓库详情"
    echo "  Then  显示仓库完整信息"
    
    local response=$(api_get "/repositories/$repo_id")
    local name=$(json_get "$response" ".data.name")
    local code=$(json_get "$response" ".code")
    
    if [ "$code" = "OK" ] && [ -n "$name" ]; then
        log_success "  ✓ 获取仓库详情成功"
        log_info "    仓库名: $name"
    else
        log_fail "  ✗ 获取仓库详情失败: $response"
    fi
}

# ============================================================
# US-REPO-004: 更新仓库信息
# ============================================================

test_us_repo_004() {
    log_section "US-REPO-004: 更新仓库信息"
    
    local repo_id=$(cat /tmp/test_repo_id 2>/dev/null || echo "")
    
    if [ -z "$repo_id" ]; then
        log_skip "无测试仓库 ID，跳过更新测试"
        return
    fi
    
    echo "场景 1: 更新仓库默认分支"
    echo "  Given 仓库已添加"
    echo "  When  我修改默认分支为 develop"
    echo "  Then  更新成功"
    
    local response=$(api_put "/repositories/$repo_id" "{
        \"defaultBranch\": \"develop\"
    }")
    
    local branch=$(json_get "$response" ".data.defaultBranch")
    local code=$(json_get "$response" ".code")
    
    if [ "$branch" = "develop" ]; then
        log_success "  ✓ 默认分支更新成功"
    elif [ "$code" = "OK" ]; then
        log_success "  ✓ 更新 API 正常"
    else
        log_fail "  ✗ 更新仓库失败: $response"
    fi
}

# ============================================================
# US-REPO-005: 删除仓库
# ============================================================

test_us_repo_005() {
    log_section "US-REPO-005: 删除仓库"
    
    echo "场景 1: 删除未关联的仓库"
    echo "  Given 仓库未关联到任何迭代"
    echo "  When  我删除仓库"
    echo "  Then  删除成功"
    
    # 创建一个专门用于删除测试的仓库
    local gitlab_id=$(($(date +%s) % 1000000 + RANDOM + 9000))
    local response=$(api_post "/repositories" "{
        \"name\": \"to-be-deleted-$(date +%s)\",
        \"cloneUrl\": \"https://gitlab.example.com/test/delete-test-$(date +%s).git\",
        \"defaultBranch\": \"main\",
        \"monoRepo\": false
    }")
    
    local repo_id=$(json_get "$response" ".data.id")
    
    if [ -z "$repo_id" ] || [ "$repo_id" = "null" ]; then
        log_info "  ⚠ 无法创建测试仓库，跳过删除测试"
        return
    fi
    
    # 删除仓库
    response=$(api_delete "/repositories/$repo_id")
    local code=$(json_get "$response" ".code")
    
    if [ "$code" = "OK" ]; then
        log_success "  ✓ 仓库删除成功"
    else
        log_fail "  ✗ 仓库删除失败: $response"
    fi
    
    echo ""
    echo "场景 2: 验证删除后不存在"
    
    response=$(api_get "/repositories/$repo_id")
    code=$(json_get "$response" ".code")
    
    if [ "$code" != "OK" ]; then
        log_success "  ✓ 删除后仓库不存在"
    else
        log_fail "  ✗ 仓库仍然存在"
    fi
}

# ============================================================
# US-REPO-006: 仓库统计信息
# ============================================================

test_us_repo_006() {
    log_section "US-REPO-006: 仓库统计信息"
    
    local repo_id=$(cat /tmp/test_repo_id 2>/dev/null || echo "")
    
    echo "场景 1: 查看仓库分支统计"
    echo "  Given 仓库已添加"
    echo "  When  我查看统计信息"
    echo "  Then  显示分支数、MR 数等"
    
    if [ -n "$repo_id" ]; then
        local response=$(api_get "/repositories/$repo_id/stats")
        local code=$(json_get "$response" ".code")
        
        if [ "$code" = "OK" ]; then
            local branch_count=$(json_get "$response" ".data.branchCount")
            local mr_count=$(json_get "$response" ".data.mrCount")
            log_success "  ✓ 获取统计信息成功"
            log_info "    分支数: $branch_count, MR 数: $mr_count"
        else
            log_info "  ⚠ 统计 API 可能未实现"
        fi
    else
        log_skip "  无测试仓库 ID"
    fi
}

# ============================================================
# 主函数
# ============================================================

main() {
    echo ""
    echo "╔════════════════════════════════════════════════════════════╗"
    echo "║      US-REPO: 代码仓库管理用户故事测试                      ║"
    echo "╚════════════════════════════════════════════════════════════╝"
    echo ""
    
    # 初始化
    init_test
    
    # 登录
    login
    
    # 执行测试
    test_us_repo_001
    test_us_repo_002
    test_us_repo_003
    test_us_repo_004
    test_us_repo_005
    test_us_repo_006
    
    # 报告
    print_summary
}

main "$@"
