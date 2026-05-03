# Slice 2: Maven 构建基础设施

- **蓝图归属**：`docs/superpowers/specs/2026-05-03-test-system-restructure-design.md` 第 2 部分
- **日期**：2026-05-03
- **执行者**：AI
- **状态**：待启动

## 事前约束检查

| 约束 | 状态 | 说明 |
|------|------|------|
| 蓝图归属 | ✅ | Maven 构建基础设施：surefire/failsafe/JaCoCo/Pitest + WireMock 依赖 |
| 用户价值 | ✅ | 快慢测试分离、覆盖率量化、突变测试、WireMock stub 依赖就绪 |
| 端到端路径 | ✅ | 父 POM → 子模块 → CI |
| 单一目标 | ✅ | 只做 Maven 插件配置和依赖声明 |
| 可独立验证 | ✅ | `mvn test`/`mvn verify -Pcoverage`/`mvn verify -Ppitest` 均正常 |
| 可回滚 | ✅ | git revert POM 文件 |
| 依赖明确 | ✅ | 依赖 Slice 1（profile 清晰） |
| 风险收敛 | ✅ | POM 变更，不触及业务代码 |

## 涉及文件

| 文件 | 操作 | 层 |
|------|------|-----|
| `backend/pom.xml` | 修改：`<pluginManagement>` 加 surefire/failsafe/JaCoCo/Pitest 配置 + WireMock BOM | Build |
| `backend/releasehub-infrastructure/pom.xml` | 修改：加 WireMock test 依赖 | Build |
| `backend/releasehub-bootstrap/pom.xml` | 修改：TESTCONTAINERS_RYUK 从 surefire 移到 failsafe | Build |

## 关键配置

### surefire（父 POM pluginManagement）
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

### failsafe（父 POM pluginManagement）
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
            <goals><goal>integration-test</goal><goal>verify</goal></goals>
        </execution>
    </executions>
</plugin>
```

### JaCoCo（父 POM profile `coverage`）
```xml
<profile>
    <id>coverage</id>
    <build>
        <plugins>
            <plugin>
                <groupId>org.jacoco</groupId>
                <artifactId>jacoco-maven-plugin</artifactId>
                <executions>
                    <execution><id>prepare-agent</id><goal>prepare-agent</goal></execution>
                    <execution><id>report</id><phase>verify</phase><goal>report</goal></execution>
                    <execution><id>check</id><phase>verify</phase><goal>check</goal>
                        <configuration>
                            <rules>
                                <rule><element>BUNDLE</element>
                                    <limits>
                                        <limit><counter>LINE</counter>
                                            <value>COVEREDRATIO</value>
                                            <minimum>0.50</minimum></limit>
                                    </limits>
                                </rule>
                            </rules>
                        </configuration>
                    </execution>
                </executions>
            </plugin>
        </plugins>
    </build>
</profile>
```

### Pitest（父 POM profile `pitest`）
```xml
<profile>
    <id>pitest</id>
    <build>
        <plugins>
            <plugin>
                <groupId>org.pitest</groupId>
                <artifactId>pitest-maven</artifactId>
                <version>1.15.8</version>
                <configuration>
                    <targetClasses>io.releasehub.*</targetClasses>
                    <targetTests>io.releasehub.*</targetTests>
                    <mutators>STRONGER</mutators>
                    <outputFormats><format>HTML</format></outputFormats>
                </configuration>
                <executions>
                    <execution>
                        <goals><goal>mutationCoverage</goal></goals>
                    </execution>
                </executions>
            </plugin>
        </plugins>
    </build>
</profile>
```

### WireMock 依赖（infrastructure POM）
```xml
<dependency>
    <groupId>org.wiremock</groupId>
    <artifactId>wiremock-standalone</artifactId>
    <version>3.9.1</version>
    <scope>test</scope>
</dependency>
```

## 执行步骤

### Step 1: surefire/failsafe 配置
- 父 POM `<pluginManagement>` 声明两个插件
- bootstrap POM 移除 surefire 的 TESTCONTAINERS env，移到 failsafe

### Step 2: JaCoCo
- 父 POM 加 `coverage` profile（prepare-agent + report + check）
- 初始阈值：LINE 50%

### Step 3: Pitest
- 父 POM 加 `pitest` profile（targetClasses = `io.releasehub.*`）

### Step 4: WireMock 依赖
- infrastructure POM 加 wiremock-standalone test scope

### Step 5: VERIFY
- `mvn test` 只跑 surefire，< 30s
- `mvn verify` 额外跑 failsafe
- `mvn verify -Pcoverage` 生成 JaCoCo 报告
- `mvn verify -Ppitest` 生成 Pitest 报告

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
