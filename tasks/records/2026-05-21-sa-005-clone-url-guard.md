# SA-005 Clone URL 纳管保护

日期：2026-05-21

## 用户旅行图

1. 系统管理员进入仓库管理页，点击新增/同步仓库。
2. 管理员输入仓库名称、Clone URL、叶子分组、默认分支和 Git Provider。
3. 前端在输入侧校验 Clone URL 是否为受支持的 Git 地址。
4. 后端在创建/更新前统一解析 Clone URL，并按规范化 key 检查是否已被其它仓库纳管。
5. 如果 SSH/HTTP(S)/`.git`/大小写差异指向同一仓库，系统以 `REPO_012` 拒绝重复纳管；如果 URL 不可解析，系统以 `REPO_013` 拒绝。

## 实现范围

- 领域层新增 `CloneUrl`，集中处理 URL 解析、格式校验和规范化。
- 应用层创建/更新仓库前执行规范化重复检查，并跳过历史不可解析 URL，避免存量脏数据阻断新仓库纳管。
- 前端仓库编辑弹窗复用 `isSupportedCloneUrl` 做即时格式校验，并提交前 trim 关键字段。
- 后端中英文错误消息补充 `REPO_012`、`REPO_013`。

## 验证

- `mvn -pl releasehub-domain,releasehub-application -am -Dtest=CodeRepositoryTest,CodeRepositoryAppServiceTest -Dsurefire.failIfNoSpecifiedTests=false test`
  - `CodeRepositoryTest`: 8 PASS
  - `CodeRepositoryAppServiceTest`: 10 PASS
- `mvn -pl releasehub-bootstrap -am -Dtest=RepositoryE2ETest -Dsurefire.failIfNoSpecifiedTests=false test`
  - `RepositoryE2ETest`: 11 PASS
- `pnpm exec vitest run src/utils/__tests__/cloneUrl.spec.ts`
  - 2 PASS

## 非目标

- 本轮不实现仓库删除保护扩展、组织筛选、错误 URL 连通性探测或 GitLab 权限诊断细分。
- Clone URL 唯一性当前在应用层基于规范化 key 校验；后续如需要抗并发唯一性，可引入持久化 normalized key 与唯一索引作为独立迁移切片。
