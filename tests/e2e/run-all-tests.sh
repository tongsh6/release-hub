#!/bin/bash
# ============================================================
# ReleaseHub 自动化测试运行器
# ============================================================

set -e

SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
cd "$SCRIPT_DIR"

# 颜色
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
NC='\033[0m'

# 测试结果
declare -a TEST_RESULTS
TOTAL_SUITES=0
PASSED_SUITES=0
FAILED_SUITES=0

# ============================================================
# 辅助函数
# ============================================================

print_banner() {
    echo ""
    echo -e "${BLUE}╔════════════════════════════════════════════════════════════╗${NC}"
    echo -e "${BLUE}║         ReleaseHub 自动化测试套件                          ║${NC}"
    echo -e "${BLUE}║         基于用户故事的端到端测试                           ║${NC}"
    echo -e "${BLUE}╚════════════════════════════════════════════════════════════╝${NC}"
    echo ""
}

check_prerequisites() {
    echo -e "${BLUE}[检查] 前置条件${NC}"
    
    # 检查 curl
    if ! command -v curl &> /dev/null; then
        echo -e "${RED}错误: curl 未安装${NC}"
        exit 1
    fi
    echo "  ✓ curl 已安装"
    
    # 检查 python3
    if ! command -v python3 &> /dev/null; then
        echo -e "${RED}错误: python3 未安装${NC}"
        exit 1
    fi
    echo "  ✓ python3 已安装"
    
    # 检查后端服务
    local health=$(curl -s -o /dev/null -w "%{http_code}" "http://localhost:8080/actuator/health" 2>/dev/null || echo "000")
    if [ "$health" != "200" ]; then
        echo -e "${RED}错误: 后端服务不可用 (HTTP: $health)${NC}"
        echo ""
        echo "请先启动后端服务:"
        echo "  cd release-hub && ./mvnw spring-boot:run -pl releasehub-bootstrap"
        echo ""
        exit 1
    fi
    echo "  ✓ 后端服务正常"
    
    echo ""
}

run_test_suite() {
    local suite_name="$1"
    local suite_file="$2"
    
    ((TOTAL_SUITES++))
    
    echo -e "${BLUE}━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━${NC}"
    echo -e "${BLUE}运行: $suite_name${NC}"
    echo -e "${BLUE}━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━${NC}"
    
    if [ -f "$suite_file" ]; then
        chmod +x "$suite_file"
        if bash "$suite_file"; then
            ((PASSED_SUITES++))
            TEST_RESULTS+=("${GREEN}✓${NC} $suite_name")
        else
            ((FAILED_SUITES++))
            TEST_RESULTS+=("${RED}✗${NC} $suite_name")
        fi
    else
        echo -e "${YELLOW}跳过: 文件不存在 - $suite_file${NC}"
        TEST_RESULTS+=("${YELLOW}○${NC} $suite_name (跳过)")
    fi
    
    echo ""
}

print_summary() {
    echo ""
    echo -e "${BLUE}╔════════════════════════════════════════════════════════════╗${NC}"
    echo -e "${BLUE}║                   测试套件执行汇总                          ║${NC}"
    echo -e "${BLUE}╚════════════════════════════════════════════════════════════╝${NC}"
    echo ""
    
    for result in "${TEST_RESULTS[@]}"; do
        echo -e "  $result"
    done
    
    echo ""
    echo -e "${BLUE}────────────────────────────────────────────────────────────${NC}"
    echo "  总套件数:    $TOTAL_SUITES"
    echo -e "  ${GREEN}通过:        $PASSED_SUITES${NC}"
    echo -e "  ${RED}失败:        $FAILED_SUITES${NC}"
    echo ""
    
    if [ $FAILED_SUITES -eq 0 ]; then
        echo -e "  ${GREEN}✅ 所有测试套件通过！${NC}"
    else
        echo -e "  ${RED}❌ 有 $FAILED_SUITES 个测试套件失败${NC}"
    fi
    
    echo ""
    echo -e "${BLUE}════════════════════════════════════════════════════════════${NC}"
}

# ============================================================
# 主函数
# ============================================================

main() {
    print_banner
    check_prerequisites
    
    # 运行各测试套件
    case "${1:-all}" in
        all)
            run_test_suite "US-RW: 发布窗口管理" "$SCRIPT_DIR/us-release-window-test.sh"
            run_test_suite "US-REPO: 代码仓库管理" "$SCRIPT_DIR/us-repository-test.sh"
            run_test_suite "US-VU: 版本更新" "$SCRIPT_DIR/us-version-update-test.sh"
            run_test_suite "US-VAL: 版本校验" "$SCRIPT_DIR/us-version-validation-test.sh"
            ;;
        rw)
            run_test_suite "US-RW: 发布窗口管理" "$SCRIPT_DIR/us-release-window-test.sh"
            ;;
        repo)
            run_test_suite "US-REPO: 代码仓库管理" "$SCRIPT_DIR/us-repository-test.sh"
            ;;
        vu)
            run_test_suite "US-VU: 版本更新" "$SCRIPT_DIR/us-version-update-test.sh"
            ;;
        val)
            run_test_suite "US-VAL: 版本校验" "$SCRIPT_DIR/us-version-validation-test.sh"
            ;;
        e2e)
            run_test_suite "E2E 综合测试" "$SCRIPT_DIR/e2e-test-suite.sh"
            ;;
        smoke)
            run_test_suite "冒烟测试: 迭代-仓库-窗口完整流程" "$SCRIPT_DIR/smoke-test.sh"
            ;;
        auto)
            run_test_suite "US-AUTO: 发布自动化功能" "$SCRIPT_DIR/us-release-automation-test.sh"
            ;;
        *)
            echo "用法: $0 [all|rw|repo|vu|val|e2e|smoke]"
            echo ""
            echo "  all   - 运行所有测试套件（默认）"
            echo "  rw    - 仅运行发布窗口测试"
            echo "  repo  - 仅运行仓库管理测试"
            echo "  vu    - 仅运行版本更新测试"
            echo "  val   - 仅运行版本校验测试"
            echo "  e2e   - 运行 E2E 综合测试"
            echo "  smoke - 运行冒烟测试（迭代-仓库-窗口完整流程）"
            echo "  auto  - 运行发布自动化功能测试"
            exit 1
            ;;
    esac
    
    print_summary
    
    # 返回状态码
    if [ $FAILED_SUITES -gt 0 ]; then
        exit 1
    fi
}

main "$@"
