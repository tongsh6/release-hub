# Project Context

## Purpose
ReleaseHub is a single source of truth for release cadence across multiple projects and repositories. It manages release windows (time-bound releases with states/freeze), project/repository topology, branch naming rules, version policies, and version update execution/diffs so teams can coordinate multi-repo releases with lower cognitive and execution cost.

## Tech Stack
- Backend: Java 21, Spring Boot 3.2.x, modular Maven monolith (common, domain, application, infrastructure, interfaces, bootstrap), Spring Data JPA + Flyway migrations, Spring Security with JWT, Lombok.
- Database: H2 in-memory (PostgreSQL mode) for local/test; target Postgres/MySQL per `application.yml`.
- API/Docs: springdoc OpenAPI; REST endpoints under `/api/v1`.
- Frontend: Vue 3 + TypeScript + Vite + Element Plus, Pinia, Vue Router, Axios; pnpm for package management; openapi-typescript for typed API client; Vitest for unit tests.
- Tooling/quality: ArchUnit + Maven Enforcer for layer purity, JUnit 5, MockMvc integration tests, eslint/prettier/ts type-checking on the web.

## Project Conventions

### Code Style
- Backend: Domain layer stays framework-free; use Lombok; expose REST controllers in `releasehub-interfaces` returning `ApiResponse` wrappers and versioned routes; validate payloads with `jakarta.validation`; OpenAPI annotations on controllers.
- Frontend: ESLint flat config with TypeScript/Vue presets and Prettier; allows single-word component names and `any` where needed; prefer `<script setup>` SFCs; run `pnpm lint` + `pnpm typecheck` to keep CI clean.

### Architecture Patterns
- DDD-inspired modular monolith: common → domain → application → interfaces/infrastructure → bootstrap. Access rules enforced by ArchUnit and Maven Enforcer (e.g., domain cannot depend on Spring/JPA; infra cannot depend on interfaces; bootstrap only depends on interfaces/infra).
- Persistence via Spring Data JPA adapters and Flyway migrations (`releasehub-infrastructure/src/main/resources/db/migration`).
- Security via Spring Security + JWT with seeded admin user when `releasehub.seed.enabled=true`.
- Frontend organized by feature modules (`src/views`, `src/router/modules`, `src/api/modules`) with generated API types from backend OpenAPI.

### Testing Strategy
- Backend: Run `mvn -q clean test` for domain unit tests, ArchUnit gates, and MockMvc integration tests (e.g., `AuthApiTest`). Manual verification scripts `verify_rw.sh` and `verify_rw_v2.sh` assume the app runs via `mvn -pl releasehub-bootstrap spring-boot:run`.
- Frontend: `pnpm test` (Vitest), `pnpm lint`, `pnpm typecheck`; regenerate API types with `pnpm gen:api` when backend OpenAPI changes.

### Git Workflow
- Not formally documented; default to feature branches with PR reviews before merging to the main line. No automated Git/CI integrations implemented yet (interfaces are planned for later).

## Domain Context
- Key aggregates: ReleaseWindow (start/end, freeze/unfreeze, release guarded by window), Project, CodeRepository, SubProject (planned), BranchRule (branch naming/regex), VersionPolicy (SemVer-first bump rules), VersionUpdater (Maven primary, Gradle properties MVP).
- Goals: visualize and govern release windows, enforce branch/version conventions, and execute version bumps with auditable runs/diffs across multiple repos/projects.

## Important Constraints
- Layer purity enforced by ArchUnit + Maven Enforcer; domain must not depend on Spring/JPA; infrastructure cannot depend on interfaces; bootstrap cannot depend directly on application/domain/common.
- Release window guards: start/end must be non-null with start < end; frozen windows block release; release only allowed inside configured window.
- Flyway manages schema; application.yml uses H2 (PostgreSQL mode) locally.
- API secured with JWT; seed data toggled by `releasehub.seed.enabled`.

## External Dependencies
- Backend libs: Spring Boot (web, validation, data-jpa), Spring Security + JWT, Flyway, H2 for local dev, Lombok.
- Frontend libs: Element Plus UI, Pinia, Vue Router, Vue I18n, Axios, Day.js, openapi-typescript generator.
- No external SaaS integrations yet; frontend targets `VITE_API_BASE_URL` to the backend service.
