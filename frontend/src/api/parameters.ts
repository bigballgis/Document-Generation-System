import request from './request'
import type {
  ParameterDTO,
  CreateParameterRequest,
  UpdateParameterRequest,
  ScanResultDTO,
  ParameterSchemaDTO,
  BatchUpdateItem,
  AggregationSchemaDTO,
} from '@/types/parameter'


export function getParameters(templateId: number) {
  return request.get<any, ParameterDTO[]>(`/templates/${templateId}/parameters`)
}

export function getParametersFlat(templateId: number) {
  return request.get<any, ParameterDTO[]>(`/templates/${templateId}/parameters`, {
    params: { flat: true },
  })
}

export function createParameter(templateId: number, data: CreateParameterRequest) {
  return request.post<any, ParameterDTO>(`/templates/${templateId}/parameters`, data)
}

export function updateParameter(id: number, data: UpdateParameterRequest) {
  return request.put<any, ParameterDTO>(`/parameters/${id}`, data)
}

export function deleteParameter(id: number) {
  return request.delete(`/parameters/${id}`)
}


export function scanPlaceholders(templateId: number) {
  return request.post<any, ScanResultDTO>(`/templates/${templateId}/parameters/scan`)
}

export function autoCreateParameters(templateId: number) {
  return request.post<any, ParameterDTO[]>(`/templates/${templateId}/parameters/auto-create`)
}


export function getParameterSchema(templateId: number) {
  return request.get<any, ParameterSchemaDTO>(`/templates/${templateId}/parameter-schema`)
}


export function batchDeleteParameters(templateId: number, ids: number[]) {
  return request.post(`/templates/${templateId}/parameters/batch-delete`, { ids })
}

export function batchUpdateParameters(templateId: number, items: BatchUpdateItem[]) {
  return request.post<any, ParameterDTO[]>(
    `/templates/${templateId}/parameters/batch-update`,
    { items },
  )
}

export function jsonImportParameters(templateId: number, jsonData: string, parentId?: number) {
  return request.post<any, ParameterDTO[]>(
    `/templates/${templateId}/parameters/json-import`,
    { jsonData, parentId },
  )
}


export function getAggregationSchema(templateId: number) {
  return request.get<any, AggregationSchemaDTO[]>(`/templates/${templateId}/aggregation-schema`)
}

