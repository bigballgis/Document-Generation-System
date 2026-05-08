import { reactive } from 'vue'
import { vi } from 'vitest'

/** Mutable route state for `vue-router` mocks (Template workspace Index tests). */
export const mockRouteState = reactive({ params: { id: '1' } })

export const mockRouterPush = vi.fn()
