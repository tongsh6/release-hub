import { apiGet, apiPost, apiPut, apiDel, type ApiPath } from './http'
import type { paths } from './schema'
import type { AxiosRequestConfig } from 'axios'

// Extract paths and methods
type Path = keyof paths
type Method = 'get' | 'post' | 'put' | 'delete' | 'patch'

// Helper to extract response type for 200 OK
type ResponseType<P extends Path, M extends Method> = 
  M extends keyof paths[P] 
    ? paths[P][M] extends { responses: { 200: { content: { "application/json": infer R } } } } 
      ? R 
      : never
    : never

// Helper to extract request body type
type RequestBodyType<P extends Path, M extends Method> = 
  M extends keyof paths[P]
    ? paths[P][M] extends { requestBody: { content: { "application/json": infer B } } }
      ? B
      : undefined
    : undefined

// Helper to extract query params
type QueryParamsType<P extends Path, M extends Method> = 
  M extends keyof paths[P]
    ? paths[P][M] extends { parameters: { query?: infer Q } }
      ? Q
      : undefined
    : undefined

function toApiPath<P extends Path>(url: P): ApiPath {
  return (String(url).replace(/^\/api\//, '/')) as ApiPath
}

export const apiClient = {
  get: <P extends Path>(
    url: P, 
    params?: QueryParamsType<P, 'get'>,
    config?: AxiosRequestConfig
  ): Promise<ResponseType<P, 'get'>> => {
    return apiGet(toApiPath(url), { ...config, params })
  },

  post: <P extends Path>(
    url: P, 
    data: RequestBodyType<P, 'post'>,
    config?: AxiosRequestConfig
  ): Promise<ResponseType<P, 'post'>> => {
    return apiPost(toApiPath(url), data, config)
  },

  put: <P extends Path>(
    url: P, 
    data: RequestBodyType<P, 'put'>,
    config?: AxiosRequestConfig
  ): Promise<ResponseType<P, 'put'>> => {
    return apiPut(toApiPath(url), data, config)
  },

  delete: <P extends Path>(
    url: P, 
    config?: AxiosRequestConfig
  ): Promise<ResponseType<P, 'delete'>> => {
    return apiDel(toApiPath(url), config)
  }
}
