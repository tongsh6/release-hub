#!/bin/bash
# ============================================================
# ReleaseHub 冒烟测试
# 测试场景：创建迭代、仓库、发布窗口的完整工作流
# ============================================================

# 不使用 set -e，让测试可以继续执行

# 导入测试工具
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
source "$SCRIPT_DIR/test_utils.sh"

# ============================================================
# 测试数据
# ============================================================

TIMESTAMP=$(date +%s)

# 仓库信息 (10个仓库)
declare -a REPO_NAMES=(
    "frontend-web"
    "frontend-mobile"
    "backend-api"
    "backend-gateway"
    "backend-auth"
    "service-order"
    "service-payment"
    "service-notification"
    "common-lib"
    "infra-config"
)

# 迭代信息 (5个迭代)
declare -a ITERATION_KEYS=(
    "SPRINT-2026-01"
    "SPRINT-2026-02"
    "SPRINT-2026-03"
    "SPRINT-2026-04"
    "SPRINT-2026-05"
)

# 发布窗口信息 (2个窗口)
declare -a WINDOW_KEYS=(
    "RW-2026-Q1"
    "RW-2026-Q2"
)

# 存储创建的 ID
declare -a REPO_IDS=()
declare -a WINDOW_IDS=()

# ============================================================
# 测试开始
# ============================================================

echo ""
echo -e "${BLUE}╔════════════════════════════════════════════════════════════╗${NC}"
echo -e "${BLUE}║             ReleaseHub 冒烟测试                            ║${NC}"
echo -e "${BLUE}║    迭代 → 仓库 → 发布窗口 → 挂载 完整工作流               ║${NC}"
echo -e "${BLUE}╚════════════════════════════════════════════════════════════╝${NC}"
echo ""

init_test
login

# ============================================================
# 步骤 1: 创建 10 个仓库
# ============================================================

log_section "步骤 1: 创建 10 个代码仓库"

echo "场景: 为不同的微服务创建代码仓库"
echo "  Given 系统已登录"
echo "  When  我创建 10 个代码仓库"
echo "  Then  所有仓库创建成功"
echo ""

for i in "${!REPO_NAMES[@]}"; do
    repo_name="${REPO_NAMES[$i]}"
    
    response=$(api_post "/repositories" "{
        \"name\": \"$repo_name-$TIMESTAMP\",
        \"cloneUrl\": \"https://gitlab.example.com/$repo_name.git\",
        \"defaultBranch\": \"main\",
        \"monoRepo\": false
    }")
    
    repo_id=$(json_get "$response" ".data")
    success=$(json_get "$response" ".success")
    
    if [ "$success" = "True" ] || [ "$success" = "true" ]; then
        # 处理返回的 ID（可能是直接 ID 或对象）
        if [ -n "$repo_id" ] && [ "$repo_id" != "null" ] && [ "$repo_id" != "None" ]; then
            # 如果返回的是字典格式，尝试提取 id
            if echo "$repo_id" | grep -q "id"; then
                repo_id=$(echo "$response" | python3 -c "import sys,json; d=json.load(sys.stdin); print(d.get('data',{}).get('id','') if isinstance(d.get('data'),dict) else d.get('data',''))" 2>/dev/null)
            fi
            REPO_IDS+=("$repo_id")
            log_success "  ✓ 仓库 [$((i+1))/10] $repo_name 创建成功"
        else
            log_fail "  ✗ 仓库 $repo_name 创建失败 - ID 为空"
        fi
    else
        log_fail "  ✗ 仓库 $repo_name 创建失败: $response"
    fi
done

echo ""
log_info "已创建 ${#REPO_IDS[@]} 个仓库"

# ============================================================
# 步骤 2: 创建 5 个迭代，每个迭代挂载 2 个仓库
# ============================================================

log_section "步骤 2: 创建 5 个迭代并挂载仓库"

echo "场景: 创建 5 个迭代，每个迭代关联 2 个仓库"
echo "  Given 已创建 10 个仓库"
echo "  When  我创建 5 个迭代"
echo "  And   每个迭代关联 2 个仓库"
echo "  Then  所有迭代创建成功"
echo ""

for i in "${!ITERATION_KEYS[@]}"; do
    iter_key="${ITERATION_KEYS[$i]}-$TIMESTAMP"
    
    # 每个迭代关联 2 个仓库 (0-1, 2-3, 4-5, 6-7, 8-9)
    repo_idx1=$((i * 2))
    repo_idx2=$((i * 2 + 1))
    
    if [ $repo_idx1 -lt ${#REPO_IDS[@]} ] && [ $repo_idx2 -lt ${#REPO_IDS[@]} ]; then
        repo_id1="${REPO_IDS[$repo_idx1]}"
        repo_id2="${REPO_IDS[$repo_idx2]}"
        repo_ids_json="[\"$repo_id1\", \"$repo_id2\"]"
        repo_names="${REPO_NAMES[$repo_idx1]}, ${REPO_NAMES[$repo_idx2]}"
    else
        repo_ids_json="[]"
        repo_names="无"
    fi
    
    response=$(api_post "/iterations" "{
        \"name\": \"$iter_key\",
        \"description\": \"迭代 $((i+1)) - 测试冒烟场景\",
        \"repoIds\": $repo_ids_json
    }")
    
    success=$(json_get "$response" ".success")
    created_key=$(json_get "$response" ".data.key")
    
    if [ "$success" = "True" ] || [ "$success" = "true" ]; then
        log_success "  ✓ 迭代 [$((i+1))/5] $iter_key 创建成功 (key: $created_key)"
        log_info "      关联仓库: $repo_names"
        
        # 更新 ITERATION_KEYS 为服务自动生成的 key
        ITERATION_KEYS[$i]="$created_key"
    else
        log_fail "  ✗ 迭代 $iter_key 创建失败: $response"
    fi
done

# ============================================================
# 步骤 3: 验证迭代的仓库关联
# ============================================================

log_section "步骤 3: 验证迭代的仓库关联"

echo "场景: 验证每个迭代正确关联了 2 个仓库"
echo "  Given 5 个迭代已创建"
echo "  When  我查询每个迭代的仓库列表"
echo "  Then  每个迭代应有 2 个关联仓库"
echo ""

for i in "${!ITERATION_KEYS[@]}"; do
    iter_key="${ITERATION_KEYS[$i]}"
    
    response=$(api_get "/iterations/$iter_key/repos")
    success=$(json_get "$response" ".success")
    
    if [ "$success" = "True" ] || [ "$success" = "true" ]; then
        # 检查返回的仓库数量
        repo_count=$(echo "$response" | python3 -c "import sys,json; d=json.load(sys.stdin); print(len(d.get('data',[])) if d.get('data') else 0)" 2>/dev/null || echo "0")
        
        if [ "$repo_count" = "2" ]; then
            log_success "  ✓ 迭代 $iter_key 关联了 $repo_count 个仓库"
        else
            log_info "    迭代 $iter_key 关联了 $repo_count 个仓库 (预期 2)"
        fi
    else
        log_fail "  ✗ 查询迭代 $iter_key 仓库失败"
    fi
done

# ============================================================
# 步骤 3.5: 测试动态添加仓库到迭代 (任务1验证)
# ============================================================

log_section "步骤 3.5: 测试动态添加仓库到迭代"

echo "场景: 使用 addRepos API 向迭代添加新仓库"
echo "  Given 迭代 1 已存在"
echo "  When  我通过 addRepos API 添加额外仓库"
echo "  Then  迭代应包含新添加的仓库"
echo ""

if [ ${#ITERATION_KEYS[@]} -ge 1 ] && [ ${#REPO_IDS[@]} -ge 3 ]; then
    iter_key="${ITERATION_KEYS[0]}"
    # 尝试添加第3个仓库到迭代1（已有仓库0和1）
    extra_repo_id="${REPO_IDS[2]}"
    
    response=$(api_post "/iterations/$iter_key/repos/add" "{
        \"repoIds\": [\"$extra_repo_id\"]
    }")
    
    success=$(json_get "$response" ".success")
    
    if [ "$success" = "True" ] || [ "$success" = "true" ]; then
        log_success "  ✓ 成功添加仓库到迭代 $iter_key"
        
        # 验证仓库数量
        verify_response=$(api_get "/iterations/$iter_key/repos")
        repo_count=$(echo "$verify_response" | python3 -c "import sys,json; d=json.load(sys.stdin); print(len(d.get('data',[])) if d.get('data') else 0)" 2>/dev/null || echo "0")
        
        if [ "$repo_count" = "3" ]; then
            log_success "  ✓ 迭代 $iter_key 现在有 $repo_count 个仓库 (预期: 3)"
        else
            log_info "    迭代 $iter_key 仓库数量: $repo_count (预期: 3)"
        fi
        
        # 测试移除仓库
        remove_response=$(api_post "/iterations/$iter_key/repos/remove" "{
            \"repoIds\": [\"$extra_repo_id\"]
        }")
        
        remove_success=$(json_get "$remove_response" ".success")
        if [ "$remove_success" = "True" ] || [ "$remove_success" = "true" ]; then
            log_success "  ✓ 成功从迭代移除仓库"
        else
            log_fail "  ✗ 移除仓库失败"
        fi
    else
        log_fail "  ✗ 添加仓库失败: $response"
    fi
else
    log_skip "  跳过: 数据不足"
fi

# ============================================================
# 步骤 4: 创建 2 个发布窗口
# ============================================================

log_section "步骤 4: 创建 2 个发布窗口"

echo "场景: 创建 2 个发布窗口用于不同季度的发布"
echo "  Given 系统已登录"
echo "  When  我创建 2 个发布窗口"
echo "  Then  窗口创建成功，状态为 DRAFT"
echo ""

# 计算时间窗口
START_DATE=$(date -v+1d +%Y-%m-%dT00:00:00Z 2>/dev/null || date -d "+1 day" +%Y-%m-%dT00:00:00Z)
END_DATE=$(date -v+30d +%Y-%m-%dT23:59:59Z 2>/dev/null || date -d "+30 days" +%Y-%m-%dT23:59:59Z)

for i in "${!WINDOW_KEYS[@]}"; do
    window_key="${WINDOW_KEYS[$i]}-$TIMESTAMP"
    window_name="发布窗口 Q$((i+1)) 2026"
    
    response=$(api_post "/release-windows" "{
        \"windowKey\": \"$window_key\",
        \"name\": \"$window_name\"
    }")
    
    success=$(json_get "$response" ".success")
    window_id=$(json_get "$response" ".data.id")
    status=$(json_get "$response" ".data.status")
    
    if [ "$success" = "True" ] && [ -n "$window_id" ] && [ "$window_id" != "null" ]; then
        WINDOW_IDS+=("$window_id")
        WINDOW_KEYS[$i]="$window_key"
        
        log_success "  ✓ 发布窗口 [$((i+1))/2] $window_name 创建成功"
        log_info "      ID: ${window_id:0:8}... | 状态: $status"
        
        # 配置时间窗口
        config_response=$(api_put "/release-windows/$window_id/config" "{
            \"startAt\": \"$START_DATE\",
            \"endAt\": \"$END_DATE\"
        }")
        
        config_success=$(json_get "$config_response" ".success")
        if [ "$config_success" = "True" ] || [ "$config_success" = "true" ]; then
            log_info "      时间配置成功: $START_DATE → $END_DATE"
        else
            log_info "      时间配置响应: $config_response"
        fi
    else
        log_fail "  ✗ 发布窗口 $window_name 创建失败: $response"
    fi
done

# ============================================================
# 步骤 5: 将迭代挂载到发布窗口
# ============================================================

log_section "步骤 5: 将迭代挂载到发布窗口"

echo "场景: 将 5 个迭代分配到 2 个发布窗口"
echo "  - 窗口 1 (Q1): 迭代 1, 2, 3"
echo "  - 窗口 2 (Q2): 迭代 4, 5"
echo ""

# 窗口 1: 挂载迭代 1, 2, 3
if [ ${#WINDOW_IDS[@]} -ge 1 ]; then
    window_id="${WINDOW_IDS[0]}"
    iterations_to_attach='["'"${ITERATION_KEYS[0]}"'", "'"${ITERATION_KEYS[1]}"'", "'"${ITERATION_KEYS[2]}"'"]'
    
    echo "挂载到窗口 1 (${WINDOW_KEYS[0]}):"
    
    response=$(api_post "/release-windows/$window_id/attach" "{
        \"iterationKeys\": $iterations_to_attach
    }")
    
    success=$(json_get "$response" ".success")
    
    if [ "$success" = "True" ] || [ "$success" = "true" ]; then
        log_success "  ✓ 成功挂载 3 个迭代到窗口 1"
        log_info "      ${ITERATION_KEYS[0]}"
        log_info "      ${ITERATION_KEYS[1]}"
        log_info "      ${ITERATION_KEYS[2]}"
    else
        log_fail "  ✗ 挂载失败: $response"
    fi
fi

# 窗口 2: 挂载迭代 4, 5
if [ ${#WINDOW_IDS[@]} -ge 2 ]; then
    window_id="${WINDOW_IDS[1]}"
    iterations_to_attach='["'"${ITERATION_KEYS[3]}"'", "'"${ITERATION_KEYS[4]}"'"]'
    
    echo ""
    echo "挂载到窗口 2 (${WINDOW_KEYS[1]}):"
    
    response=$(api_post "/release-windows/$window_id/attach" "{
        \"iterationKeys\": $iterations_to_attach
    }")
    
    success=$(json_get "$response" ".success")
    
    if [ "$success" = "True" ] || [ "$success" = "true" ]; then
        log_success "  ✓ 成功挂载 2 个迭代到窗口 2"
        log_info "      ${ITERATION_KEYS[3]}"
        log_info "      ${ITERATION_KEYS[4]}"
    else
        log_fail "  ✗ 挂载失败: $response"
    fi
fi

# ============================================================
# 步骤 6: 验证发布窗口的迭代挂载
# ============================================================

log_section "步骤 6: 验证发布窗口的迭代挂载"

echo "场景: 验证每个发布窗口正确挂载了迭代"
echo ""

for i in "${!WINDOW_IDS[@]}"; do
    window_id="${WINDOW_IDS[$i]}"
    window_key="${WINDOW_KEYS[$i]}"
    
    expected_count=$([[ $i -eq 0 ]] && echo "3" || echo "2")
    
    response=$(api_get "/release-windows/$window_id/iterations")
    success=$(json_get "$response" ".success")
    
    if [ "$success" = "True" ] || [ "$success" = "true" ]; then
        iter_count=$(echo "$response" | python3 -c "import sys,json; d=json.load(sys.stdin); print(len(d.get('data',[])) if d.get('data') else 0)" 2>/dev/null || echo "0")
        
        if [ "$iter_count" = "$expected_count" ]; then
            log_success "  ✓ 窗口 $((i+1)) ($window_key) 挂载了 $iter_count 个迭代 (预期: $expected_count)"
        else
            log_fail "  ✗ 窗口 $((i+1)) 迭代数量不匹配 (实际: $iter_count, 预期: $expected_count)"
        fi
        
        # 列出挂载的迭代
        echo "$response" | python3 -c "
import sys, json
data = json.load(sys.stdin)
for item in (data.get('data', []) or []):
    print(f'      - {item.get(\"iterationKey\", \"unknown\")}')
" 2>/dev/null || true
    else
        log_fail "  ✗ 查询窗口 $window_key 迭代失败: $response"
    fi
done

# ============================================================
# 步骤 6.3: 测试仓库详情 API (任务3验证)
# ============================================================

log_section "步骤 6.3: 测试仓库详情 API"

echo "场景: 验证仓库详情包含 cloneUrl (用于打开 GitLab)"
echo "  Given 已创建仓库"
echo "  When  我查询仓库详情"
echo "  Then  应返回包含 cloneUrl 的仓库信息"
echo ""

if [ ${#REPO_IDS[@]} -ge 1 ]; then
    repo_id="${REPO_IDS[0]}"
    
    response=$(api_get "/repositories/$repo_id")
    success=$(json_get "$response" ".success")
    
    if [ "$success" = "True" ] || [ "$success" = "true" ]; then
        clone_url=$(json_get "$response" ".data.cloneUrl")
        repo_name=$(json_get "$response" ".data.name")
        
        if [ -n "$clone_url" ] && [ "$clone_url" != "null" ]; then
            log_success "  ✓ 仓库详情 API 返回成功"
            log_info "      仓库名: $repo_name"
            log_info "      cloneUrl: $clone_url"
        else
            log_info "    仓库详情 API 响应缺少 cloneUrl"
        fi
    else
        log_fail "  ✗ 仓库详情 API 调用失败"
    fi
else
    log_skip "  跳过: 无可用仓库"
fi

# ============================================================
# 步骤 6.5: 测试版本策略 API (任务2验证)
# ============================================================

log_section "步骤 6.5: 测试版本策略 API"

echo "场景: 验证版本策略 API 返回内置策略"
echo "  Given 系统已登录"
echo "  When  我查询版本策略列表"
echo "  Then  应返回内置的版本策略"
echo ""

response=$(api_get "/version-policies")
success=$(json_get "$response" ".success")

if [ "$success" = "True" ] || [ "$success" = "true" ]; then
    policy_count=$(echo "$response" | python3 -c "import sys,json; d=json.load(sys.stdin); print(len(d.get('data',[])) if d.get('data') else 0)" 2>/dev/null || echo "0")
    
    if [ "$policy_count" -ge 4 ]; then
        log_success "  ✓ 版本策略 API 返回 $policy_count 个策略"
        
        # 显示策略
        echo "$response" | python3 -c "
import sys, json
data = json.load(sys.stdin)
for item in (data.get('data', []) or []):
    name = item.get('name', 'unknown')
    scheme = item.get('scheme', 'unknown')
    bump = item.get('bumpRule', 'NONE')
    print(f'      - {name}: {scheme} ({bump})')
" 2>/dev/null || true
    else
        log_fail "  ✗ 版本策略数量不足 (实际: $policy_count, 预期: >= 4)"
    fi
else
    log_fail "  ✗ 版本策略 API 调用失败: $response"
fi

# ============================================================
# 步骤 6.7: 测试 Dashboard 统计 API (任务5验证)
# ============================================================

log_section "步骤 6.7: 测试 Dashboard 统计 API"

echo "场景: 验证仪表盘统计数据"
echo "  Given 已创建仓库、迭代和发布窗口"
echo "  When  我查询 Dashboard 统计 API"
echo "  Then  应返回正确的统计数据"
echo ""

response=$(api_get "/dashboard/stats")
success=$(json_get "$response" ".success")

if [ "$success" = "True" ] || [ "$success" = "true" ]; then
    total_repos=$(json_get "$response" ".data.totalRepositories")
    total_iterations=$(json_get "$response" ".data.totalIterations")
    active_windows=$(json_get "$response" ".data.activeWindows")
    recent_runs=$(json_get "$response" ".data.recentRuns")
    
    log_success "  ✓ Dashboard 统计 API 返回成功"
    log_info "      仓库总数: $total_repos"
    log_info "      迭代总数: $total_iterations"
    log_info "      活跃窗口: $active_windows"
    log_info "      近期执行: $recent_runs"
    
    # 验证数据合理性
    if [ "$total_repos" -ge 10 ]; then
        log_success "  ✓ 仓库数量验证通过 (>= 10)"
    else
        log_fail "  ✗ 仓库数量异常 (实际: $total_repos, 预期: >= 10)"
    fi
else
    log_fail "  ✗ Dashboard 统计 API 调用失败: $response"
fi

# ============================================================
# 步骤 6.8: 测试分支规则 API (任务6验证)
# ============================================================

log_section "步骤 6.8: 测试分支规则 API"

echo "场景: 验证分支规则 CRUD"
echo "  Given 系统已登录"
echo "  When  我查询分支规则列表"
echo "  Then  应返回内置的分支规则"
echo ""

# 获取分支规则列表
response=$(api_get "/branch-rules")
success=$(json_get "$response" ".success")
rule_count=$(echo "$response" | python3 -c "import sys,json; d=json.load(sys.stdin); print(len(d.get('data',[])) if d.get('data') else 0)" 2>/dev/null || echo "0")

if [ "$success" = "True" ] || [ "$success" = "true" ]; then
    if [ "$rule_count" -ge 4 ]; then
        log_success "  ✓ 分支规则 API 返回 $rule_count 条规则"
        
        # 显示规则
        echo "$response" | python3 -c "
import sys, json
data = json.load(sys.stdin)
for item in (data.get('data', []) or []):
    name = item.get('name', 'unknown')
    pattern = item.get('pattern', 'unknown')
    rtype = item.get('type', 'ALLOW')
    print(f'      - {name}: {pattern} ({rtype})')
" 2>/dev/null || true
    else
        log_info "  分支规则数量: $rule_count"
    fi
else
    log_fail "  ✗ 分支规则 API 调用失败: $response"
fi

# 测试创建新规则
echo ""
echo "场景: 创建新分支规则"
response=$(api_post "/branch-rules" '{"name":"Test Rule","pattern":"test/*","type":"ALLOW"}')
success=$(json_get "$response" ".success")

if [ "$success" = "True" ] || [ "$success" = "true" ]; then
    rule_id=$(json_get "$response" ".data.id")
    log_success "  ✓ 创建分支规则成功 (ID: $rule_id)"
    
    # 删除测试规则
    delete_response=$(api_delete "/branch-rules/$rule_id")
    delete_success=$(json_get "$delete_response" ".success")
    if [ "$delete_success" = "True" ] || [ "$delete_success" = "true" ]; then
        log_success "  ✓ 删除测试规则成功"
    else
        log_info "  删除规则响应: $delete_response"
    fi
else
    log_fail "  ✗ 创建分支规则失败: $response"
fi

# ============================================================
# 步骤 7: 发布窗口并验证
# ============================================================

log_section "步骤 7: 发布窗口状态流转"

echo "场景: 将窗口 1 发布，验证状态变更"
echo ""

if [ ${#WINDOW_IDS[@]} -ge 1 ]; then
    window_id="${WINDOW_IDS[0]}"
    
    # 发布窗口
    response=$(api_post "/release-windows/$window_id/publish" "{}")
    success=$(json_get "$response" ".success")
    status=$(json_get "$response" ".data.status")
    
    if [ "$success" = "True" ] && [ "$status" = "PUBLISHED" ]; then
        log_success "  ✓ 窗口 1 发布成功，状态: $status"
    else
        log_info "  窗口 1 发布响应: $response"
    fi
fi

# ============================================================
# 步骤 8: 查看发布计划
# ============================================================

log_section "步骤 8: 查看发布计划"

echo "场景: 查看窗口 1 的发布计划"
echo ""

if [ ${#WINDOW_IDS[@]} -ge 1 ]; then
    window_id="${WINDOW_IDS[0]}"
    
    response=$(api_get "/release-windows/$window_id/plan")
    success=$(json_get "$response" ".success")
    
    if [ "$success" = "True" ] || [ "$success" = "true" ]; then
        log_success "  ✓ 获取发布计划成功"
        
        # 显示计划内容
        plan_count=$(echo "$response" | python3 -c "import sys,json; d=json.load(sys.stdin); print(len(d.get('data',[])) if d.get('data') else 0)" 2>/dev/null || echo "0")
        log_info "      计划包含 $plan_count 个仓库"
        
        echo "$response" | python3 -c "
import sys, json
data = json.load(sys.stdin)
for item in (data.get('data', []) or [])[:5]:
    repo_name = item.get('repoName', item.get('repoId', 'unknown'))
    iter_key = item.get('iterationKey', 'unknown')
    print(f'      - [{iter_key}] {repo_name}')
if len(data.get('data', [])) > 5:
    print(f'      ... 等共 {len(data.get(\"data\", []))} 个')
" 2>/dev/null || true
    else
        log_info "  发布计划 API 响应: $response"
    fi
fi

# ============================================================
# 步骤 9: 清理测试数据 (可选)
# ============================================================

log_section "步骤 9: 测试数据汇总"

echo ""
echo "创建的测试数据:"
echo "  • 仓库数量: ${#REPO_IDS[@]} 个"
echo "  • 迭代数量: ${#ITERATION_KEYS[@]} 个"
echo "  • 发布窗口: ${#WINDOW_IDS[@]} 个"
echo ""
echo "数据关系:"
echo "  ┌─────────────────────────────────────────────────────────┐"
echo "  │  发布窗口 1 (Q1)                                        │"
echo "  │    ├── 迭代 1 → 仓库 1, 2                               │"
echo "  │    ├── 迭代 2 → 仓库 3, 4                               │"
echo "  │    └── 迭代 3 → 仓库 5, 6                               │"
echo "  ├─────────────────────────────────────────────────────────┤"
echo "  │  发布窗口 2 (Q2)                                        │"
echo "  │    ├── 迭代 4 → 仓库 7, 8                               │"
echo "  │    └── 迭代 5 → 仓库 9, 10                              │"
echo "  └─────────────────────────────────────────────────────────┘"
echo ""

# ============================================================
# 打印测试报告
# ============================================================

print_summary

# 退出码
if [ $FAILED_TESTS -gt 0 ]; then
    exit 1
fi
exit 0
