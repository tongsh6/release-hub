// src/types/dto.ts

export interface ApiResponse<T> {
  code: string;
  message: string;
  data: T;
  traceId?: string;
}

export type YesNo = 'YES' | 'NO';
export type BuildTool = 'MAVEN' | 'GRADLE';
export type Status = 'ENABLED' | 'DISABLED';

export type ReleaseWindowStatus =
  | 'DRAFT'
  | 'PLANNED'
  | 'ACTIVE'
  | 'FROZEN'
  | 'PUBLISHED'
  | 'CLOSED';

export type RunStatus = 'PLANNED' | 'RUNNING' | 'DONE' | 'PARTIAL_FAILED' | 'FAILED' | 'NEEDS_REVIEW';
export type RunType = 'WINDOW_ORCHESTRATION' | 'WINDOW_CLEANUP' | 'ATTACH_ITERATION' | 'VERSION_UPDATE';
export type RunItemResult =
  | 'CREATED' | 'ALREADY_EXISTS' | 'MR_REUSED'
  | 'MERGED' | 'ALREADY_MERGED' | 'MERGE_BLOCKED' | 'UNKNOWN' | 'FAILED'
  | 'SKIPPED_DUE_TO_BLOCK' | 'SKIPPED' | 'SUCCESS'
  | 'VERSION_UPDATE_SUCCESS' | 'VERSION_UPDATE_FAILED'
  | 'BRANCH_EXISTS' | 'BRANCH_CREATED' | 'TAG_CREATED'
  | 'CI_TRIGGERED' | 'CI_NOT_CONFIGURED' | 'ITERATION_CLOSED';

export interface BranchRuleScopeDTO {
  level: 'GLOBAL' | 'PROJECT' | 'SUB_PROJECT';
  projectId?: string;
  subProjectId?: string;
}
export type BranchRuleScopeReq = BranchRuleScopeDTO

export interface PolicyScopeDTO {
  level: 'GLOBAL' | 'PROJECT' | 'SUB_PROJECT' | 'REPO';
  projectId?: string;
  subProjectId?: string;
  repoUrl?: string;
}
export type PolicyScopeReq = PolicyScopeDTO

export interface BaseEntity {
  id: string
  createdAt?: string
  updatedAt?: string
  createdBy?: string
  updatedBy?: string
}

export interface ProjectDTO extends BaseEntity {
  name: string
  code: string
}

export interface ReleaseWindowDTO extends BaseEntity {
  name: string
  status: string
}
