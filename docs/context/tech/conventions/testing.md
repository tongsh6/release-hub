# TDD 开发原则与测试规范

> 适用范围：`**/*Test.java`, `**/*Spec.ts`, `**/*.test.ts`, `**/*.spec.ts`

**重要：所有 AI 响应必须使用中文。**

## 🎯 TDD 核心原则

### Red-Green-Refactor 循环

```
┌─────────────────────────────────────────────────────────────┐
│  1. RED    →  2. GREEN  →  3. REFACTOR  →  (重复)          │
│  写失败测试    通过且对齐蓝图   重构优化                      │
└─────────────────────────────────────────────────────────────┘
```

**严格遵循**：
1. **先写测试**：在编写任何业务代码之前，先写一个会失败的测试
2. **通过且对齐蓝图**：只写刚好让当前测试通过的代码，但必须符合完整目标蓝图，不得缩小最终目标
3. **持续重构**：测试通过后，优化代码结构，保持测试绿色

### TDD 开发流程（必须遵循）

```
需求分析 → 设计测试用例 → 编写测试（失败）→ 实现代码 → 测试通过 → 重构 → 下一个用例
```

## 📋 TDD 实践清单

### 开发新功能时

```markdown
□ 1. 分析需求，识别验收标准
□ 2. 设计测试用例（正常路径 + 边界条件 + 异常场景）
□ 3. 从最简单的测试开始
□ 4. 运行测试，确认失败（RED）
□ 5. 编写刚好让测试通过且不偏离完整蓝图的实现
□ 6. 运行测试，确认通过（GREEN）
□ 7. 重构代码（REFACTOR）
□ 8. 重复 3-7 直到完成所有用例
```

### 修复 Bug 时

```markdown
□ 1. 先写一个能重现 Bug 的测试
□ 2. 确认测试失败
□ 3. 修复 Bug
□ 4. 确认测试通过
□ 5. 确保没有破坏其他测试
```

## 后端测试分层

### 测试金字塔

```
        ┌───────────┐
        │  E2E 测试  │  ← 少量，验证关键流程
        ├───────────┤
        │ 集成测试   │  ← 适量，验证组件协作
        ├───────────┤
        │ 单元测试   │  ← 大量，验证业务逻辑
        └───────────┘
```

### 各层测试规范

| 层级 | 测试类型 | 注解 | Spring 上下文 | 速度 |
|------|----------|------|---------------|------|
| Domain | 纯单元测试 | JUnit 5 only | ❌ 无 | 极快 |
| Application | 单元/集成 | `@SpringBootTest` | ✅ 完整 | 中等 |
| Infrastructure | 单元测试 | JUnit 5 + `@TempDir` | ❌ 无 | 快 |
| API | 集成测试 | `@SpringBootTest` + MockMvc | ✅ 完整 | 较慢 |
| Architecture | ArchUnit | JUnit 5 | ❌ 无 | 快 |

## Domain 层 TDD 模式

### 1. 编写领域测试（先写）

```java
class ReleaseWindowTest {
    
    private final Instant now = Instant.now();
    
    // ✅ 正常场景测试
    @Test
    void should_create_draft_successfully() {
        // Given - 准备条件
        String key = "WK-01";
        String name = "2024-W01";
        
        // When - 执行操作
        ReleaseWindow rw = ReleaseWindow.createDraft(key, name, now);
        
        // Then - 验证结果
        assertThat(rw.getStatus()).isEqualTo(ReleaseWindowStatus.DRAFT);
        assertThat(rw.getKey()).isEqualTo(key);
        assertThat(rw.getName()).isEqualTo(name);
        assertThat(rw.isFrozen()).isFalse();
    }
    
    // ✅ 异常场景测试
    @Test
    void should_fail_to_configure_when_frozen() {
        // Given
        ReleaseWindow rw = ReleaseWindow.createDraft("WK-01", "2024-W01", now);
        rw.freeze(now);
        
        // When & Then
        assertThatThrownBy(() -> rw.configureWindow(now.plusDays(1), now.plusDays(3), now))
            .isInstanceOf(BizException.class)
            .hasMessageContaining("Cannot configure frozen");
    }
    
    // ✅ 边界条件测试
    @Test
    void should_be_idempotent_when_freeze_twice() {
        ReleaseWindow rw = ReleaseWindow.createDraft("WK-01", "2024-W01", now);
        rw.freeze(now);
        Instant firstFreezeTime = rw.getUpdatedAt();
        
        rw.freeze(now.plusSeconds(10)); // 第二次冻结
        
        assertThat(rw.getUpdatedAt()).isEqualTo(firstFreezeTime); // 幂等，无变化
    }
}
```

### 2. 实现领域代码（后写）

```java
@Getter
public class ReleaseWindow extends BaseEntity<ReleaseWindowId> {
    
    // 工厂方法 - 只在测试通过后重构
    public static ReleaseWindow createDraft(String key, String name, Instant now) {
        validateKey(key);
        validateName(name);
        return new ReleaseWindow(ReleaseWindowId.newId(), key, name, DRAFT, now, now);
    }
    
    public void freeze(Instant now) {
        if (this.frozen) return; // 幂等守卫
        this.frozen = true;
        touch(now);
    }
    
    public void configureWindow(Instant start, Instant end, Instant now) {
        if (this.frozen) {
            throw new BizException("WINDOW_FROZEN", "Cannot configure frozen ReleaseWindow");
        }
        // ... 配置逻辑
    }
}
```

## Application 层 TDD 模式

### 使用内存实现的纯单元测试

```java
class GroupAppServiceTest {
    
    private InMemoryGroupPort port;
    private GroupAppService service;
    
    @BeforeEach
    void setUp() {
        port = new InMemoryGroupPort();
        service = new GroupAppService(port);
    }
    
    @Test
    void should_return_top_level_groups() {
        // Given
        Instant now = Instant.now();
        Group a = Group.create("A", "Group A", null, now);
        Group b = Group.create("B", "Group B", "A", now);
        port.save(a);
        port.save(b);
        
        // When
        List<GroupDTO> topLevel = service.topLevel();
        
        // Then
        assertThat(topLevel).hasSize(1);
        assertThat(topLevel.get(0).getCode()).isEqualTo("A");
    }
    
    // 内存实现 Port
    static class InMemoryGroupPort implements GroupPort {
        private final Map<String, Group> store = new HashMap<>();
        
        @Override
        public void save(Group group) {
            store.put(group.getId().value(), group);
        }
        
        @Override
        public List<Group> findTopLevel() {
            return store.values().stream()
                .filter(g -> g.getParentCode() == null)
                .toList();
        }
        // ... 其他方法
    }
}
```

## Infrastructure 层 TDD 模式

### 使用临时目录的文件操作测试

```java
class MavenVersionUpdaterTest {
    
    @TempDir
    Path tempDir;
    
    private MavenVersionUpdater updater;
    
    @BeforeEach
    void setUp() {
        updater = new MavenVersionUpdater();
    }
    
    @Test
    void should_update_version_successfully() throws IOException {
        // Given - 准备测试文件
        String pomContent = """
            <?xml version="1.0" encoding="UTF-8"?>
            <project>
                <version>1.0.0</version>
            </project>
            """;
        Path pomFile = tempDir.resolve("pom.xml");
        Files.writeString(pomFile, pomContent);
        
        // When
        VersionUpdateResult result = updater.update(
            VersionUpdateRequest.forMaven(RepoId.newId(), tempDir.toString(), "2.0.0", pomFile.toString())
        );
        
        // Then
        assertThat(result.success()).isTrue();
        assertThat(result.oldVersion()).isEqualTo("1.0.0");
        assertThat(result.newVersion()).isEqualTo("2.0.0");
        assertThat(Files.readString(pomFile)).contains("<version>2.0.0</version>");
    }
    
    @Test
    void should_fail_when_pom_not_found() {
        // When
        VersionUpdateResult result = updater.update(
            VersionUpdateRequest.forMaven(RepoId.newId(), tempDir.toString(), "2.0.0", "/nonexistent.xml")
        );
        
        // Then
        assertThat(result.success()).isFalse();
        assertThat(result.errorMessage()).contains("不存在");
    }
}
```

## API 层 TDD 模式

### 完整的集成测试

```java
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class VersionUpdateApiTest {
    
    private static String token;
    private static String windowId;
    
    @Autowired
    private MockMvc mockMvc;
    
    @Autowired
    private ObjectMapper objectMapper;
    
    @Test
    @Order(1)
    void should_login_successfully() throws Exception {
        // Given
        LoginRequest request = new LoginRequest("admin", "admin");
        
        // When & Then
        MvcResult result = mockMvc.perform(post("/api/v1/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.token").exists())
            .andReturn();
        
        token = extractToken(result);
    }
    
    @Test
    @Order(2)
    void should_execute_version_update() throws Exception {
        // Given
        VersionUpdateRequest request = new VersionUpdateRequest();
        request.setRepoId(repoId);
        request.setTargetVersion("2.0.0");
        
        // When & Then
        mockMvc.perform(post("/api/v1/release-windows/" + windowId + "/execute/version-update")
                .header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.status").value("COMPLETED"));
    }
    
    @Test
    @Order(3)
    void should_return_404_for_nonexistent_repo() throws Exception {
        // Given
        VersionUpdateRequest request = new VersionUpdateRequest();
        request.setRepoId("non-existent-id");
        
        // When & Then
        mockMvc.perform(post("/api/v1/release-windows/" + windowId + "/execute/version-update")
                .header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
            .andExpect(status().isNotFound())
            .andExpect(jsonPath("$.code").value("REPO_NOT_FOUND"));
    }
}
```

## 前端测试

### 测试框架
- Vitest 用于单元测试
- Vue Test Utils 用于组件测试

### 组件测试模式

```typescript
import { describe, it, expect, vi } from 'vitest'
import { mount } from '@vue/test-utils'
import { createPinia, setActivePinia } from 'pinia'
import VersionUpdateDialog from './VersionUpdateDialog.vue'

describe('VersionUpdateDialog', () => {
  beforeEach(() => {
    setActivePinia(createPinia())
  })

  it('应该在提交时验证必填字段', async () => {
    const wrapper = mount(VersionUpdateDialog, {
      props: { visible: true, windowId: '123' }
    })
    
    await wrapper.find('form').trigger('submit')
    
    expect(wrapper.text()).toContain('请选择仓库')
  })

  it('应该在成功后触发 close 事件', async () => {
    const wrapper = mount(VersionUpdateDialog, {
      props: { visible: true, windowId: '123' }
    })
    
    // 模拟填写表单并提交
    await wrapper.find('[data-test="repo-select"]').setValue('repo-1')
    await wrapper.find('[data-test="version-input"]').setValue('2.0.0')
    await wrapper.find('form').trigger('submit')
    
    expect(wrapper.emitted('close')).toBeTruthy()
  })
})
```

### Store 测试模式

```typescript
import { describe, it, expect, beforeEach, vi } from 'vitest'
import { setActivePinia, createPinia } from 'pinia'
import { useReleaseWindowStore } from './releaseWindow'

// Mock API
vi.mock('@/api', () => ({
  releaseWindowApi: {
    list: vi.fn().mockResolvedValue({ data: { items: [], total: 0 } }),
    create: vi.fn().mockResolvedValue({ data: { id: '1', key: 'RW-001' } })
  }
}))

describe('ReleaseWindowStore', () => {
  beforeEach(() => {
    setActivePinia(createPinia())
  })

  it('应该正确创建发布窗口', async () => {
    const store = useReleaseWindowStore()
    
    const result = await store.create({ key: 'RW-001', name: 'Test' })
    
    expect(result.id).toBe('1')
    expect(result.key).toBe('RW-001')
  })
})
```

## 测试数据管理

### Fixture 模式

```java
// 后端 Fixture
public class ReleaseWindowFixtures {
    
    public static ReleaseWindow aDraftWindow() {
        return ReleaseWindow.createDraft("TEST-001", "Test Release", Instant.now());
    }
    
    public static ReleaseWindow aConfiguredWindow() {
        ReleaseWindow rw = aDraftWindow();
        rw.configureWindow(
            Instant.now().plus(1, ChronoUnit.DAYS),
            Instant.now().plus(7, ChronoUnit.DAYS),
            Instant.now()
        );
        return rw;
    }
    
    public static ReleaseWindow aPublishedWindow() {
        ReleaseWindow rw = aConfiguredWindow();
        rw.publish(Instant.now());
        return rw;
    }
}
```

```typescript
// 前端 Fixture
export function createMockReleaseWindow(overrides: Partial<ReleaseWindowDTO> = {}): ReleaseWindowDTO {
  return {
    id: '1',
    key: 'RW-001',
    name: 'Test Release',
    status: 'DRAFT',
    frozen: false,
    createdAt: new Date().toISOString(),
    updatedAt: new Date().toISOString(),
    ...overrides
  }
}
```

## 测试命名约定

### 后端测试命名

```java
// 格式: should_<期望行为>_when_<条件>
@Test void should_create_draft_successfully() {}
@Test void should_fail_to_configure_when_frozen() {}
@Test void should_return_404_when_repo_not_found() {}
@Test void should_be_idempotent_when_freeze_twice() {}
```

### 前端测试命名

```typescript
// 使用中文描述
it('应该成功创建发布窗口', async () => {})
it('当仓库不存在时应该显示错误', async () => {})
it('提交时应该验证必填字段', async () => {})
```

## 运行测试命令

```bash
# 后端
mvn -q clean test                           # 所有测试
mvn -pl releasehub-domain test              # Domain 层测试
mvn -pl releasehub-bootstrap test           # API 集成测试
mvn -pl releasehub-bootstrap test -Dtest=VersionUpdateApiTest  # 单个测试类

# 前端
pnpm test                # 运行所有测试
pnpm test:watch          # 监听模式
pnpm test:coverage       # 带覆盖率
pnpm test -- --grep "VersionUpdateDialog"  # 特定组件
```

## 提交前验证清单

```bash
# 后端 TDD 验证
mvn -q clean test && echo "✅ 后端测试通过"

# 前端 TDD 验证
pnpm lint && pnpm typecheck && pnpm test && echo "✅ 前端验证通过"

# 架构验证
mvn -pl releasehub-bootstrap test -Dtest=ArchitectureRulesTest
```

## ⚠️ TDD 反模式（避免）

### ❌ 不要这样做

```java
// 1. 测试实现细节而非行为
@Test void should_call_repository_save() { ... }  // ❌ 测试内部调用

// 2. 测试太大，覆盖多个行为
@Test void should_create_and_configure_and_freeze() { ... }  // ❌ 一个测试多个行为

// 3. 测试依赖外部服务
@Test void should_send_real_email() { ... }  // ❌ 依赖外部系统

// 4. 没有断言的测试
@Test void should_not_throw() {
    service.doSomething();  // ❌ 没有验证结果
}
```

### ✅ 应该这样做

```java
// 1. 测试行为和结果
@Test void should_return_draft_status_after_creation() { ... }

// 2. 每个测试只验证一个行为
@Test void should_create_draft() { ... }
@Test void should_configure_window() { ... }
@Test void should_freeze_window() { ... }

// 3. 使用 Mock 隔离外部依赖
@Test void should_save_to_repository() {
    when(mockPort.save(any())).thenReturn(savedEntity);
    // ...
}

// 4. 明确的断言
@Test void should_update_version() {
    var result = updater.update(request);
    assertThat(result.success()).isTrue();
    assertThat(result.newVersion()).isEqualTo("2.0.0");
}
```
