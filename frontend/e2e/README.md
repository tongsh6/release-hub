# E2E 自动化测试

基于 Puppeteer 的前端端到端自动化测试。

## 目录结构

```
e2e/
├── puppeteer.config.ts     # Puppeteer 配置
├── run-all.ts              # 测试运行器（汇总执行所有测试）
├── tsconfig.json           # TypeScript 配置
├── README.md               # 本文档
├── utils/
│   └── test-helper.ts      # 测试工具类和辅助函数
├── tests/
│   ├── login.test.ts       # 登录页面测试
│   ├── navigation.test.ts  # 导航和布局测试
│   ├── release-window.test.ts  # 发布窗口测试
│   ├── repository.test.ts  # 仓库管理测试
│   └── iteration.test.ts   # 迭代管理测试
└── screenshots/            # 测试截图（自动生成）
```

## 安装依赖

```bash
cd release-hub-web
pnpm install
```

## 运行测试

### 前置条件

1. 启动后端服务：
```bash
cd release-hub/releasehub-bootstrap
./mvnw spring-boot:run
```

2. 启动前端开发服务器：
```bash
cd release-hub-web
pnpm dev
```

### 运行所有测试

```bash
pnpm test:e2e
```

### 运行单个测试套件

```bash
# 登录测试
pnpm test:e2e:login

# 导航测试
pnpm test:e2e:navigation

# 发布窗口测试
pnpm test:e2e:release-window

# 仓库管理测试
pnpm test:e2e:repository

# 迭代管理测试
pnpm test:e2e:iteration
```

### 有界面模式运行（调试用）

```bash
pnpm test:e2e:headed
```

## 配置选项

通过环境变量配置测试参数：

| 环境变量 | 默认值 | 说明 |
|---------|--------|------|
| `E2E_BASE_URL` | `http://localhost:5173` | 前端服务地址 |
| `E2E_API_URL` | `http://localhost:8080` | 后端 API 地址 |
| `E2E_HEADLESS` | `true` | 是否无头模式运行 |
| `E2E_SLOW_MO` | `0` | 操作延迟（毫秒），调试用 |
| `E2E_DEVTOOLS` | `false` | 是否打开开发者工具 |
| `E2E_USERNAME` | `admin` | 测试用户名 |
| `E2E_PASSWORD` | `admin123` | 测试密码 |

### 示例

```bash
# 使用自定义配置运行
E2E_BASE_URL=http://localhost:3000 E2E_HEADLESS=false pnpm test:e2e

# 慢速模式（方便观察）
E2E_SLOW_MO=100 E2E_HEADLESS=false pnpm test:e2e:login
```

## 测试覆盖范围

### 登录页面 (`login.test.ts`)
- ✅ 登录页面正确渲染
- ✅ 空表单提交显示验证错误
- ✅ 错误凭据显示错误消息
- ✅ 记住我复选框切换
- ✅ 成功登录后跳转
- ✅ Enter 键提交表单

### 导航和布局 (`navigation.test.ts`)
- ✅ 主布局正确渲染
- ✅ 侧边栏菜单导航
- ✅ 各主要页面可访问
- ✅ 面包屑导航
- ✅ 移动端视口适配
- ✅ 404 页面处理

### 发布窗口 (`release-window.test.ts`)
- ✅ 列表页正确渲染
- ✅ 搜索功能
- ✅ 创建对话框
- ✅ 表格分页
- ✅ 查看详情
- ✅ 状态标签显示
- ✅ 重置搜索条件

### 仓库管理 (`repository.test.ts`)
- ✅ 列表页正确渲染
- ✅ 搜索功能
- ✅ 查看详情
- ✅ 创建表单
- ✅ 表格列显示
- ✅ 分组筛选
- ✅ 编辑功能
- ✅ 同步状态显示

### 迭代管理 (`iteration.test.ts`)
- ✅ 列表页正确渲染
- ✅ 创建对话框
- ✅ 查看详情
- ✅ 关联发布窗口
- ✅ 状态标签显示

## 编写新测试

### 基本模板

```typescript
import { TestRunner, PageHelper, Assertions, delay } from '../utils/test-helper'

const runner = new TestRunner()

// 辅助函数：确保已登录
async function ensureLoggedIn(): Promise<void> {
  const auth = runner.getAuthHelper()
  const helper = runner.getHelper()
  
  await helper.navigate('/')
  await delay(500)
  
  const page = runner.getContext().getPage()
  if (page.url().includes('/login')) {
    await auth.login()
  }
}

// 测试用例
runner.test('测试名称', async () => {
  await ensureLoggedIn()
  
  const helper = runner.getHelper()
  const assertions = runner.getAssertions()
  
  // 导航到页面
  await helper.navigate('/your-page')
  await delay(1000)
  
  // 执行断言
  await assertions.elementExists('.your-element')
  
  // 截图
  await helper.screenshot('test-screenshot')
})

// 运行测试
runner.run().catch(console.error)
```

### 常用工具方法

#### PageHelper

```typescript
const helper = runner.getHelper()

// 导航
await helper.navigate('/path')

// 点击元素
await helper.clickElement('.selector')

// 输入文本
await helper.typeText('.input', 'text')
await helper.clearAndType('.input', 'new text')

// 等待
await helper.waitForText('some text')
await helper.waitForLoading()
await helper.waitForTableData()

// 检查元素
const exists = await helper.elementExists('.selector')
const text = await helper.getTextContent('.selector')

// Element Plus 组件
await helper.selectOption('.el-select', 'option text')
await helper.waitForMessage('success')
await helper.confirmDialog()

// 截图
await helper.screenshot('name')
```

#### Assertions

```typescript
const assertions = runner.getAssertions()

await assertions.elementExists('.selector')
await assertions.elementNotExists('.selector')
await assertions.textContentEquals('.selector', 'expected')
await assertions.textContentContains('.selector', 'partial')
await assertions.inputValueEquals('.input', 'value')
await assertions.urlContains('/path')
await assertions.tableRowCount(5)
```

## 截图

- 测试失败时自动截图
- 可在测试中手动调用 `helper.screenshot('name')`
- 截图保存在 `e2e/screenshots/` 目录
- 文件名格式：`{name}-{timestamp}.png`

## 故障排除

### 测试超时

增加配置中的超时时间，或检查前端/后端服务是否正常运行。

### 元素找不到

1. 检查选择器是否正确
2. 增加 `delay()` 等待时间
3. 使用 `E2E_HEADLESS=false` 运行查看实际情况

### 登录失败

1. 确认测试用户凭据正确
2. 检查后端服务是否运行
3. 查看 `E2E_USERNAME` 和 `E2E_PASSWORD` 环境变量

### 权限问题

某些操作可能需要特定权限，确保测试用户有足够权限。
