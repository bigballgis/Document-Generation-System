import request from './request'

// --- Types ---

export type DataSourceType = 'HTTP_API' | 'DATABASE' | 'INTERNAL_SYSTEM'

export interface DataSourceDTO {
  id: number
  templateId: number
  name: string
  type: DataSourceType
  configJson: string
  cacheEnabled: boolean
  cacheTtl: number | null
  priority: number
  createdAt: string
  updatedAt: string
}

export interface CreateDataSourceRequest {
  name: string
  type: DataSourceType
  configJson: string
  cacheEnabled?: boolean
  cacheTtl?: number
  priority?: number
}

export interface UpdateDataSourceRequest extends CreateDataSourceRequest {}

/** Parsed config for HTTP API data source */
export interface HttpApiConfig {
  url: string
  method: string
  headers: Record<string, string>
  params: Record<string, string>
  authType: 'NONE' | 'API_KEY' | 'OAUTH' | 'BASIC'
  authConfig: Record<string, string>
  timeout: number
  retryEnabled: boolean
  retryCount: number
  retryInterval: number
  retryBackoff: boolean
  responseTransformRules?: TransformRule[]
}

/** Parsed config for Database data source */
export interface DatabaseConfig {
  dbType: 'POSTGRESQL' | 'MYSQL' | 'SQLSERVER' | 'ORACLE'
  host: string
  port: number
  dbName: string
  username: string
  password: string
  sqlQuery: string
}

/** Parsed config for Internal System data source */
export interface InternalSystemConfig {
  serviceName: string
  serviceUrl: string
  authType: 'NONE' | 'API_KEY' | 'OAUTH' | 'BASIC'
  authConfig: Record<string, string>
  timeout: number
  retryEnabled: boolean
  retryCount: number
  retryInterval: number
}

export interface TransformRule {
  type: 'JSONPATH' | 'XPATH' | 'FLATTEN' | 'GROUP_BY' | 'SORT_BY'
  expression: string
  targetField?: string
}

export interface TestConnectionResult {
  success: boolean
  message: string
  responseTime?: number
}

export interface PipelineStage {
  id: string
  name: string
  type: 'FETCH' | 'TRANSFORM' | 'COMPUTE' | 'VALIDATE'
  dataSourceId?: number
  dependsOn: string[]
}

// --- API Functions ---

export function getDataSources(templateId: number) {
  return request.get<any, DataSourceDTO[]>(`/templates/${templateId}/data-sources`)
}

export function getDataSource(id: number) {
  return request.get<any, DataSourceDTO>(`/data-sources/${id}`)
}

export function createDataSource(templateId: number, data: CreateDataSourceRequest) {
  return request.post<any, DataSourceDTO>(`/templates/${templateId}/data-sources`, data)
}

export function updateDataSource(id: number, data: UpdateDataSourceRequest) {
  return request.put<any, DataSourceDTO>(`/data-sources/${id}`, data)
}

export function deleteDataSource(id: number) {
  return request.delete(`/data-sources/${id}`)
}

export function testConnection(id: number) {
  return request.post<any, TestConnectionResult>(`/data-sources/${id}/test`)
}
