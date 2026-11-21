# 2026-11-21 AIEF 项目画像接入与阶段校准

## 目标与完成边界

按维护者要求复核 ReleaseHub 的生命周期，并将个人 AIEF 上游最新的项目状态与推进约束合入本项目。只调整协作规则、画像、导航与当前事实口径；本记录不代表产品功能验收，也不把在研 C1 计为已交付。

正式生命周期由唯一画像维护为 `development`；当前计划对应 `system_refactoring`。这些分别表示研发中和当前工作模式。历史受控候选、当前代码完成度、当前任务阶段与产品范围分别记录，不用同一个“MVP”标签代替。

<a id="user-baseline"></a>
## 已有用户指令依据

以下是本会话已明确的维护者指令，不是 AI 从代码或版本号推断的事实：

- “现在是研发阶段，无历史包袱，该重构就重构”。
- “项目未上线，不保留旧路径、兼容层或双轨”。
- “新增实体、接口或设计前必须查重并核实已有对象是否真能承责、有实际消费者”。
- 先按愿景 → 元原则 → 设计 → 任务队列 → 实现核对；先独立定义正反例和定位依据，再核对现有 trace。
- 使用 SOLID、DRY、KISS、YAGNI 和关注点分离取舍，不为原则堆抽象。
- 按可评审粒度提交验证通过的改动；后续明确“分阶段推送”覆盖此前“不推送”的指令。
- 完成一个已授权切片即推进下一切片，反馈实质进展、阻塞或验收结果。
- 维护者提供的 ReleaseHub v2.6 定义和业务约束：窗口驱动、仓库重叠、仓内串行、MR-first、审计及安全重试。

“个人项目”的最新表述明确指向 AIEF 源项目，不能自动作为 ReleaseHub 的所有权确认。未明确的用户范围、支持承诺、部署责任、数据分类、保留/恢复和兼容承诺仍为 unknown；无历史包袱不等于数据可以随意删除。

## 上游版本与合入方式

- 上游：`https://github.com/tongsh6/ai-engineering-framework.git`。
- 本地源仓库：`/Users/loong/workspace/code/github/ai/ai-engineering-framework`。
- 原本地 main：`195162ba028276027d0b27ae84edae364a2843ff`。
- 本次核实的 GitHub main：`d0e0fb50c3e490c209225a4cc55d445ccebb1986`；更新日期 2026-11-13。
- 已在干净工作区执行 fast-forward，保留上游历史。上游四个新提交分别增加 Operating Context、通用 development、profile-only Doctor、CI 自动发现专项测试。
- 使用此源码修订提供的 canonical CLI；包内版本仍为 1.9.2，不能假定 npm 同名版本已经含这些新增能力。

| 上游资产 | ReleaseHub 接入位置 | 处理 |
|---|---|---|
| Project Operating Context | `docs/.ai/standards/patterns/project-operating-context.md` | 保留协议，仅映射本项目 Schema、实例、检查表路径并添加来源 |
| Project Profile Schema | `docs/.ai/standards/schemas/project-profile.schema.json` | 原样引入 |
| 行为验收基线 | `docs/.ai/standards/checklists/project-operating-context-acceptance.md` | 原样引入 |
| MIT 许可 | `docs/.ai/standards/LICENSE-AIEF` | 原样引入 |
| Profile 模板/字段治理 | `docs/context/PROJECT_PROFILE.yaml` | 按已有用户确认填写，未知事实显式保留 |
| AGENTS 与 Context Loading | 根 `AGENTS.md`、`docs/AGENTS.md`、既有 context 与适配入口 | 仅桥接和合并规则，不复制项目字段或知识树 |

ReleaseHub 原有 docs 布局已承担业务、经验、设计与任务事实；没有等价的机器可校验画像、上游 lock 或 AIEF submodule。新增画像由 AGENTS、工程指南及 Doctor 实际消费；Schema/规范供字段校验与策略核对使用，不新增运行时实体或策略引擎。

同时只读核对了个人 `aief-pm` 仓库（`85bd6cf607c67e23696d2926617f42f49ece95c5`）：其 CLI 实际使用 `aief.yml`、lock、install/compile 生成 `.aief/effective`，不合并既有 docs 文件，doctor 子命令尚为空提示。因此本次采用 AIEF Core 明确支持的既有 SSOT 手动接入与 `doctor --profile-only`，不引入另一套上下文编译目录。

<a id="release-evidence"></a>
## 生命周期核对：事实、推断与边界

| 证据 | 能支持的判断 | 不能支持的判断 |
|---|---|---|
| 维护者明确“研发阶段”及持续研发授权 | 正式生命周期记录 development | 不自行细化成 mvp_development、pre_release 或 production |
| 实际路由装配窗口、迭代、仓库、Run、版本操作、日历、数据质量、候选评审等模块 | 已有完整产品面，不是只有概念或脚手架 | 页面存在不证明每条行为正确 |
| B2 已提交的 Plan/Run/Item、窗口行锁、关闭受理、MR/Tag/CI 证据与失败传播实现 | 当前工作是对既有产品能力继续完善和收敛 | 不证明后台任务恢复已经交付 |
| 2026-05-24 受控候选记录：GitLab 场景 170 PASS、Playwright 全套 49 PASS | 曾形成受控候选与自动化证据 | 49 项包含 route stub，不能全称真实 GitLab 用户闭环；历史通过不覆盖新版语义 |
| 本地 v0.1.12 tag 对应 d450b78564cc69b01fe7ee788f6d53135507a1cc | 有版本化工程交付基线 | tag、候选报告不自动等于正式上线或公开发布 |
| B2 本轮普通后端 613 项、数据库/API/HTTP 70 项、前端 149 项及静态门禁通过 | 已提交研发基线有当前回归证据 | 不代替当前真实 GitLab/UI 全旅程、独立进程中断和长期生产运行 |
| C1 工作区 52 个路径已改，RunPort 新契约与 adapter 尚未闭合 | C1 是未完成研发切片 | 不能记为编译通过、验收完成；也不能倒推 B2 历史结果失效 |

源码依据：`frontend/src/router/routes.ts`；B2 后端提交 `e127023d18b8d03b4756b2daaadf9e8674638dc1` 的 `RunPlanAppService`、`RunAppService`、`RunJpaPersistenceAdapter`、`RunPlanApiIT`。历史依据：`docs/reports/release-candidate-2026-05-23.md`、`tasks/records/2026-05-24-sa-001-controlled-rc-validation.md`。当前验证依据：项目台账、场景矩阵与 2026-11-21 SA-013 研发记录。

因此“已有产品基础、正在完善功能并收敛执行可靠性”是工作描述；正式生命周期采用维护者已经确认的 development。everReleased/channel 保留 null/unknown，避免把“未上线”与“从未形成版本”混成同一事实；这不阻碍已授权研发。

## 对推进方式的具体影响

1. 由确认目标、完整蓝图和实际缺口决定实现范围，不能借“尽快交付 MVP”遗漏幂等、未知结果、持久记录、真实消费者或必要恢复设计。
2. 架构复杂度按实际责任与消费者选择；删除旧写路径可以直接推进，不为未来可能性新增通用 DAG/调度平台。
3. 计划依赖图与产品运行时模型分开；需要明确先后关系，不表示必须新建 Dependency/Executor/Registry 实体。
4. Profile 管项目事实和稳定约束；台账管交付证据；路线图管唯一队首；OpenSpec/tasks 管任务阶段。现有任务顺序保留。
5. 真实数据、GitLab 远端写入和历史证据按实际责任保护；unknown 不解释为 none。
6. 候选评审、发布准备或生产阶段只能在维护者确认及对应证据成立后更新，历史标签不自动改变当前阶段。

## 本次验收

接入与专项验证已完成：

- 显式路径及根 AGENTS 自动发现两种 Doctor 输出一致：`scope=profile`，2 PASS、1 预期 unknown WARNING、0 FAIL、0 blockingFail。
- Schema、行为基线与许可三个原样引入资产逐字节匹配上游；只存在一份 `PROJECT_PROFILE.yaml`。
- 99 条本地 Markdown 链接全部可达；路线图唯一 HEAD 仍为 SA-013；`git diff --check` 通过。
- 原有 52 个 C1 路径逐一 SHA-256 一致，index 保持为空；没有把未闭合业务实现纳入此切片。
- 固定上游的 lifecycle/profile-only 专项测试最终 18/18 PASS。首次为 17 PASS/1 FAIL，原因是 macOS 临时目录 `/var` 与 `/private/var` 指向同一位置而测试比较文本路径；将本次测试 TMPDIR 设为真实路径后通过，未修改上游源码或断言。该问题未影响 ReleaseHub 实际路径的 Doctor 校验。
- 交叉审查确认入口职责、来源引用和历史 49 项 Playwright 的 stub 边界；同步修正矩阵里“C1 尚未实现”的旧描述，并将路线图误指 C1 的 194 项历史测试归还到 A/B1 基线。

本次只有文档、画像与协作规则变更，采用对应的结构、来源、引用、队列和保护校验；没有启动 C1 业务全量构建或声称新增产品验收。证据：`.ai/reports/validation/aief-profile-doctor-explicit.json`、`aief-profile-doctor-discovered.json`、`aief-adoption-integrity.json`、`aief-source-checks.json`。

验证命令使用固定上游源码；canonical 包依赖为 ajv/yaml，需先在源仓库按 package.json 安装：

```bash
node /Users/loong/workspace/code/github/ai/ai-engineering-framework/packages/ai-engineering-framework-init/bin/aief-init.mjs doctor --profile-only --profile-path docs/context/PROJECT_PROFILE.yaml --json
bash scripts/dev/check-roadmap.sh
git diff --check
```

专项 Doctor 的通过只证明 Profile 结构、声明路径和记录的来源规则，不证明模型实际行为、产品验收或整仓 Doctor。未知事实产生的 warning 必须保留，不能为消除 warning 填成 none。

工作区保护快照：`.ai/reports/validation/aief-c1-preservation-baseline.json`。接入前 52 个 C1 路径的内容哈希与 staged 状态已记录；本次提交只包含协作治理改动，不提交未完成的 C1。
