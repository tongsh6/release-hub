# Implementation Tasks: Repository Management

- [x] **Phase 1: Domain & Application Layer Refinement**
  - [x] Verify `CodeRepository` entity validations against Spec FR-005 (Uniqueness check in service).
  - [x] Implement `GitLabPort` interface method signatures clearly.
  - [x] Implement `CodeRepositoryAppService.syncRepository(RepoId id)` method.

- [x] **Phase 2: Infrastructure Layer (GitLab Integration)**
  - [x] Implement `GitLabAdapter` to fetch real data from GitLab API (using Mock or Real Token).
  - [x] Ensure `CodeRepositoryJpaAdapter` handles uniqueness constraints properly.

- [x] **Phase 3: API & Frontend Integration**
  - [x] Add `POST /api/v1/repositories/{id}/sync` endpoint.
  - [x] Update `RepositoryDetail.vue` to display sync status and add a "Sync" button.
  - [x] Update `RepositoryList.vue` to show health indicators (compliant/non-compliant branches).

- [x] **Phase 4: Verification**
  - [x] Write Integration Test for `CodeRepositoryAppService` — `RepositoryE2eTest` (Phase 7, 10 用例，覆盖 CRUD + keyword + 非叶子节点校验)
  - [x] Manual test of adding a repo and syncing data — E2E 测试通过 (62/62, 含 repository.test.ts 12 用例)
