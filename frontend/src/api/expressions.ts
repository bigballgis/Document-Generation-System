import request from './request'
import type { ExpressionDTO, CreateExpressionRequest, UpdateExpressionRequest, ValidateExpressionRequest, ExpressionValidationResult } from '@/types/document'

export function createExpression(templateId: number, data: CreateExpressionRequest) {
  return request.post<any, ExpressionDTO>(`/templates/${templateId}/expressions`, data)
}

export function getExpressions(templateId: number) {
  return request.get<any, ExpressionDTO[]>(`/templates/${templateId}/expressions`)
}

export function updateExpression(id: number, data: UpdateExpressionRequest) {
  return request.put<any, ExpressionDTO>(`/expressions/${id}`, data)
}

export function deleteExpression(id: number) {
  return request.delete(`/expressions/${id}`)
}

export function validateExpression(data: ValidateExpressionRequest) {
  return request.post<any, ExpressionValidationResult>('/expressions/validate', data)
}
