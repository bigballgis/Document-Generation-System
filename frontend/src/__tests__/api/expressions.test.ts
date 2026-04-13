import { describe, it, expect, vi, beforeEach } from 'vitest'

const mockGet = vi.fn()
const mockPost = vi.fn()
const mockPut = vi.fn()
const mockDelete = vi.fn()

vi.mock('@/api/request', () => ({
  default: {
    get: (...args: any[]) => mockGet(...args),
    post: (...args: any[]) => mockPost(...args),
    put: (...args: any[]) => mockPut(...args),
    delete: (...args: any[]) => mockDelete(...args),
  },
}))

import {
  createExpression,
  getExpressions,
  updateExpression,
  deleteExpression,
  validateExpression,
} from '@/api/expressions'

describe('expressions API', () => {
  beforeEach(() => {
    mockGet.mockClear()
    mockPost.mockClear()
    mockPut.mockClear()
    mockDelete.mockClear()
    mockGet.mockResolvedValue([])
    mockPost.mockResolvedValue({})
    mockPut.mockResolvedValue({})
    mockDelete.mockResolvedValue(undefined)
  })

  describe('createExpression', () => {
    it('sends POST to /templates/{templateId}/expressions', async () => {
      const data = { name: 'calc', expressionType: 'JAVASCRIPT', expressionText: '1+1' }
      await createExpression(10, data)

      expect(mockPost).toHaveBeenCalledWith('/templates/10/expressions', data)
    })
  })

  describe('getExpressions', () => {
    it('sends GET to /templates/{templateId}/expressions', async () => {
      await getExpressions(10)

      expect(mockGet).toHaveBeenCalledWith('/templates/10/expressions')
    })
  })

  describe('updateExpression', () => {
    it('sends PUT to /expressions/{id}', async () => {
      const data = { name: 'updated', expressionText: '2+2' }
      await updateExpression(5, data)

      expect(mockPut).toHaveBeenCalledWith('/expressions/5', data)
    })
  })

  describe('deleteExpression', () => {
    it('sends DELETE to /expressions/{id}', async () => {
      await deleteExpression(7)

      expect(mockDelete).toHaveBeenCalledWith('/expressions/7')
    })
  })

  describe('validateExpression', () => {
    it('sends POST to /expressions/validate', async () => {
      const data = { expression: '1+1', expressionType: 'JAVASCRIPT' }
      await validateExpression(data)

      expect(mockPost).toHaveBeenCalledWith('/expressions/validate', data)
    })
  })
})
