## 1. Backend pagination contract
- [x] Update `ApiPageResponse`/`PageMeta` to expose `page/size/total` with 1-based semantics
- [x] Standardize `/paged` controllers to accept 1-based `page` and compute offsets accordingly
- [x] Replace in-memory slicing with repository-level pagination where possible

## 2. Add missing paged endpoints
- [x] Add `/api/v1/branch-rules/paged` with name filtering
- [x] Add `/api/v1/version-policies/paged` with name/strategy filtering

## 3. Run list filtering
- [x] Extend Run query APIs to filter by runType/operator
- [x] Add RunItem-based filters (windowKey/repoId/iterationKey)
- [x] Apply status filter based on derived status

## 4. Frontend pagination alignment
- [x] Update API modules to use `/paged` endpoints and `page.total`
- [x] Remove client-side pagination slicing in list APIs
- [x] Ensure list dialogs (release window attach) use server-side pagination

## 5. Validation
- [x] Update/extend backend tests for paged list endpoints and run filters
- [x] Run `mvn -q -pl releasehub-bootstrap test`
- [x] Run `pnpm lint && pnpm typecheck` in `release-hub-web`
