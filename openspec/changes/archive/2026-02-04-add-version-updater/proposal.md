# Change: 添加版本号自动更新功能

## Why
版本号自动管理是 ReleaseHub 的核心 MVP 功能之一。当前系统缺少版本更新器（VersionUpdater）实现，无法完成"创建发布窗口 → 绑定仓库 → 执行版本更新 → 查看结果"的完整闭环。根据项目总体规划书，这是解决"执行成本"（版本号更新与校验依赖人工，易错且难规模化）的关键能力。

## What Changes
- **后端核心功能**：
  - 新增 VersionUpdater Port 接口（支持 Maven/Gradle 两种实现）
  - 实现 Maven VersionUpdater（pom.xml 版本更新、多模块支持、diff 生成）
  - 实现 Gradle VersionUpdater（gradle.properties 版本更新）
  - 版本更新执行服务集成到 Run 流程
  - 版本更新 API（`POST /api/v1/release-windows/{id}/execute/version-update`）

- **版本校验功能**：
  - 根据 VersionPolicy 推导目标版本
  - 根据 BranchRule 推导分支名
  - 版本号格式校验和冲突检测

- **前端集成**：
  - 版本更新执行 UI
  - 执行结果和 diff 展示
  - 错误处理和提示

- **测试与文档**：
  - VersionUpdater 单元测试和集成测试
  - API 文档更新

## Impact
- **Affected specs**: version-update（新增规格）
- **Affected code**: 
  - `releasehub-application`: VersionUpdater Port、版本更新服务
  - `releasehub-infrastructure`: Maven/Gradle VersionUpdater 实现
  - `releasehub-interfaces`: 版本更新 API 控制器
  - `releasehub-domain`: 版本值对象（如需要）
  - `release-hub-web`: 版本更新 UI 组件
- Requirement doc: [添加版本号自动更新功能](../../../requirements/completed/添加版本号自动更新功能.md)
