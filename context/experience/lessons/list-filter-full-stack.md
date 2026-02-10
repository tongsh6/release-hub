# 列表筛选功能全栈实现模式

> 从「发布窗口状态筛选」功能提炼的可复用模式。

## 背景

列表页添加筛选条件是常见需求，涉及前后端多层修改。本文档记录一个完整的实现流程，供后续类似功能参考。

## 实现清单

### 后端（5 层修改）

| 层 | 文件 | 修改内容 |
|----|------|----------|
| **Port 接口** | `XxxPort.java` | 添加筛选参数到 `findPaged()` 方法签名 |
| **Application** | `XxxAppService.java` | 透传筛选参数 |
| **Infrastructure** | `XxxJpaRepository.java` | 添加 JPA 查询方法（命名约定或 `@Query`）|
| **Infrastructure** | `XxxPersistenceAdapter.java` | 实现 Port 接口，调用 Repository |
| **Interfaces** | `XxxController.java` | 添加 `@RequestParam` 接收筛选参数 |

### 前端（3 层修改）

| 层 | 文件 | 修改内容 |
|----|------|----------|
| **API** | `xxx.ts` | 添加筛选参数到请求函数 |
| **页面** | `XxxList.vue` | 添加筛选 UI 组件，绑定状态 |
| **i18n** | `zh-CN.json` / `en-US.json` | 添加筛选相关文案 |

## 代码示例

### 1. Port 接口

```java
// 修改前
PageResult<ReleaseWindow> findPaged(String name, int page, int size);

// 修改后：添加 status 参数
PageResult<ReleaseWindow> findPaged(String name, ReleaseWindowStatus status, int page, int size);
```

### 2. JPA Repository（命名约定查询）

```java
// 精确匹配 status
Page<ReleaseWindowEntity> findByStatus(ReleaseWindowStatus status, Pageable pageable);

// 组合条件：name 模糊 + status 精确
Page<ReleaseWindowEntity> findByNameContainingAndStatus(String name, ReleaseWindowStatus status, Pageable pageable);
```

### 3. Adapter 实现（条件组合）

```java
@Override
public PageResult<ReleaseWindow> findPaged(String name, ReleaseWindowStatus status, int page, int size) {
    Pageable pageable = PageRequest.of(page - 1, size, Sort.by(Sort.Direction.DESC, "createdAt"));
    Page<ReleaseWindowEntity> pageResult;
    
    boolean hasName = name != null && !name.isBlank();
    boolean hasStatus = status != null;
    
    if (hasName && hasStatus) {
        pageResult = repository.findByNameContainingAndStatus(name, status, pageable);
    } else if (hasName) {
        pageResult = repository.findByNameContaining(name, pageable);
    } else if (hasStatus) {
        pageResult = repository.findByStatus(status, pageable);
    } else {
        pageResult = repository.findAll(pageable);
    }
    
    return new PageResult<>(
        pageResult.getContent().stream().map(this::toDomain).toList(),
        pageResult.getTotalElements()
    );
}
```

### 4. Controller（可选参数）

```java
@GetMapping
public PageResult<ReleaseWindowDTO> list(
    @RequestParam(required = false) String name,
    @RequestParam(required = false) ReleaseWindowStatus status,  // 枚举自动转换
    @RequestParam(defaultValue = "1") int page,
    @RequestParam(defaultValue = "10") int size
) {
    return appService.listPaged(name, status, page, size)
                     .map(ReleaseWindowDTO::fromDomain);
}
```

### 5. 前端 API

```typescript
export function getReleaseWindowList(params: {
  name?: string
  status?: ReleaseWindowStatus  // 使用 pnpm gen:api 生成的类型
  page?: number
  size?: number
}) {
  return request.get<PageResult<ReleaseWindowDTO>>('/release-windows', { params })
}
```

### 6. Vue 组件

```vue
<template>
  <el-select v-model="filterStatus" clearable :placeholder="t('releaseWindow.filterStatus')">
    <el-option
      v-for="status in statusOptions"
      :key="status"
      :label="t(`releaseWindow.status.${status}`)"
      :value="status"
    />
  </el-select>
</template>

<script setup lang="ts">
const filterStatus = ref<ReleaseWindowStatus | undefined>()

// 监听筛选变化，重新加载
watch(filterStatus, () => {
  currentPage.value = 1  // 重置页码
  loadData()
})

async function loadData() {
  const res = await getReleaseWindowList({
    name: filterName.value || undefined,
    status: filterStatus.value,
    page: currentPage.value,
    size: pageSize.value,
  })
  // ...
}
</script>
```

## 关键经验

### 1. 枚举参数处理

- **后端**：Spring 自动将字符串转换为枚举，无需额外配置
- **前端**：使用 `pnpm gen:api` 生成的枚举类型，保持前后端一致

### 2. 可选参数设计

- 筛选参数应全部可选（`required = false`）
- null 表示「不筛选」，前端用 `undefined` 或不传
- 清空筛选后应重置页码到第 1 页

### 3. JPA 查询方法命名

| 场景 | 方法名 |
|------|--------|
| 精确匹配 | `findByStatus(status)` |
| 模糊匹配 | `findByNameContaining(name)` |
| 组合条件 | `findByNameContainingAndStatus(name, status)` |
| 忽略大小写 | `findByNameContainingIgnoreCase(name)` |

### 4. 条件组合策略

当有多个可选筛选条件时，有两种策略：

**策略 A：多方法组合**（本例采用）
```java
if (hasName && hasStatus) { ... }
else if (hasName) { ... }
else if (hasStatus) { ... }
else { ... }
```

**策略 B：Specification 动态查询**（条件更多时推荐）
```java
Specification<Entity> spec = Specification.where(null);
if (hasName) spec = spec.and(nameContains(name));
if (hasStatus) spec = spec.and(statusEquals(status));
return repository.findAll(spec, pageable);
```

### 5. 前端状态管理

- 筛选状态用 `ref` 管理，不需要 Pinia
- 使用 `watch` 监听筛选变化，自动触发查询
- 筛选变化时重置页码，避免「第 3 页 + 新筛选 = 空结果」

## 相关经验

- [发布窗口生命周期设计](release-window-lifecycle.md) - 状态枚举定义
- [前端测试分层](frontend-vitest-e2e-separation.md) - 前端测试策略
