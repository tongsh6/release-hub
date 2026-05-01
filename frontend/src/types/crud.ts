export type Id = string | number

export type CrudMode = 'create' | 'edit' | 'view'

export interface PageQuery {
  page: number
  pageSize: number
  [key: string]: any
}

export interface PageResult<T> {
  list: T[]
  total: number
}
