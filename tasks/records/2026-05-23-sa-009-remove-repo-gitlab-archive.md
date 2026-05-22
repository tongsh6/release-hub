# SA-009 移除仓库真实 GitLab 归档证据

## 目标

- 补齐 SA-009 “未挂载发布窗口的迭代移除仓库后，feature 分支按归档约定处理”的真实 GitLab 证据。
- 同时证明已挂载发布窗口的迭代仍禁止变更仓库集合，不能绕过锁定保护触发归档副作用。

## 变更

- 新增 `scripts/acceptance/sa009-remove-repo-gitlab-evidence.sh`：
  - 初始化真实 GitLab seed 和应用登录。
  - 创建独立叶子分组、真实 GitLab 项目、ReleaseHub 仓库引用和迭代。
  - 未挂窗路径：移除仓库后直查 GitLab，确认原 `feature/<iterationKey>` 不再活跃，`archive/unpublished/feature-<iterationKey>` 存在。
  - 已挂窗路径：挂载发布窗口后移除仓库被拒绝，仓库集合不变，原 feature 分支仍活跃，且未产生归档分支。
- 精简 `IterationAppService.removeRepos` 内的归档实现，复用既有 `archiveFeatureBranchForRepo` helper，避免更新迭代和移除仓库两条路径维护重复逻辑。
- 同步更新场景矩阵、项目台账和执行路线图：SA-009 P0 出队，下一队首为 SA-004 GitLab 连接异常诊断展示。

## 验证

```bash
bash -n scripts/acceptance/sa009-remove-repo-gitlab-evidence.sh
bash scripts/acceptance/sa009-remove-repo-gitlab-evidence.sh
mvn -q -pl releasehub-application -am -Dtest=IterationAppServiceTest -Dsurefire.failIfNoSpecifiedTests=false test
```

结果：

- SA-009 专用真实 GitLab 验收：`PASS=22 FAIL=0`。
- 未挂窗迭代移除仓库后，GitLab 直查确认原 feature 分支 `NOT_FOUND`，归档分支 `FOUND`。
- 已挂窗迭代移除仓库被拒绝；GitLab 直查确认原 feature 分支 `FOUND`，归档分支 `NOT_FOUND`。
- 应用层 `IterationAppServiceTest` 通过。

## 结论

- SA-009 P0 已覆盖，后续保持回归。
- 下一步按路线图推进 SA-004 GitLab 连接异常诊断展示。
