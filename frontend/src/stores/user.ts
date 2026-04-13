import { defineStore } from 'pinia'
import { ref, computed } from 'vue'
import { loginApi, registerApi, refreshTokenApi } from '@/api/auth'
import type { LoginRequest } from '@/types'
import type { RegisterRequest } from '@/api/auth'

export type UserRole = 'SUPER_ADMIN' | 'TENANT_ADMIN' | 'TEAM_ADMIN' | 'USER'

export interface UserInfo {
  id: number
  username: string
  email: string
  role: UserRole
  tenantId: number
  teamId?: number
  locale: string
}

export const useUserStore = defineStore('user', () => {
  const token = ref<string>(localStorage.getItem('access_token') || '')
  const refreshToken = ref<string>(localStorage.getItem('refresh_token') || '')
  const userInfo = ref<UserInfo | null>(null)

  const isLoggedIn = computed(() => !!token.value)
  const userRole = computed(() => userInfo.value?.role || null)

  function setToken(accessToken: string, refresh: string) {
    token.value = accessToken
    refreshToken.value = refresh
    localStorage.setItem('access_token', accessToken)
    localStorage.setItem('refresh_token', refresh)
  }

  function setUserInfo(info: UserInfo) {
    userInfo.value = info
  }

  /** Parse basic user info from JWT payload (best-effort) */
  function parseTokenPayload(jwt: string): Partial<UserInfo> | null {
    try {
      const parts = jwt.split('.')
      if (parts.length !== 3) return null
      const payload = JSON.parse(atob(parts[1]))
      return {
        id: payload.userId ?? payload.sub,
        username: payload.username ?? payload.sub,
        email: payload.email ?? '',
        role: payload.role ?? 'USER',
        tenantId: payload.tenantId ?? 0,
        teamId: payload.teamId,
        locale: payload.locale ?? 'en-US',
      }
    } catch {
      return null
    }
  }

  async function login(data: LoginRequest) {
    const result = await loginApi(data)
    setToken(result.accessToken, result.refreshToken)
    // Extract user info from token
    const info = parseTokenPayload(result.accessToken)
    if (info) {
      setUserInfo(info as UserInfo)
    }
    return result
  }

  async function register(data: RegisterRequest) {
    const result = await registerApi(data)
    setToken(result.accessToken, result.refreshToken)
    const info = parseTokenPayload(result.accessToken)
    if (info) {
      setUserInfo(info as UserInfo)
    }
    return result
  }

  async function refresh() {
    if (!refreshToken.value) throw new Error('No refresh token')
    const result = await refreshTokenApi(refreshToken.value)
    setToken(result.accessToken, result.refreshToken)
    const info = parseTokenPayload(result.accessToken)
    if (info) {
      setUserInfo(info as UserInfo)
    }
    return result
  }

  function logout() {
    token.value = ''
    refreshToken.value = ''
    userInfo.value = null
    localStorage.removeItem('access_token')
    localStorage.removeItem('refresh_token')
  }

  // On store init, try to restore user info from existing token
  if (token.value) {
    const info = parseTokenPayload(token.value)
    if (info) {
      userInfo.value = info as UserInfo
    }
  }

  return {
    token,
    refreshToken,
    userInfo,
    isLoggedIn,
    userRole,
    setToken,
    setUserInfo,
    login,
    register,
    refresh,
    logout,
  }
})
