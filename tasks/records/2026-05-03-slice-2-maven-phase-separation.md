# Slice 2: Maven surefire/failsafe 分离

- **蓝图归属**：`docs/superpowers/specs/2026-05-03-test-system-restructure-design.md` 第 2 部分
- **日期**：2026-05-03
- **执行者**：AI
- **状态**：待启动

## 事前约束检查

| 约束 | 状态 | 说明 |
|------|------|------|
| 蓝图归属 | ✅ | Maven 测试阶段分离，蓝图第二部分 |
| 用户价值 | ✅ | `mvn test` 秒级反馈 vs `mvn verify` 完整验证 |
| 端到端路径 | ✅ | Build config → 测试执行 → 报告 |
| 单一目标 | ✅ | 只做 surefire/failsafe 分离 |
| 可独立验证 | ✅ | `mvn test` 不含 *ApiTest/*IT/*E2eTest |
| 可回滚 | ✅ | git revert POM 文件 |
| 依赖明确 | ✅ | 依赖 Slice 1（profile 清晰后知道哪些测试归属 e2e） |
| 风险收敛 | ✅ | POM 配置变更，不触及业务代码 |

## 涉及文件

| 文件 | 操作 | 层 |
|------|------|-----|
| `backend/pom.xml` | 修改：`<pluginManagement>` 新增 failsafe 配置 | Build |
| `backend/releasehub-bootstrap/pom.xml` | 修改：移除 surefire TESTCONTAINERS env，挪到 failsafe configuration | Build |
| 所有模块 `pom.xml` | 检查：确认 surefire 默认配置不被覆盖 | Build |

## 关键配置

### surefire（父 POM）
```xml
<plugin>
    <artifactId>maven-surefire-plugin</artifactId>
    <configuration>
        <includes><include>**/*Test.java</include></includes>
        <excludes>
            <exclude>**/*ApiTest.java</exclude>
            <exclude>**/*IT.java</exclude>
            <exclude>**/*E2eTest.java</exclude>
        </excludes>
    </configuration>
</plugin>
```

### failsafe（父 POM）
```xml
<plugin>
    <artifactId>maven-failsafe-plugin</artifactId>
    <configuration>
        <includes>
            <include>**/*ApiTest.java</include>
            <include>**/*IT.java</include>
            <include>**/*E2eTest.java</include>
        </includes>
        <environmentVariables>
            <TESTCONTAINERS_RYUK_DISABLED>true</TESTCONTAINERS_RYUK_DISABLED>
        </environmentVariables>
    </configuration>
    <executions>
        <execution>
            <goals>
                <goal>integration-test</goal>
                <goal>verify</goal>
            </goals>
        </execution>
    </executions>
</plugin>
```

## 执行步骤

### Step 1: 父 POM 配置
- `<pluginManagement>` 中声明 surefire（排除规则）+ failsafe（include 规则 + integration-test/verify goals）

### Step 2: bootstrap POM 适配
- 移除 surefire 中的 `TESTCONTAINERS_RYUK_DISABLED` env
- 在 failsafe configuration 中加入该 env

### Step 3: VERIFY
- `mvn test` 确认输出不含 ApiTest/IT/E2eTest
- `mvn test` 测试数量与变更前对比不减少
- `mvn verify` 确认 failsafe 跑 ApiTest/IT/E2eTest
- `mvn verify -DskipITs` 跳过集成测试

## 事后检查

| 检查项 | 状态 | 说明 |
|--------|------|------|
| 行为闭环 | ⬜ | |
| 层级闭环 | ⬜ | |
| 测试闭环 | ⬜ | |
| 架构闭环 | ⬜ | |
| 性能闭环 | ⬜ | |
| 文档闭环 | ⬜ | |
| 工作区闭环 | ⬜ | |

## 静态扫描

**扫描命令**：
**报告路径**：
**TopN 处理结论**：
**未解决风险**：

## 未完成项

| 项 | 原因 | 追踪位置 |
|----|------|---------|
| | | |

## 经验沉淀

- [ ] 不需要
- [ ] 已创建经验文档
- [ ] 已更新经验索引
