# Tasks: 发布窗口发布自动化

## 1. 数据模型扩展

- [x] 1.1 创建数据库迁移脚本：code_repository 表添加版本号字段
- [x] 1.2 创建数据库迁移脚本：iteration_repo 表添加版本号和分支字段
- [x] 1.3 创建数据库迁移脚本：window_iteration 表添加 release 分支字段
- [x] 1.4 创建数据库迁移脚本：run_task 表
- [x] 1.5 创建 RunTask 领域实体
- [x] 1.6 创建 RunTaskType 枚举
- [x] 1.7 创建 RunTaskStatus 枚举
- [x] 1.8 创建 MergeStatus 枚举
- [x] 1.9 创建 RunTaskJpaEntity
- [x] 1.10 扩展 CodeRepositoryJpaEntity 添加版本号字段
- [x] 1.11 扩展 IterationRepoJpaEntity 添加版本号和分支字段
- [x] 1.12 扩展 WindowIterationJpaEntity 添加 release 分支字段

## 2. 仓库初始版本号管理

- [x] 2.1 创建 VersionExtractor 接口（从仓库获取版本号）
- [x] 2.2 实现 MavenVersionExtractor（解析 pom.xml）
- [x] 2.3 实现 GradleVersionExtractor（解析 gradle.properties）
- [x] 2.4 创建 GitLabFileReader 接口（从 GitLab 读取文件）
- [x] 2.5 实现 MockGitLabFileReader（模拟实现）
- [x] 2.6 修改 CodeRepositoryAppService.create 方法，获取初始版本号
- [x] 2.7 添加手动设置初始版本号的 API

## 3. 仓库关联迭代版本管理

- [x] 3.1 创建 VersionDeriver 工具类（版本号推导）
- [x] 3.2 创建 GitLabBranchService 接口（分支操作）
- [x] 3.3 实现 MockGitLabBranchService（模拟实现）
- [x] 3.4 修改 IterationAppService.addRepo 方法：
  - [x] 3.4.1 获取 master 分支版本号
  - [x] 3.4.2 创建 feature 分支
  - [x] 3.4.3 推导开发版本和目标版本
  - [x] 3.4.4 更新 feature 分支的版本号
  - [x] 3.4.5 保存版本信息到关联记录

## 4. 版本冲突检测和解决

- [x] 4.1 创建 VersionConflict DTO
- [x] 4.2 创建 ConflictResolution 枚举
- [x] 4.3 实现 checkVersionConflict 方法
- [x] 4.4 实现 syncVersionFromRepo 方法
- [x] 4.5 实现 resolveConflict 方法
- [x] 4.6 添加版本同步和冲突解决 API

## 5. 迭代关联发布窗口时的分支管理

- [x] 5.1 创建 ReleaseBranchService 服务（release 分支操作）
- [x] 5.2 实现 createReleaseBranch 方法（从 master 创建 release 分支）
- [x] 5.3 实现 mergeFeatureToRelease 方法（feature → release 合并）
- [x] 5.4 创建 MergeResult DTO
- [x] 5.5 修改 WindowIterationAppService.attachIteration 方法：
  - [x] 5.5.1 检查 release 分支是否存在，不存在则创建
  - [x] 5.5.2 将迭代所有仓库的 feature 分支合并到 release
  - [x] 5.5.3 记录 release 分支名和合并时间
- [x] 5.6 添加 release 分支创建和合并的 API

## 6. 发布窗口代码合并功能

- [x] 6.1 创建 CodeMergeService 服务
- [x] 6.2 实现 mergeFeatureToRelease 方法（单迭代合并）
- [x] 6.3 实现 mergeAllFeaturesToRelease 方法（批量合并）
- [x] 6.4 实现合并冲突检测和返回
- [x] 6.5 添加代码合并 API：POST /api/v1/release-windows/{id}/merge
- [x] 6.6 添加单迭代合并 API：POST /api/v1/release-windows/{id}/iterations/{iterKey}/merge

## 7. 发布窗口发布流程

- [x] 7.1 修改 ReleaseWindow.publish 方法，验证关联迭代
- [x] 7.2 创建 ReleaseRunService（负责创建发布运行任务）
- [x] 7.3 实现 createReleaseRun 方法，生成所有 RunTask
- [x] 7.4 实现任务顺序逻辑

## 8. 异步任务执行

- [x] 8.1 配置 Spring Async
- [x] 8.2 创建 RunTaskExecutor 接口
- [x] 8.3 实现 CloseIterationTaskExecutor
- [x] 8.4 实现 ArchiveFeatureBranchTaskExecutor（模拟）
- [x] 8.5 实现 MergeFeatureToReleaseTaskExecutor（模拟）
- [x] 8.6 实现 MergeReleaseToMasterTaskExecutor（模拟）
- [x] 8.7 实现 CreateTagTaskExecutor（模拟）
- [x] 8.8 实现 UpdatePomVersionTaskExecutor（模拟）
- [x] 8.9 实现 TriggerCiBuildTaskExecutor（模拟）
- [x] 8.10 创建 RunExecutionService，协调任务执行

## 9. 重试机制

- [x] 9.1 实现 RetryPolicy 配置
- [x] 9.2 在 RunTaskExecutor 中集成重试逻辑
- [x] 9.3 实现手动重试 API

## 10. API 接口

- [x] 10.1 创建 GET /api/v1/runs/{id}/tasks 接口
- [x] 10.2 创建 POST /api/v1/runs/{id}/tasks/{taskId}/retry 接口
- [x] 10.3 创建 POST /api/v1/iterations/{id}/repos/{repoId}/sync-version 接口
- [x] 10.4 创建 POST /api/v1/iterations/{id}/repos/{repoId}/resolve-conflict 接口
- [x] 10.5 创建 RunTaskView DTO
- [x] 10.6 扩展 IterationRepoView DTO 包含版本信息
- [x] 10.7 扩展 WindowIterationView DTO 包含 release 分支信息

## 11. 前端页面

- [x] 11.1 修改迭代详情页，展示仓库版本信息（基准版本、开发版本、目标版本）
- [x] 11.2 添加版本同步按钮
- [x] 11.3 实现版本冲突解决对话框
- [x] 11.4 修改发布窗口详情页，添加"代码合并"功能按钮
- [x] 11.5 实现代码合并对话框（选择迭代/全部合并）
- [x] 11.6 展示合并结果和冲突信息
- [x] 11.7 修改执行记录详情页，展示 RunTask 列表
- [x] 11.8 实现任务状态显示（PENDING/RUNNING/COMPLETED/FAILED）
- [x] 11.9 实现失败任务重试按钮
- [x] 11.10 实现任务执行日志展示

## 12. 测试

- [x] 12.1 VersionDeriver 单元测试
- [x] 12.2 VersionExtractor 单元测试
- [x] 12.3 RunTask 领域实体单元测试
- [x] 12.4 VersionConflict 检测集成测试
- [x] 12.5 ReleaseRunService 集成测试
- [x] 12.6 RunExecutionService 集成测试
- [x] 12.7 ReleaseBranchService 集成测试
- [x] 12.8 CodeMergeService 集成测试
