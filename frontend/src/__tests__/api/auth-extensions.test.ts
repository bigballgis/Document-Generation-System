import { describe, it, expect, vi, beforeEach } from 'vitest'

const mockPost = vi.fn()

vi.mock('@/api/request', () => ({
  default: {
    get: vi.fn(),
    post: (...args: any[]) => mockPost(...args),
    put: vi.fn(),
    delete: vi.fn(),
  },
}))

import { resetPassword } from '@/api/auth'

describe('auth.ts extension functions', () => {
  beforeEach(() => {
    mockPost.mockClear()
    mockPost.mockResolvedValue(undefined)
  })

  describe('resetPassword', () => {
    it('sends POST to /auth/reset-password with email body', async () => {
      await resetPassword('user@example.com')

      expect(mockPost).toHaveBeenCalledWith('/auth/reset-password', { email: 'user@example.com' })
    })
  })
})
