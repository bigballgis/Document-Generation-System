import axios from 'axios'
import type { AxiosInstance, InternalAxiosRequestConfig, AxiosResponse, AxiosError } from 'axios'
import { ElMessage } from 'element-plus'

const service: AxiosInstance = axios.create({
  baseURL: '/api',
  timeout: 15000,
})

let isRefreshing = false
let pendingRequests: Array<(token: string) => void> = []

function getAccessToken(): string | null {
  return localStorage.getItem('access_token')
}

function getRefreshToken(): string | null {
  return localStorage.getItem('refresh_token')
}

function setTokens(accessToken: string, refreshToken: string) {
  localStorage.setItem('access_token', accessToken)
  localStorage.setItem('refresh_token', refreshToken)
}

function clearTokens() {
  localStorage.removeItem('access_token')
  localStorage.removeItem('refresh_token')
}

function redirectToLogin() {
  clearTokens()
  if (window.location.pathname !== '/login') {
    window.location.href = '/login'
  }
}

// Request interceptor — attach JWT token
service.interceptors.request.use(
  (config: InternalAxiosRequestConfig) => {
    const token = getAccessToken()
    if (token && config.headers) {
      config.headers.Authorization = `Bearer ${token}`
    }
    return config
  },
  (error) => Promise.reject(error),
)

// Response interceptor — handle 401 with token refresh
service.interceptors.response.use(
  (response: AxiosResponse) => response.data,
  async (error: AxiosError) => {
    const originalRequest = error.config as InternalAxiosRequestConfig & { _retry?: boolean }
    const status = error.response?.status

    if (status === 401 && !originalRequest._retry) {
      // Don't retry refresh or login requests
      if (originalRequest.url?.includes('/auth/refresh') || originalRequest.url?.includes('/auth/login')) {
        redirectToLogin()
        return Promise.reject(error)
      }

      if (isRefreshing) {
        // Queue this request until refresh completes
        return new Promise((resolve) => {
          pendingRequests.push((newToken: string) => {
            originalRequest.headers.Authorization = `Bearer ${newToken}`
            resolve(service(originalRequest))
          })
        })
      }

      originalRequest._retry = true
      isRefreshing = true

      const refreshToken = getRefreshToken()
      if (!refreshToken) {
        redirectToLogin()
        return Promise.reject(error)
      }

      try {
        // Use a plain axios call to avoid interceptor loops
        const { data } = await axios.post<{ accessToken: string; refreshToken: string }>(
          '/api/auth/refresh',
          { refreshToken },
        )
        const newAccessToken = data.accessToken
        const newRefreshToken = data.refreshToken

        setTokens(newAccessToken, newRefreshToken)

        // Retry all queued requests
        pendingRequests.forEach((cb) => cb(newAccessToken))
        pendingRequests = []

        originalRequest.headers.Authorization = `Bearer ${newAccessToken}`
        return service(originalRequest)
      } catch {
        redirectToLogin()
        return Promise.reject(error)
      } finally {
        isRefreshing = false
      }
    }

    if (status === 403) {
      ElMessage.error('Access denied')
    } else if (status === 429) {
      ElMessage.warning('Too many requests, please try again later')
    } else if (status !== 401) {
      const message = (error.response?.data as any)?.message || error.message
      ElMessage.error(message)
    }

    return Promise.reject(error)
  },
)

export default service
