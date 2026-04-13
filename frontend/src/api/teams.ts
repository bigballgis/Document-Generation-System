import request from './request'

export interface TeamDTO {
  id: number
  tenantId: number
  name: string
  createdAt: string
  updatedAt: string
}

export interface CreateTeamRequest {
  name: string
  tenantId?: number
}

export interface UpdateTeamRequest {
  name: string
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
