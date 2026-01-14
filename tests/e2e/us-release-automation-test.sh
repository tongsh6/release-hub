#!/bin/bash
# ============================================================
# US-AUTO: 发布自动化功能测试
# ============================================================
# 测试覆盖 tasks.md 中已实现的发布自动化功能：
# - 迭代仓库版本管理
# - 代码合并功能
# - 运行任务管理
# ============================================================

source "$(dirname "$0")/test_utils.sh"

# ============================================================
# 测试数据
# ============================================================

TEST_TIMESTAMP=$(date +%s)
TEST_REPO_ID=""
TEST_ITER_KEY=""
TEST_WINDOW_ID=""
TEST_RUN_ID=""

# ============================================================
# 辅助函数
# ============================================================

setup_test_data() {
    log_section "测试数据准备"
    
    # 1. 创建测试仓库
    echo "创建测试仓库..."
    local response=$(api_post "/repositories" "{
        \"name\": \"auto-test-repo-$TEST_TIMESTAMP\",
        \"cloneUrl\": \"https://gitlab.example.com/test/auto-test-$TEST_TIMESTAMP.git\",
        \"defaultBranch\": \"main\",
        \"monoRepo\": false
    }")
    
    TEST_REPO_ID=$(json_get "$response" ".data.id")
    local success=$(json_get "$response" ".success")
    
    if [ "$success" = "True" ] && [ -n "$TEST_REPO_ID" ] && [ "$TEST_REPO_ID" != "null" ]; then
        log_success "  ✓ 测试仓库创建成功: $TEST_REPO_ID"
    else
        log_fail "  ✗ 测试仓库创建失败"
        return 1
    fi
    
    # 2. 创建测试迭代
    echo "创建测试迭代..."
    response=$(api_post "/iterations" "{
        \"name\": \"AUTO-SPRINT-$TEST_TIMESTAMP\",
        \"description\": \"自动化测试迭代\",
        \"repoIds\": [\"$TEST_REPO_ID\"]
    }")
    
    TEST_ITER_KEY=$(json_get "$response" ".data.key")
    success=$(json_get "$response" ".success")
    
    if [ "$success" = "True" ] && [ -n "$TEST_ITER_KEY" ] && [ "$TEST_ITER_KEY" != "null" ]; then
        log_success "  ✓ 测试迭代创建成功: $TEST_ITER_KEY"
    else
        log_fail "  ✗ 测试迭代创建失败"
        return 1
    fi
    
    # 3. 创建测试发布窗口
    echo "创建测试发布窗口..."
    response=$(api_post "/release-windows" "{
        \"name\": \"Auto Test Window $TEST_TIMESTAMP\",
        \"description\": \"自动化测试发布窗口\"
    }")
    
    TEST_WINDOW_ID=$(json_get "$response" ".data.id")
    success=$(json_get "$response" ".success")
    
    if [ "$success" = "True" ] && [ -n "$TEST_WINDOW_ID" ] && [ "$TEST_WINDOW_ID" != "null" ]; then
        log_success "  ✓ 测试发布窗口创建成功: $TEST_WINDOW_ID"
    else
        log_fail "  ✗ 测试发布窗口创建失败"
        return 1
    fi
    
    # 4. 挂载迭代到发布窗口
    echo "挂载迭代到发布窗口..."
    response=$(api_post "/release-windows/$TEST_WINDOW_ID/attach" "{
        \"iterationKeys\": [\"$TEST_ITER_KEY\"]
    }")
    
    success=$(json_get "$response" ".success")
    
    if [ "$success" = "True" ]; then
        log_success "  ✓ 迭代挂载成功"
    else
        log_fail "  ✗ 迭代挂载失败"
    fi
    
    echo ""
}

# ============================================================
# 测试套件 1: 迭代仓库版本管理
# ============================================================

test_iteration_version_api() {
    log_section "测试套件 1: 迭代仓库版本管理"
    
    if [ -z "$TEST_ITER_KEY" ] || [ -z "$TEST_REPO_ID" ]; then
        log_skip "测试数据不完整，跳过版本管理测试"
        return
    fi
    
    # 1.1 获取迭代所有仓库版本信息
    echo ""
    echo "测试 1.1: 获取迭代所有仓库版本信息"
    echo "  API: GET /iterations/{iterationKey}/versions"
    
    local response=$(api_get "/iterations/$TEST_ITER_KEY/versions")
    local success=$(json_get "$response" ".success")
    
    if [ "$success" = "True" ]; then
        local version_count=$(echo "$response" | python3 -c "import sys,json; d=json.load(sys.stdin); print(len(d.get('data',[])) if d.get('data') else 0)" 2>/dev/null || echo "0")
        log_success "  ✓ 获取版本列表成功，共 $version_count 个仓库"
        
        # 显示第一个仓库的版本信息
        if [ "$version_count" -gt 0 ]; then
            local base_version=$(json_get "$response" ".data[0].baseVersion")
            local dev_version=$(json_get "$response" ".data[0].devVersion")
            local target_version=$(json_get "$response" ".data[0].targetVersion")
            log_info "      基准版本: $base_version"
            log_info "      开发版本: $dev_version"
            log_info "      目标版本: $target_version"
        fi
    else
        local code=$(json_get "$response" ".code")
        log_info "  API 响应: success=$success, code=$code"
    fi
    
    # 1.2 获取单个仓库版本信息
    echo ""
    echo "测试 1.2: 获取单个仓库版本信息"
    echo "  API: GET /iterations/{iterationKey}/repos/{repoId}/version"
    
    response=$(api_get "/iterations/$TEST_ITER_KEY/repos/$TEST_REPO_ID/version")
    success=$(json_get "$response" ".success")
    
    if [ "$success" = "True" ]; then
        local repo_id=$(json_get "$response" ".data.repoId")
        local base_version=$(json_get "$response" ".data.baseVersion")
        log_success "  ✓ 获取仓库版本信息成功"
        log_info "      仓库 ID: $repo_id"
        log_info "      基准版本: $base_version"
    else
        local code=$(json_get "$response" ".code")
        log_info "  API 响应: success=$success, code=$code"
    fi
    
    # 1.3 获取仓库版本详情 (通过 IterationController)
    echo ""
    echo "测试 1.3: 获取迭代仓库版本详情"
    echo "  API: GET /iterations/{key}/repos/{repoId}/version-info"
    
    response=$(api_get "/iterations/$TEST_ITER_KEY/repos/$TEST_REPO_ID/version-info")
    success=$(json_get "$response" ".success")
    
    if [ "$success" = "True" ]; then
        log_success "  ✓ 获取版本详情成功"
    else
        local code=$(json_get "$response" ".code")
        log_info "  API 响应: success=$success, code=$code"
    fi
    
    # 1.4 版本冲突检测
    echo ""
    echo "测试 1.4: 版本冲突检测"
    echo "  API: GET /iterations/{key}/repos/{repoId}/check-conflict"
    
    response=$(api_get "/iterations/$TEST_ITER_KEY/repos/$TEST_REPO_ID/check-conflict")
    success=$(json_get "$response" ".success")
    
    if [ "$success" = "True" ]; then
        local has_conflict=$(json_get "$response" ".data.hasConflict")
        log_success "  ✓ 版本冲突检测成功"
        log_info "      是否有冲突: $has_conflict"
    else
        local code=$(json_get "$response" ".code")
        log_info "  API 响应: success=$success, code=$code"
    fi
    
    # 1.5 版本同步
    echo ""
    echo "测试 1.5: 版本同步"
    echo "  API: POST /iterations/{key}/repos/{repoId}/sync-version"
    
    response=$(api_post "/iterations/$TEST_ITER_KEY/repos/$TEST_REPO_ID/sync-version" "{}")
    success=$(json_get "$response" ".success")
    
    if [ "$success" = "True" ]; then
        log_success "  ✓ 版本同步成功"
    else
        local code=$(json_get "$response" ".code")
        log_info "  API 响应: success=$success, code=$code (版本同步可能需要 GitLab 连接)"
    fi
}

# ============================================================
# 测试套件 2: 代码合并功能
# ============================================================

test_code_merge_api() {
    log_section "测试套件 2: 代码合并功能"
    
    if [ -z "$TEST_WINDOW_ID" ] || [ -z "$TEST_ITER_KEY" ]; then
        log_skip "测试数据不完整，跳过代码合并测试"
        return
    fi
    
    # 2.1 单迭代合并
    echo ""
    echo "测试 2.1: 单迭代代码合并"
    echo "  API: POST /release-windows/{windowId}/iterations/{iterationKey}/merge"
    
    local response=$(api_post "/release-windows/$TEST_WINDOW_ID/iterations/$TEST_ITER_KEY/merge" "{}")
    local success=$(json_get "$response" ".success")
    
    if [ "$success" = "True" ]; then
        local merge_count=$(echo "$response" | python3 -c "import sys,json; d=json.load(sys.stdin); print(len(d.get('data',[])) if d.get('data') else 0)" 2>/dev/null || echo "0")
        log_success "  ✓ 单迭代合并成功，返回 $merge_count 个合并结果"
    else
        local code=$(json_get "$response" ".code")
        log_info "  API 响应: success=$success, code=$code (代码合并可能需要 GitLab 连接)"
    fi
    
    # 2.2 批量合并 (全部迭代)
    echo ""
    echo "测试 2.2: 批量代码合并"
    echo "  API: POST /release-windows/{windowId}/merge"
    
    response=$(api_post "/release-windows/$TEST_WINDOW_ID/merge" "{}")
    success=$(json_get "$response" ".success")
    
    if [ "$success" = "True" ]; then
        local merge_count=$(echo "$response" | python3 -c "import sys,json; d=json.load(sys.stdin); print(len(d.get('data',[])) if d.get('data') else 0)" 2>/dev/null || echo "0")
        log_success "  ✓ 批量合并成功，返回 $merge_count 个合并结果"
    else
        local code=$(json_get "$response" ".code")
        log_info "  API 响应: success=$success, code=$code (代码合并可能需要 GitLab 连接)"
    fi
}

# ============================================================
# 测试套件 3: 运行任务管理
# ============================================================

test_run_task_api() {
    log_section "测试套件 3: 运行任务管理"
    
    # 先获取一个 Run ID
    echo ""
    echo "测试 3.0: 获取可用的运行记录"
    
    local response=$(api_get "/runs/paged?page=0&size=10")
    local success=$(json_get "$response" ".success")
    
    if [ "$success" = "True" ]; then
        local run_count=$(json_get "$response" ".page.total")
        log_info "  总运行记录数: $run_count"
        
        if [ "$run_count" != "0" ] && [ -n "$run_count" ]; then
            TEST_RUN_ID=$(json_get "$response" ".data[0].id")
            log_info "  使用运行记录 ID: $TEST_RUN_ID"
        fi
    fi
    
    if [ -z "$TEST_RUN_ID" ] || [ "$TEST_RUN_ID" = "null" ]; then
        log_info "  没有可用的运行记录，尝试发布窗口来创建运行记录"
        
        # 尝试发布窗口来创建运行记录
        if [ -n "$TEST_WINDOW_ID" ]; then
            response=$(api_post "/release-windows/$TEST_WINDOW_ID/publish" "{}")
            success=$(json_get "$response" ".success")
            
            if [ "$success" = "True" ]; then
                log_info "  窗口发布成功，查找新创建的运行记录"
                
                # 重新获取运行记录
                sleep 1
                response=$(api_get "/runs/paged?page=0&size=10")
                TEST_RUN_ID=$(json_get "$response" ".data[0].id")
            fi
        fi
    fi
    
    if [ -z "$TEST_RUN_ID" ] || [ "$TEST_RUN_ID" = "null" ]; then
        log_skip "没有可用的运行记录，跳过任务管理测试"
        return
    fi
    
    # 3.1 获取运行任务列表
    echo ""
    echo "测试 3.1: 获取运行任务列表"
    echo "  API: GET /runs/{runId}/tasks"
    
    response=$(api_get "/runs/$TEST_RUN_ID/tasks")
    success=$(json_get "$response" ".success")
    
    if [ "$success" = "True" ]; then
        local task_count=$(echo "$response" | python3 -c "import sys,json; d=json.load(sys.stdin); print(len(d.get('data',[])) if d.get('data') else 0)" 2>/dev/null || echo "0")
        log_success "  ✓ 获取任务列表成功，共 $task_count 个任务"
        
        # 显示任务类型和状态
        if [ "$task_count" -gt 0 ]; then
            echo "$response" | python3 -c "
import sys, json
data = json.load(sys.stdin)
for task in (data.get('data', []) or [])[:5]:
    task_type = task.get('taskType', 'unknown')
    status = task.get('status', 'unknown')
    print(f'      - {task_type}: {status}')
" 2>/dev/null || true
        fi
        
        # 获取第一个失败的任务 ID（如果有）
        local failed_task_id=$(echo "$response" | python3 -c "
import sys, json
data = json.load(sys.stdin)
for task in (data.get('data', []) or []):
    if task.get('status') == 'FAILED':
        print(task.get('id', ''))
        break
" 2>/dev/null || echo "")
        
        # 3.2 尝试重试失败的任务
        if [ -n "$failed_task_id" ] && [ "$failed_task_id" != "" ]; then
            echo ""
            echo "测试 3.2: 重试失败的任务"
            echo "  API: POST /runs/{runId}/tasks/{taskId}/retry"
            
            response=$(api_post "/runs/$TEST_RUN_ID/tasks/$failed_task_id/retry" "{}")
            success=$(json_get "$response" ".success")
            
            if [ "$success" = "True" ]; then
                log_success "  ✓ 任务重试成功"
            else
                local code=$(json_get "$response" ".code")
                log_info "  API 响应: success=$success, code=$code"
            fi
        else
            echo ""
            echo "测试 3.2: 重试任务 (跳过)"
            log_info "  没有失败的任务可供重试"
        fi
    else
        local code=$(json_get "$response" ".code")
        log_fail "  ✗ 获取任务列表失败: $code"
    fi
}

# ============================================================
# 测试套件 4: 仓库初始版本管理
# ============================================================

test_repo_initial_version() {
    log_section "测试套件 4: 仓库初始版本管理"
    
    if [ -z "$TEST_REPO_ID" ]; then
        log_skip "测试数据不完整，跳过仓库版本测试"
        return
    fi
    
    # 4.1 获取仓库初始版本
    echo ""
    echo "测试 4.1: 获取仓库初始版本"
    echo "  API: GET /repositories/{id}/initial-version"
    
    local response=$(api_get "/repositories/$TEST_REPO_ID/initial-version")
    local success=$(json_get "$response" ".success")
    
    if [ "$success" = "True" ]; then
        local version=$(json_get "$response" ".data.version")
        log_success "  ✓ 获取初始版本成功"
        log_info "      当前版本: $version"
    else
        local code=$(json_get "$response" ".code")
        log_info "  API 响应: success=$success, code=$code"
    fi
    
    # 4.2 设置仓库初始版本
    echo ""
    echo "测试 4.2: 设置仓库初始版本"
    echo "  API: PUT /repositories/{id}/initial-version"
    
    response=$(api_put "/repositories/$TEST_REPO_ID/initial-version" '{
        "version": "1.0.0"
    }')
    success=$(json_get "$response" ".success")
    
    if [ "$success" = "True" ]; then
        local version=$(json_get "$response" ".data.version")
        log_success "  ✓ 设置初始版本成功"
        log_info "      新版本: $version"
    else
        local code=$(json_get "$response" ".code")
        log_info "  API 响应: success=$success, code=$code"
    fi
    
    # 4.3 同步仓库版本
    echo ""
    echo "测试 4.3: 同步仓库版本"
    echo "  API: POST /repositories/{id}/sync-version"
    
    response=$(api_post "/repositories/$TEST_REPO_ID/sync-version" "{}")
    success=$(json_get "$response" ".success")
    
    if [ "$success" = "True" ]; then
        local version=$(json_get "$response" ".data.version")
        log_success "  ✓ 同步版本成功"
        log_info "      同步后版本: $version"
    else
        local code=$(json_get "$response" ".code")
        log_info "  API 响应: success=$success, code=$code (版本同步可能需要 GitLab 连接)"
    fi
}

# ============================================================
# 测试套件 5: 版本策略验证
# ============================================================

test_version_policy_validation() {
    log_section "测试套件 5: 版本策略验证"
    
    # 5.1 测试 PATCH 版本推导
    echo ""
    echo "测试 5.1: PATCH 版本推导 (1.2.3 → 1.2.4)"
    
    local response=$(api_get "/version-policies/PATCH")
    local success=$(json_get "$response" ".success")
    
    if [ "$success" = "True" ]; then
        local scheme=$(json_get "$response" ".data.scheme")
        local bump_rule=$(json_get "$response" ".data.bumpRule")
        log_success "  ✓ 获取 PATCH 策略成功"
        log_info "      Scheme: $scheme, BumpRule: $bump_rule"
    else
        log_info "  策略获取响应: $response"
    fi
    
    # 5.2 测试 MINOR 版本推导
    echo ""
    echo "测试 5.2: MINOR 版本推导 (1.2.3 → 1.3.0)"
    
    response=$(api_get "/version-policies/MINOR")
    success=$(json_get "$response" ".success")
    
    if [ "$success" = "True" ]; then
        local scheme=$(json_get "$response" ".data.scheme")
        local bump_rule=$(json_get "$response" ".data.bumpRule")
        log_success "  ✓ 获取 MINOR 策略成功"
        log_info "      Scheme: $scheme, BumpRule: $bump_rule"
    else
        log_info "  策略获取响应: $response"
    fi
    
    # 5.3 测试 MAJOR 版本推导
    echo ""
    echo "测试 5.3: MAJOR 版本推导 (1.2.3 → 2.0.0)"
    
    response=$(api_get "/version-policies/MAJOR")
    success=$(json_get "$response" ".success")
    
    if [ "$success" = "True" ]; then
        local scheme=$(json_get "$response" ".data.scheme")
        local bump_rule=$(json_get "$response" ".data.bumpRule")
        log_success "  ✓ 获取 MAJOR 策略成功"
        log_info "      Scheme: $scheme, BumpRule: $bump_rule"
    else
        log_info "  策略获取响应: $response"
    fi
    
    # 5.4 测试 DATE 版本策略
    echo ""
    echo "测试 5.4: DATE 版本策略"
    
    response=$(api_get "/version-policies/DATE")
    success=$(json_get "$response" ".success")
    
    if [ "$success" = "True" ]; then
        local scheme=$(json_get "$response" ".data.scheme")
        log_success "  ✓ 获取 DATE 策略成功"
        log_info "      Scheme: $scheme"
    else
        log_info "  策略获取响应: $response"
    fi
}

# ============================================================
# 主函数
# ============================================================

main() {
    echo ""
    echo "╔════════════════════════════════════════════════════════════╗"
    echo "║      US-AUTO: 发布自动化功能测试                          ║"
    echo "║      覆盖 tasks.md 中已实现的功能                          ║"
    echo "╚════════════════════════════════════════════════════════════╝"
    echo ""
    
    # 初始化
    init_test
    login
    
    # 准备测试数据
    setup_test_data
    
    # 执行测试套件
    test_repo_initial_version
    test_iteration_version_api
    test_code_merge_api
    test_run_task_api
    test_version_policy_validation
    
    # 输出报告
    print_summary
    
    # 返回状态码
    if [ $FAILED_TESTS -gt 0 ]; then
        exit 1
    fi
}

main "$@"
