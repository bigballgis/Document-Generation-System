import request from './request'

export type TeamApprovalMode = 'CROSS_REVIEW' | 'MAKER_CHECKER'

export interface TeamDTO {
  id: number
  tenantId: number
  name: string
  description?: string
  approvalMode?: TeamApprovalMode
  adGroupObjectId?: string | null
  adMakerGroupObjectId?: string | null
  adCheckerGroupObjectId?: string | null
  createdAt: string
}

export interface CreateTeamRequest {
  name: string
  description?: string
  approvalMode?: TeamApprovalMode
  adGroupObjectId?: string
  adMakerGroupObjectId?: string
  adCheckerGroupObjectId?: string
  tenantId?: number
}

export interface UpdateTeamRequest {
  name?: string
  description?: string
  approvalMode?: TeamApprovalMode
  adGroupObjectId?: string
  adMakerGroupObjectId?: string
  adCheckerGroupObjectId?: string
}

export function createTeam(tenantId: number, data: CreateTeamRequest) {
  return request.post<any, TeamDTO>(`/tenants/${tenantId}/teams`, data)
}

export function listTeams(tenantId: number) {
  return request.get<any, TeamDTO[]>(`/tenants/${tenantId}/teams`)
}

export function getTeam(tenantId: number, teamId: number) {
  return request.get<any, TeamDTO>(`/tenants/${tenantId}/teams/${teamId}`)
}

export function updateTeam(tenantId: number, teamId: number, data: UpdateTeamRequest) {
  return request.put<any, TeamDTO>(`/tenants/${tenantId}/teams/${teamId}`, data)
}

export function deleteTeam(tenantId: number, teamId: number) {
  return request.delete(`/tenants/${tenantId}/teams/${teamId}`)
}
