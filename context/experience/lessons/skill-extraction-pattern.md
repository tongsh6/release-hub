# 会话技能点提取与分类归纳

> 归档自 `.trae/documents/会话技能点提取与分类归纳.md`（Trae 会话产出）

## 技能点分类

从仓库管理功能开发会话中提取的 7 大技能领域：

### 1. 规范驱动开发（OpenSpec）

- **OpenSpec 管理闭环**：提案→实现→验收，`openspec/changes/<change-id>/` 结构
- **需求文档互链门禁**：`requirements/in-progress/` → `requirements/completed/`
- **交付归档**：change 迁移到 archive，修复互链

### 2. 后端 DDD / 应用服务 / 错误码

- **应用服务编排**：`CodeRepositoryAppService` 封装用例，`@Transactional` 划定事务
- **统一错误码**：`BusinessException.gitlabSettingsMissing()` → `GITLAB_001`
- **DTO 与 Domain 校验分工**：DTO 允许空但做长度校验，AppService 归一化默认值，Domain 保持严格

### 3. 数据库迁移与实体一致性

- **schema 与接口校验一致**：接口允许 512 则 DB 列也需 VARCHAR(512)
- **Flyway 迁移**：优先使用 Flyway 管控，避免 Hibernate ddl-auto 漂移

### 4. 第三方集成（GitLab API）

- **cloneUrl 解析**：正则解析 SSH/HTTPS → project path → GitLab API
- **口径拆分**：sync（统计同步）与 sync-version（版本提取）独立接口

### 5. 前端 Vue 3 + Element Plus

- **统一错误提示**：axios 拦截器 → `ApiError` → `handleError(e)`
- **表单回显补齐**：二次请求获取详情接口不返回的字段
- **i18n 缺失 key 定位**：全局搜索 `t('xxx')` → 补齐或移除

### 6. 测试与验证

- **后端集成测试**：`@SpringBootTest + @AutoConfigureMockMvc`，先登录再断言
- **前端质量门禁**：`pnpm typecheck && pnpm lint && pnpm test`

### 7. 通用问题解决模式

- **现象→反推缺口**：用户现象 → UI 渲染/catch 点 → 接口契约 → 数据源 → 修复
