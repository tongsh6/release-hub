import type { PageQuery } from '@/types/crud'

export interface ReleaseWindowQuery extends PageQuery {
  name?: string
  status?: string
}

export interface ReleaseWindowDTO {
  id: string
  name: string
  status: 'active' | 'frozen'
  createdAt: string
  description?: string
}

export interface ReleaseWindowForm {
  id?: string
  name: string
  status: 'active' | 'frozen'
  description?: string
}
