# ReleaseHub AI Coding Agent Instructions

## Project Overview
ReleaseHub is a **multi-repository release coordination platform** built with DDD (Domain-Driven Design) principles. It manages Release Windows, version policies, branch rules, and automated version updates across multiple Git repositories.

**Core Problem Solved**: Unify release rhythm management for multi-repo scenarios, reducing cognitive, coordination, and execution costs.

## Architecture: DDD + Modular Monolith

### Backend Module Boundaries (Strict Layering)
```
releasehub-domain/       → Aggregates, entities, value objects (NO external dependencies)
releasehub-application/  → Use case orchestration, transaction boundaries (Port interfaces)
releasehub-infrastructure/ → JPA implementations, adapters, external integrations
releasehub-interfaces/   → REST controllers, DTOs (request/response only)
releasehub-bootstrap/    → Spring Boot entry point, configuration
releasehub-common/       → Shared exceptions, utilities
```

**Critical Rules**:
- Domain layer MUST NOT depend on any other layer (pure business logic)
- Application layer defines Port interfaces (e.g., `ReleaseWindowPort`), Infrastructure implements them
- Use `BaseEntity<ID>` in domain for all aggregates/entities (located at `domain/base/BaseEntity.java`)
- Domain models use factory methods (`createDraft()`) and rehydration (`rehydrate()`) patterns

### Frontend Architecture (Vue 3 + TypeScript)
```
src/views/        → Feature pages (e.g., release-window/, branch-rule/)
src/components/   → Reusable UI components
src/api/modules/  → Backend API clients (auto-generated types from OpenAPI)
src/stores/       → Pinia stores (AVOID for page-level state)
src/router/modules/ → Route definitions per feature
```

**Frontend Conventions**:
- Page-level state: Use `reactive`/`ref`, NOT Pinia stores
- API types: Auto-generated via `pnpm gen:api` from `http://localhost:8080/v3/api-docs`
- Proxy: `/api/*` → `http://localhost:8080` (see `vite.config.ts`)

## Key Domain Concepts

### Ubiquitous Language
- **ReleaseWindow**: Time-bound release with target version, status flow, and scope
- **WindowIteration**: Links ReleaseWindow to Iteration (N:N relationship)
- **Iteration**: Represents a sprint/iteration with associated repos
- **BranchRule**: Template/regex for branch naming validation
- **VersionPolicy**: Defines version bump strategy (SemVer, date-based)
- **Run**: Execution record for version updates (tracks diff, status)

### Status Flows
**ReleaseWindow**: `DRAFT → PLANNED → ACTIVE → FROZEN → PUBLISHED → CLOSED`
- `DRAFT/PLANNED` can transition to `CANCELLED`
- `FROZEN` prevents further configuration changes

## Development Workflows

### Backend Development
```bash
# Build (requires Java 21)
cd release-hub
./mvnw clean install -DskipTests

# Run application
cd releasehub-bootstrap
./mvnw spring-boot:run

# OpenAPI docs: http://localhost:8080/swagger-ui.html
```

**Database Migrations**: Use Flyway scripts in `releasehub-infrastructure/src/main/resources/db/migration/`

### Frontend Development
```bash
cd release-hub-web
pnpm install
pnpm dev  # http://localhost:5173

# Generate API types (backend must be running)
pnpm gen:api
```

### Generate CRUD Scaffolding (Plop)
```bash
cd release-hub-web
pnpm plop

# Creates: List view, Detail/Dialog components, API module, types, router
# MUST manually register router in src/router/routes.ts
```

## Code Patterns & Examples

### Domain Model Pattern
```java
// Domain aggregate with encapsulated business logic
@Getter
public class ReleaseWindow extends BaseEntity<ReleaseWindowId> {
    // Factory method for creation
    public static ReleaseWindow createDraft(String key, String name, Instant now) {
        validateName(name);
        return new ReleaseWindow(ReleaseWindowId.newId(), key, name, DRAFT, now, now, ...);
    }
    
    // Rehydration from persistence
    public static ReleaseWindow rehydrate(ReleaseWindowId id, ...) { }
    
    // Business methods with guard conditions
    public void freeze(Instant now) {
        if (this.frozen) return;
        this.frozen = true;
        touch(now);
    }
}
```

### Application Service Pattern
```java
@Service
@RequiredArgsConstructor
public class ReleaseWindowAppService {
    private final ReleaseWindowPort port; // Port interface, not concrete impl
    private final Clock clock = Clock.systemUTC();
    
    @Transactional
    public ReleaseWindowView create(String key, String name) {
        ReleaseWindow rw = ReleaseWindow.createDraft(key, name, Instant.now(clock));
        port.save(rw);
        return ReleaseWindowView.from(rw);
    }
}
```

### Frontend API Pattern
```typescript
// src/api/modules/branchRule.ts
import { get, post, put } from '@/api/http'

export function pageBranchRules(query: PageQuery): Promise<PageResult<BranchRuleDTO>> {
  return get<PageResult<BranchRuleDTO>>(`${API_BASE}/branch-rules`, { params: toQuery(query) })
}

export function testBranchRule(data: BranchRuleTestReq): Promise<BranchRuleTestResp> {
  return post<BranchRuleTestResp>(`${API_BASE}/branch-rules/test`, data)
}
```

### Frontend Page State Pattern (NO Pinia)
```typescript
<script setup lang="ts">
import { reactive, ref } from 'vue'

// Page-local state, NOT stored in Pinia
const queryForm = reactive<BranchRuleQuery>({
  page: 1,
  size: 10,
  name: ''
})

const loading = ref(false)
const dataList = ref<BranchRuleDTO[]>([])

const fetchData = async () => {
  loading.value = true
  const res = await pageBranchRules(queryForm)
  dataList.value = res.records
  loading.value = false
}
</script>
```

## Testing Strategies
- Domain models: Pure unit tests (no Spring context)
- Application services: Integration tests with `@SpringBootTest`
- API layer: `@WebMvcTest` or full end-to-end tests
- Frontend: Vitest for logic, manual UI validation (E2E TBD)

## OpenSpec Integration
This project uses OpenSpec for spec-driven development:
- Proposals: `openspec/changes/<change-id>/` (proposal.md, tasks.md, design.md)
- Specs: `openspec/specs/<capability>/spec.md`
- **Before coding**: Always check `openspec list` and `openspec list --specs`
- **Creating features**: Run `openspec validate <change-id> --strict` before implementation
- **After deployment**: Archive with `openspec archive <change-id> --yes`

See [@/openspec/AGENTS.md](../openspec/AGENTS.md) for full workflow.

## Critical Files to Reference
- **Domain examples**: `releasehub-domain/src/main/java/io/releasehub/domain/{releasewindow,iteration}/`
- **App service examples**: `releasehub-application/src/main/java/io/releasehub/application/releasewindow/`
- **API examples**: `release-hub-web/src/api/modules/{branchRule,versionPolicy}.ts`
- **Project plan**: `release-hub/release_hub_项目总体规划书.md`

## Dependency Versions (Current)
- Java: 21
- Spring Boot: 3.2.8
- Lombok: 1.18.36
- Vue: 3.x
- Vite: Latest
- Element Plus: Latest

## Known Issues & Gotchas
- **Lombok getters missing**: Ensure annotation processing is enabled in IDE
- **BaseEntity not found**: Must exist at `io.releasehub.domain.base.BaseEntity`
- **API types outdated**: Run `pnpm gen:api` after backend schema changes
- **Router not loading**: Verify module imported in `src/router/routes.ts`
