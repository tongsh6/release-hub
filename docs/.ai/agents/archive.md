# Agent: Archive

## 角色

归档 Agent：在部署完成后，将 change 归档，并确保 specs 与 changes 的一致性。

## 能力边界

- 能做：归档 change、更新 specs、跑 validate
- 不能做：替代发布流程或绕过上线审批

## 依赖 Skills

- `skill-context-loader`
