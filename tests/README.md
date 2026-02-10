# ReleaseHub 自动化测试

基于用户故事的端到端自动化测试脚本。

## 目录结构

```
tests/
├── run_all_tests.sh           # 测试运行器入口
├── smoke_test.sh              # 冒烟测试（完整工作流）
├── e2e_test_suite.sh          # E2E 综合测试套件
├── us_release_window_test.sh  # US-RW: 发布窗口测试
├── us_repository_test.sh      # US-REPO: 仓库管理测试
├── us_version_update_test.sh  # US-VU: 版本更新测试
├── us_version_validation_test.sh # US-VAL: 版本校验测试
├── test_utils.sh              # 测试工具函数库
└── README.md
```

## 前置条件

1. **后端服务运行中**
   ```bash
   cd release-hub
   ./mvnw spring-boot:run -pl releasehub-bootstrap
   ```

2. **依赖工具**
   - `curl` - HTTP 请求
   - `python3` - JSON 解析

## 使用方法

### 运行所有测试

```bash
cd tests
chmod +x *.sh
./run_all_tests.sh
```

### 运行特定测试套件

```bash
# 发布窗口测试
./run_all_tests.sh rw

# 仓库管理测试
./run_all_tests.sh repo

# 版本更新测试
./run_all_tests.sh vu

# 版本校验测试
./run_all_tests.sh val

# E2E 综合测试
./run_all_tests.sh e2e

# 冒烟测试（迭代-仓库-窗口完整流程）
./run_all_tests.sh smoke
```

### 直接运行单个测试文件

```bash
./us_release_window_test.sh
```

## 冒烟测试 (Smoke Test)

快速验证系统核心工作流是否正常：

```bash
./run_all_tests.sh smoke
# 或直接运行
./smoke_test.sh
```

**测试场景：**

```
┌─────────────────────────────────────────────────────────┐
│  发布窗口 1 (Q1)                                        │
│    ├── 迭代 1 → 仓库 frontend-web, frontend-mobile     │
│    ├── 迭代 2 → 仓库 backend-api, backend-gateway      │
│    └── 迭代 3 → 仓库 backend-auth, service-order       │
├─────────────────────────────────────────────────────────┤
│  发布窗口 2 (Q2)                                        │
│    ├── 迭代 4 → 仓库 service-payment, service-notif    │
│    └── 迭代 5 → 仓库 common-lib, infra-config          │
└─────────────────────────────────────────────────────────┘
```

**验证内容：**
1. ✅ 创建 10 个代码仓库
2. ✅ 创建 5 个迭代，每个关联 2 个仓库
3. ✅ 创建 2 个发布窗口
4. ✅ 将迭代挂载到发布窗口
5. ✅ 验证挂载关系
6. ✅ 查看发布计划

---

## 用户故事覆盖

### US-RW: 发布窗口管理

| ID | 用户故事 | 测试覆盖 |
|---|---|---|
| US-RW-001 | 创建发布窗口 | ✅ |
| US-RW-002 | 发布窗口状态流转 | ✅ |
| US-RW-003 | 冻结/解冻窗口 | ✅ |

### US-REPO: 代码仓库管理

| ID | 用户故事 | 测试覆盖 |
|---|---|---|
| US-REPO-001 | 添加代码仓库 | ✅ |
| US-REPO-002 | 查看仓库列表 | ✅ |
| US-REPO-003 | 查看仓库详情 | ✅ |
| US-REPO-004 | 更新仓库信息 | ✅ |
| US-REPO-005 | 删除仓库 | ✅ |
| US-REPO-006 | 仓库统计信息 | ✅ |

### US-VU: 版本更新

| ID | 用户故事 | 测试覆盖 |
|---|---|---|
| US-VU-001 | 单仓库版本更新 | ✅ |
| US-VU-002 | 批量版本更新 | ✅ |
| US-VU-003 | Diff 预览 | ✅ |
| US-VU-004 | Maven 版本更新 | ✅ |
| US-VU-005 | Gradle 版本更新 | ✅ |
| US-VU-006 | 运行记录查看 | ✅ |

### US-VAL: 版本校验

| ID | 用户故事 | 测试覆盖 |
|---|---|---|
| US-VAL-001 | 版本号推导 | ✅ |
| US-VAL-002 | 版本格式校验 | ✅ |
| US-VAL-003 | 版本策略管理 | ✅ |
| US-VAL-004 | 日期版本策略 | ✅ |

## 配置

环境变量：

```bash
# 后端 API 地址（默认）
export BASE_URL="http://localhost:8080/api/v1"
```

## 测试报告

测试完成后会显示汇总报告：

```
════════════════════════════════════════════════════════════
                   测试套件执行汇总
════════════════════════════════════════════════════════════

  ✓ US-RW: 发布窗口管理
  ✓ US-REPO: 代码仓库管理
  ✓ US-VU: 版本更新
  ✓ US-VAL: 版本校验

────────────────────────────────────────────────────────────
  总套件数:    4
  通过:        4
  失败:        0

  ✅ 所有测试套件通过！

════════════════════════════════════════════════════════════
```

## 扩展测试

添加新测试时：

1. 创建新的测试文件 `us_xxx_test.sh`
2. 引入工具库 `source "$(dirname "$0")/test_utils.sh"`
3. 使用断言函数验证结果
4. 在 `run_all_tests.sh` 中添加测试套件

示例：

```bash
#!/bin/bash
source "$(dirname "$0")/test_utils.sh"

test_example() {
    log_section "测试示例"
    
    local response=$(api_get "/example")
    local code=$(json_get "$response" "['code']")
    
    assert_equals "OK" "$code" "API 返回成功"
}

main() {
    init_test
    login
    test_example
    print_summary
}

main "$@"
```
