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

export type RunStatus = 'RUNNING' | 'SUCCEEDED' | 'FAILED' | 'CANCELLED';

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
