# TestContainers 1.20.x 在 macOS Docker Desktop 的配置

## 问题

在 macOS Docker Desktop 29.x 环境下运行 TestContainers 1.20.x 时，遇到两类典型故障：

1. **Ryuk 无法禁用**：`~/.testcontainers.properties` 中的 `ryuk.disabled=true` 不生效，Ryuk 启动失败导致所有容器无法启动
2. **多测试类间容器重启**：使用 `@Testcontainers + @Container` 时，每个测试类结束会停止容器，下一个类启动新容器得到新端口，而 `@DynamicPropertySource` 已注入旧端口，导致连接报 500

## 根本原因

### Ryuk 禁用失效

TestContainers 1.20.x 中 `ResourceReaper` 直接读取 **环境变量** `System.getenv("TESTCONTAINERS_RYUK_DISABLED")`，不通过 `TestcontainersConfiguration` 的属性文件读取路径。因此：

- `~/.testcontainers.properties` 的 `ryuk.disabled=true` → **无效**
- 环境变量 `TESTCONTAINERS_RYUK_DISABLED=true` → **有效**

### Docker API 版本不匹配（macOS）

Docker Desktop 29.x 默认 API 版本 ≥ 1.44，而 docker-java 默认协商版本为 1.32。需要在 `~/.docker-java.properties` 中显式指定：

```properties
api.version=1.44
```

## 正确配置方案

### 1. 在 Maven Surefire 插件中注入环境变量（推荐）

在 `releasehub-bootstrap/pom.xml` 中配置，使构建自包含，无需开发者手动设置：

```xml
<plugin>
    <groupId>org.apache.maven.plugins</groupId>
    <artifactId>maven-surefire-plugin</artifactId>
    <configuration>
        <environmentVariables>
            <!-- TestContainers 1.20.x 必须通过环境变量禁用 Ryuk -->
            <TESTCONTAINERS_RYUK_DISABLED>true</TESTCONTAINERS_RYUK_DISABLED>
        </environmentVariables>
    </configuration>
</plugin>
```

### 2. macOS 本地开发环境配置

`~/.docker-java.properties`：
```properties
api.version=1.44
```

`~/.testcontainers.properties`（可选，docker.host 辅助配置）：
```properties
docker.host=unix:///Users/<username>/Library/Containers/com.docker.docker/Data/docker.raw.sock
```

### 3. Singleton 容器模式（防止多测试类间容器重启）

**不要使用** `@Testcontainers + @Container` 注解（每个类结束时停止容器）。

**应使用**静态初始化块（所有测试类共享同一 JVM 生命周期内的容器）：

```java
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("e2e")
abstract class AbstractE2ETest {

    static final PostgreSQLContainer<?> postgres;

    static {
        postgres = new PostgreSQLContainer<>("postgres:16")
                .withDatabaseName("releasehub_e2e")
                .withUsername("e2e")
                .withPassword("e2e");
        postgres.start();
    }

    @DynamicPropertySource
    static void configureDataSource(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
    }
}
```

## 关键对比

| 配置方式 | TestContainers 1.20.x 是否有效 |
|----------|-------------------------------|
| `~/.testcontainers.properties` 的 `ryuk.disabled=true` | ❌ 无效 |
| 环境变量 `TESTCONTAINERS_RYUK_DISABLED=true` | ✅ 有效 |
| Surefire `<environmentVariables>` 注入 | ✅ 有效（推荐，自包含） |
| `@Testcontainers + @Container` 多类 | ❌ 容器重启，端口变化 |
| 静态初始化块 Singleton 模式 | ✅ 容器全程复用 |

## 相关问题

- Ryuk 的作用：在 JVM 退出时自动清理残留容器；禁用后容器由 JVM shutdown hook 清理，测试环境可接受
- `docker.raw.sock` vs `/var/run/docker.sock`：前者是 macOS Docker Desktop 的真实 socket，后者是符号链接；Ryuk bind mount 需要符号链接路径
