# Feature Specification: 代码仓库管理 (Repository Management)

**Feature Branch**: `feature/repository-management`  
**Created**: 2025-12-27  
**Status**: Draft  
**Input**: User description: "针对代码仓库管理功能，进行Spec模式开发"

## User Scenarios & Testing *(mandatory)*

### User Story 1 - 接入代码仓库 (Priority: P1)

作为 Release Manager，我希望将 GitLab 中的项目添加为 ReleaseHub 的受管仓库，以便对其发布流程进行管控。

**Why this priority**: 这是系统的基础数据源，没有仓库就无法进行后续的发布编排。

**Independent Test**:
- 可以独立测试添加仓库接口。
- 验证添加后数据库中存在该记录，且状态为 Active。

**Acceptance Scenarios**:

1. **Given** 一个有效的 GitLab 仓库 URL 和 Project ID，**When** 用户提交添加请求，**Then** 系统保存仓库信息并返回成功。
2. **Given** 一个不存在的 GitLab Project ID，**When** 用户提交添加请求，**Then** 系统返回错误提示“项目不存在或无权限”。
3. **Given** 重复添加同一个仓库，**When** 用户提交，**Then** 系统提示“仓库已存在”。

---

### User Story 2 - 仓库概览与同步 (Priority: P1)

作为开发者，我希望能看到仓库的活跃分支数量和 MR 统计，并能手动触发同步，以确保数据是最新的。

**Why this priority**: 提供实时的项目状态视图，辅助决策。

**Independent Test**:
- 调用同步接口，验证数据库中的分支/MR 计数被更新。

**Acceptance Scenarios**:

1. **Given** 已添加的仓库，**When** 进入详情页，**Then** 展示当前的分支数、MR 数和最后同步时间。
2. **Given** 仓库数据陈旧，**When** 点击“同步”按钮，**Then** 系统调用 GitLab API 更新数据，并刷新页面显示。

---

### User Story 3 - 分支规范检查 (Priority: P2)

作为 Tech Lead，我希望系统能自动检查分支命名是否符合规范（如 feature/*, fix/*），以便维护代码库整洁。

**Why this priority**: 规范化管理是 ReleaseHub 的核心价值之一。

**Independent Test**:
- 针对包含不合规分支名的仓库运行检查，验证返回了违规分支列表。

**Acceptance Scenarios**:

1. **Given** 包含 `temp/test` 分支的仓库，**When** 查看健康报告，**Then** 该分支被标记为“不合规”。
2. **Given** 只有 `feature/login` 和 `main` 分支的仓库，**When** 查看健康报告，**Then** 显示“合规”。

## Requirements *(mandatory)*

### Functional Requirements

- **FR-001**: 系统必须支持存储仓库的名称、GitLab Project ID、Clone URL 和默认分支。
- **FR-002**: 系统必须能够通过 GitLab API 获取指定 Project 的分支列表和 Merge Request 列表。
- **FR-003**: 系统必须支持对仓库元数据的增删改查（CRUD）。
- **FR-004**: 系统必须提供同步接口，用于从 GitLab 拉取最新元数据。
- **FR-005**: 仓库名称和 URL 必须在系统内唯一。

### Key Entities

- **CodeRepository**: 代表一个受管代码仓库，包含 projectId, name, cloneUrl 等核心属性。
- **BranchSummary**: 值对象，包含分支总数、活跃分支数、不合规分支数。
- **GateSummary**: 值对象，包含 MR 总数、Open MR 数等质量门禁指标。

## Success Criteria *(mandatory)*

### Measurable Outcomes

- **SC-001**: 添加仓库操作在 2 秒内完成（不包含初始全量同步）。
- **SC-002**: 仓库同步操作能正确反映 GitLab 侧的最新分支状态。
- **SC-003**: 前端详情页能正确展示后端返回的统计数据。
