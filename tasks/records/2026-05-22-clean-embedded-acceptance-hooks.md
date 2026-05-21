# 清理嵌入式验收钩子

日期：2026-05-22

## 背景

- 复盘验收自动化和运行时代码后，发现仓库/Git 相关链路存在测试便利逻辑外溢：
  - 领域和应用层默认 `gitProvider` 曾落到测试替身 provider。
  - 后端曾接受 `mock:` clone URL。
  - 前端曾保留环境变量驱动的登录/用户 mock API。
  - 仓库配置 UI 曾向用户展示测试 provider。
  - Git 适配器曾把测试替身作为公开 provider 参与运行时选择。
- 这些路径会让验收证据和真实生产路径混在一起，降低后续场景验收可信度。

## 清理范围

- 移除公开 `MOCK` Git provider，仓库默认 provider 收敛为 `GITLAB`。
- `CloneUrl` 与前端 cloneUrl 校验均拒绝 `mock:` scheme。
- 仓库创建/更新接口对非法 provider 返回参数错误；缺省 provider 不再落到测试替身。
- 前端移除 `VITE_USE_MOCK` 登录/用户分支及 `frontend/src/api/mock/auth.ts`。
- 仓库编辑弹窗移除测试 provider 选项，默认使用 `GITLAB`。
- 原测试替身适配器改为显式 test profile 下的 `InMemoryGitLabBranchAdapter` / `InMemoryGitLabFileAdapter`，并以 `GITLAB` provider 服务测试环境。
- 版本更新、冲突检测、分支适配器工厂不再包含 `null -> mock` 或 `provider != MOCK` 的运行时分支。

## 复查结果

- 生产路径复扫：

```bash
rg -n "GitProvider\\.MOCK|\\bMOCK\\b|mock:|mock-|MockProvider|VITE_USE_MOCK|MockGit" \
  backend/releasehub-*/src/main frontend/src --glob '!**/__tests__/**' -S
```

- 结果：无命中。
- 剩余 `mock://` 仅保留在 `cloneUrl` 单测中，用于断言该 scheme 被拒绝。
- WireMock 依赖仍保留为真实 HTTP adapter 测试工具，不属于运行时验收钩子。

## 验证

```bash
mvn -q -pl releasehub-domain,releasehub-application,releasehub-infrastructure,releasehub-interfaces -am test -DskipITs
pnpm run typecheck
pnpm run lint
pnpm exec vitest run src/utils/__tests__/cloneUrl.spec.ts src/views/release-window/__tests__/VersionUpdateDialog.spec.ts src/views/iteration/__tests__/AddReposDialog.spec.ts
git diff --check
bash scripts/dev/static-scan-topn.sh 5
```

- 后端核心模块测试：PASS。
- 前端 typecheck：PASS。
- 前端 lint：PASS。
- Focused Vitest：7 PASS。
- `git diff --check`：PASS。
- 静态扫描报告：`.ai/reports/static-scan/20260522-011556/summary.md`
  - backend SpotBugs：PASS，0 bugs。
  - frontend ESLint：PASS。
  - frontend typecheck：PASS。

## 结论

- 运行时代码、公开 API、仓库 UI 和 clone URL 校验中的嵌入式验收/Mock 钩子已清理干净。
- 测试替身被限制在显式 test profile 和测试代码中，不再作为业务 provider 暴露给用户或生产路径。
