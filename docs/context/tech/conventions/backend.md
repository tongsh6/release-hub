# ReleaseHub 后端开发规范

> 适用范围：`release-hub/**/*.java`, `release-hub/**/pom.xml`

**重要：所有 AI 响应必须使用中文。**

## 架构：DDD + 模块化单体

### 模块边界（严格分层）
```
releasehub-domain/        → 聚合、实体、值对象（禁止外部依赖）
releasehub-application/   → 用例编排、事务边界（Port 接口）
releasehub-infrastructure/→ JPA 实现、适配器、外部集成
releasehub-interfaces/    → REST 控制器、DTO（仅请求/响应）
releasehub-bootstrap/     → Spring Boot 入口、配置
releasehub-common/        → 共享异常、工具类
```

### 关键依赖规则
- **Domain 层禁止**依赖 Spring、JPA、Hibernate 或任何框架
- **Application 层**定义 Port 接口（如 `ReleaseWindowPort`），Infrastructure 实现它们
- **Infrastructure 禁止**依赖 interfaces 层
- **Bootstrap 禁止**直接依赖 domain/application/common
- ArchUnit + Maven Enforcer 验证这些约束

## 领域模型模式

### 实体创建模式
```java
// 使用工厂方法创建，rehydrate() 用于持久化加载
@Getter
public class ReleaseWindow extends BaseEntity<ReleaseWindowId> {
    // 工厂方法 - 验证并创建新实例
    public static ReleaseWindow createDraft(String key, String name, Instant now) {
        validateKey(key);
        validateName(name);
        return new ReleaseWindow(ReleaseWindowId.newId(), key, name, DRAFT, now, now);
    }
    
    // 重建方法 - 从持久化重构（不做验证）
    public static ReleaseWindow rehydrate(ReleaseWindowId id, String key, String name, 
                                          Status status, Instant createdAt, Instant updatedAt) {
        return new ReleaseWindow(id, key, name, status, createdAt, updatedAt);
    }
    
    // 带守卫条件的业务方法
    public void freeze(Instant now) {
        if (this.frozen) return;
        this.frozen = true;
        touch(now);
    }
}
```

### 基础实体使用
所有聚合/实体**必须**继承 `io.releasehub.domain.base.BaseEntity<ID>`。

### 状态流转（发布窗口）
```
DRAFT（草稿）→ PLANNED（已规划）→ ACTIVE（活跃）→ FROZEN（冻结）→ PUBLISHED（已发布）→ CLOSED（已关闭）
              ↓                ↓
              CANCELLED（已取消，仅从 DRAFT/PLANNED 可转换）
```

## 应用服务模式

```java
@Service
@RequiredArgsConstructor
public class ReleaseWindowAppService {
    private final ReleaseWindowPort port; // Port 接口，不是具体实现
    private final Clock clock = Clock.systemUTC();
    
    @Transactional
    public ReleaseWindowView create(String key, String name) {
        ReleaseWindow rw = ReleaseWindow.createDraft(key, name, Instant.now(clock));
        port.save(rw);
        return ReleaseWindowView.from(rw);
    }
}
```

## REST API 约定

### 控制器模式
```java
@RestController
@RequestMapping("/api/v1/release-windows")
@RequiredArgsConstructor
@Tag(name = "发布窗口", description = "发布窗口管理 API")
public class ReleaseWindowController {
    
    @PostMapping
    @Operation(summary = "创建新的发布窗口")
    public ApiResponse<ReleaseWindowDTO> create(@Valid @RequestBody CreateRequest req) {
        return ApiResponse.ok(service.create(req));
    }
    
    @GetMapping
    public ApiPageResponse<ReleaseWindowDTO> page(@Valid PageQuery query) {
        return ApiPageResponse.of(service.page(query));
    }
}
```

### 路径约定
- 所有 API：`/api/v1/**`
- 资源使用复数名词：`/release-windows`、`/branch-rules`
- 使用 `jakarta.validation` 进行请求验证
- 使用 `@Tag` 和 `@Operation` 编写 Swagger 文档

## 数据库与持久化

### Flyway 迁移
- 位置：`releasehub-infrastructure/src/main/resources/db/migration/`
- 命名：`V{版本号}__{描述}.sql`
- 本地开发使用 `ddl-auto=update`，测试/生产使用 Flyway

### JPA 仓储模式
```java
// Infrastructure 层实现 Application 层的 Port
@Repository
public class JpaReleaseWindowAdapter implements ReleaseWindowPort {
    private final ReleaseWindowJpaRepository jpaRepo;
    
    @Override
    public Optional<ReleaseWindow> findById(ReleaseWindowId id) {
        return jpaRepo.findById(id.getValue()).map(this::toDomain);
    }
}
```

## 技术栈版本
- Java: 21
- Spring Boot: 3.4.1
- Spring Framework: 6.2.x
- Lombok: 1.18.36
- PostgreSQL Driver: 42.7.7
- Flyway: 10.20.0

## 测试策略（TDD 优先）

**必须遵循 TDD 流程**：先写测试 → 刚好满足当前测试且符合完整规划的实现 → 重构

详细 TDD 规则参见 `testing.mdc`

### 开发新功能流程
```
1. 编写失败的测试（RED）
2. 编写刚好让测试通过且不偏离完整蓝图的实现（GREEN）
3. 重构优化（REFACTOR）
4. 重复直到功能完成
```

### 各层测试策略
| 层级 | 测试类型 | 框架 | Spring 上下文 |
|------|----------|------|---------------|
| Domain | 纯单元测试 | JUnit 5 | ❌ |
| Application | 单元/集成 | JUnit 5 + Mock | ✅/❌ |
| Infrastructure | 单元测试 | JUnit 5 + @TempDir | ❌ |
| API | 集成测试 | MockMvc + @SpringBootTest | ✅ |

### 运行测试
```bash
mvn -q clean test                        # 全量测试
mvn -pl releasehub-domain test           # Domain 层测试
mvn -pl releasehub-bootstrap test        # API 集成测试
```

## 常见问题
- **Lombok getter 缺失**：在 IDE 中启用注解处理
- **找不到 BaseEntity**：必须存在于 `io.releasehub.domain.base.BaseEntity`
- **Port 未自动注入**：检查 Infrastructure 适配器是否添加了 `@Repository` 注解
