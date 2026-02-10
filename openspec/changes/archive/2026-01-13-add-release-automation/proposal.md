# Change: 发布窗口发布自动化

## Why

当前发布窗口发布后需要手动执行版本更新、分支合并、打标签等操作，流程繁琐且容易出错。需要实现发布窗口发布后自动创建运行任务，自动化执行所有发布相关操作。

## What Changes

### 仓库初始版本管理
- **新增**：仓库新建时，从 master 分支的 pom.xml 获取初始版本号
- **新增**：支持手动设置初始版本号

### 迭代-仓库关联
- **新增**：仓库关联到迭代时，从 master 创建 feature/{iteration-key} 分支
- **新增**：自动设置开发版本号（升级中间版本 +1，添加 -SNAPSHOT）
- **新增**：保存基准版本、开发版本、目标版本到迭代-仓库关联表
- **新增**：支持版本冲突检测和解决

### 迭代关联发布窗口（新增）
- **新增**：迭代关联到发布窗口时，从 master 创建 release/{window-key} 分支
- **新增**：自动将迭代的 feature 分支合并到 release 分支
- **新增**：记录 release 分支名和合并时间

### 发布窗口代码合并功能（新增）
- **新增**：发布窗口提供"代码合并"功能，用于将 feature 分支最新代码合并到 release 分支
- **新增**：支持单迭代合并和批量合并
- **新增**：合并冲突检测和提示

### 发布窗口发布流程
- **修改**：发布窗口发布（DRAFT → PUBLISHED）时，自动创建 Run（运行任务）
- **新增**：Run 包含多个 RunTask，按顺序异步执行

### 运行任务类型
- **新增**：CLOSE_ITERATION - 关闭迭代
- **新增**：ARCHIVE_FEATURE_BRANCH - 归档 feature 分支
- **新增**：CREATE_RELEASE_BRANCH - 从 master 创建 release 分支（迭代关联窗口时）
- **新增**：MERGE_FEATURE_TO_RELEASE - feature 分支合并到 release（代码合并功能）
- **新增**：MERGE_RELEASE_TO_MASTER - release 分支合并到 master
- **新增**：CREATE_TAG - 在 master 上创建版本标签
- **新增**：UPDATE_POM_VERSION - 更新 POM 版本号（去除 SNAPSHOT）
- **新增**：TRIGGER_CI_BUILD - 触发 CI/CD 构建

### 任务执行机制
- **新增**：异步执行任务
- **新增**：失败重试机制（最大重试次数可配置）
- **新增**：执行记录页面展示任务状态

## Impact

- Affected specs: release-window, iteration, run
- Affected code:
  - `releasehub-domain/`: Iteration, ReleaseWindow, Run, RunTask 领域模型
  - `releasehub-application/`: IterationAppService, ReleaseWindowAppService, RunAppService, CodeMergeService
  - `releasehub-infrastructure/`: JPA 实体、GitLab 适配器、CI 触发器
  - `releasehub-interfaces/`: API 控制器
  - `release-hub-web/`: 执行记录页面、代码合并功能
