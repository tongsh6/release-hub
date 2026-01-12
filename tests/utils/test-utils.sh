#!/bin/bash
# ============================================================
# 测试工具函数库
# ============================================================

# 配置
BASE_URL="${BASE_URL:-http://localhost:8080/api/v1}"
TOKEN=""

# 计数器
TOTAL_TESTS=0
PASSED_TESTS=0
FAILED_TESTS=0

# 颜色
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
NC='\033[0m'

# ============================================================
# 日志函数
# ============================================================

log_info() {
    echo -e "${BLUE}[INFO]${NC} $1"
}

log_success() {
    echo -e "${GREEN}[PASS]${NC} $1"
    ((PASSED_TESTS++))
    ((TOTAL_TESTS++))
}

log_fail() {
    echo -e "${RED}[FAIL]${NC} $1"
    ((FAILED_TESTS++))
    ((TOTAL_TESTS++))
}

log_skip() {
    echo -e "${YELLOW}[SKIP]${NC} $1"
    ((TOTAL_TESTS++))
}

log_section() {
    echo ""
    echo -e "${BLUE}━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━${NC}"
    echo -e "${BLUE}  $1${NC}"
    echo -e "${BLUE}━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━${NC}"
}

# ============================================================
# HTTP 请求函数
# ============================================================

api_get() {
    local path="$1"
    local params="$2"
    curl -s -X GET "$BASE_URL$path" \
        -H "Authorization: Bearer $TOKEN" \
        -H "Content-Type: application/json"
}

api_post() {
    local path="$1"
    local data="$2"
    curl -s -X POST "$BASE_URL$path" \
        -H "Authorization: Bearer $TOKEN" \
        -H "Content-Type: application/json" \
        -d "$data"
}

api_put() {
    local path="$1"
    local data="$2"
    curl -s -X PUT "$BASE_URL$path" \
        -H "Authorization: Bearer $TOKEN" \
        -H "Content-Type: application/json" \
        -d "$data"
}

api_delete() {
    local path="$1"
    curl -s -X DELETE "$BASE_URL$path" \
        -H "Authorization: Bearer $TOKEN" \
        -H "Content-Type: application/json"
}

# ============================================================
# JSON 解析函数
# ============================================================

json_get() {
    local json="$1"
    local path="$2"
    # 使用 jq 风格的路径，例如 .data.token 或 .data[0].name
    echo "$json" | python3 -c "
import sys, json
data = json.load(sys.stdin)
path = sys.argv[1] if len(sys.argv) > 1 else ''
try:
    result = data
    for part in path.strip('.').split('.'):
        if not part:
            continue
        if '[' in part:
            key = part.split('[')[0]
            idx = int(part.split('[')[1].rstrip(']'))
            if key:
                result = result[key]
            result = result[idx]
        else:
            result = result[part]
    print(result)
except:
    print('')
" "$path" 2>/dev/null || echo ""
}

# ============================================================
# 断言函数
# ============================================================

assert_equals() {
    local expected="$1"
    local actual="$2"
    local message="$3"
    
    if [ "$expected" = "$actual" ]; then
        log_success "$message"
        return 0
    else
        log_fail "$message (expected: '$expected', actual: '$actual')"
        return 1
    fi
}

assert_not_empty() {
    local value="$1"
    local message="$2"
    
    if [ -n "$value" ] && [ "$value" != "null" ] && [ "$value" != "None" ]; then
        log_success "$message"
        return 0
    else
        log_fail "$message (value is empty or null)"
        return 1
    fi
}

assert_empty() {
    local value="$1"
    local message="$2"
    
    if [ -z "$value" ] || [ "$value" = "null" ] || [ "$value" = "None" ]; then
        log_success "$message"
        return 0
    else
        log_fail "$message (value is not empty: '$value')"
        return 1
    fi
}

assert_contains() {
    local haystack="$1"
    local needle="$2"
    local message="$3"
    
    if echo "$haystack" | grep -q "$needle"; then
        log_success "$message"
        return 0
    else
        log_fail "$message (not found: '$needle')"
        return 1
    fi
}

assert_http_success() {
    local response="$1"
    local message="$2"
    
    local code=$(json_get "$response" ".code")
    if [ "$code" = "OK" ] || [ "$code" = "0" ] || [ "$code" = "200" ]; then
        log_success "$message"
        return 0
    else
        log_fail "$message (code: $code)"
        return 1
    fi
}

# ============================================================
# 初始化和登录
# ============================================================

init_test() {
    log_info "初始化测试环境..."
    
    # 检查后端服务
    local health=$(curl -s -o /dev/null -w "%{http_code}" "http://localhost:8080/actuator/health" 2>/dev/null || echo "000")
    if [ "$health" != "200" ]; then
        echo -e "${RED}错误: 后端服务不可用 (HTTP: $health)${NC}"
        echo "请先启动后端服务:"
        echo "  cd release-hub && ./mvnw spring-boot:run -pl releasehub-bootstrap"
        exit 1
    fi
    
    log_info "后端服务正常"
}

login() {
    log_info "登录系统..."
    
    local response=$(curl -s -X POST "$BASE_URL/auth/login" \
        -H "Content-Type: application/json" \
        -d '{"username":"admin","password":"admin"}')
    
    TOKEN=$(json_get "$response" ".data.token")
    
    if [ -n "$TOKEN" ] && [ "$TOKEN" != "null" ]; then
        log_info "登录成功"
    else
        echo -e "${RED}登录失败${NC}"
        echo "Response: $response"
        exit 1
    fi
}

# ============================================================
# 测试报告
# ============================================================

print_summary() {
    echo ""
    echo -e "${BLUE}════════════════════════════════════════════════════════════${NC}"
    echo -e "${BLUE}                      测试结果汇总${NC}"
    echo -e "${BLUE}════════════════════════════════════════════════════════════${NC}"
    echo ""
    echo "  总测试数:  $TOTAL_TESTS"
    echo -e "  ${GREEN}通过:      $PASSED_TESTS${NC}"
    echo -e "  ${RED}失败:      $FAILED_TESTS${NC}"
    echo ""
    
    if [ $FAILED_TESTS -eq 0 ]; then
        echo -e "  ${GREEN}✅ 所有测试通过！${NC}"
    else
        echo -e "  ${RED}❌ 有 $FAILED_TESTS 个测试失败${NC}"
    fi
    
    echo ""
    echo -e "${BLUE}════════════════════════════════════════════════════════════${NC}"
}

# 导出函数
export -f log_info log_success log_fail log_skip log_section
export -f api_get api_post api_put api_delete
export -f json_get
export -f assert_equals assert_not_empty assert_empty assert_contains assert_http_success
export -f init_test login print_summary
