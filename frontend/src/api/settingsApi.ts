import { http } from '@/api/http'

export interface GitLabSettings {
  baseUrl: string
  token: string
}

export interface NamingSettings {
  featureTemplate: string
  releaseTemplate: string
}

export interface BlockingSettings {
  defaultPolicy: string
}

// Helper to extract data from ApiResponse
// Assuming ApiResponse structure: { code, message, data: T }
const extractData = <T>(res: any): T => res.data.data

export const settingsApi = {
  getGitLab: () => http.get('/v1/settings/gitlab').then(extractData<GitLabSettings>),
  saveGitLab: (data: GitLabSettings) => http.post('/v1/settings/gitlab', data),
  testGitLab: () => http.get('/v1/settings/gitlab/test'),

  getNaming: () => http.get('/v1/settings/naming').then(extractData<NamingSettings>),
  saveNaming: (data: NamingSettings) => http.post('/v1/settings/naming', data),

  getBlocking: () => http.get('/v1/settings/blocking').then(extractData<BlockingSettings>),
  saveBlocking: (data: BlockingSettings) => http.post('/v1/settings/blocking', data),
}
