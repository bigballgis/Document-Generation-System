import request from './request'
import type { LoginRequest, TokenPair } from '@/types'

export interface RegisterRequest {
  username: string
  email: string
  password: string
}

export function loginApi(data: LoginRequest) {
  return request.post<any, TokenPair>('/auth/login', data)
}

export function registerApi(data: RegisterRequest) {
  return request.post<any, TokenPair>('/auth/register', data)
}

export function refreshTokenApi(refreshToken: string) {
  return request.post<any, TokenPair>('/auth/refresh', { refreshToken })
}

/** Request password reset email */
export function resetPassword(email: string) {
  return request.post('/auth/reset-password', { email })
}
