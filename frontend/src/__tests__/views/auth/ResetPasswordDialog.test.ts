import { describe, it, expect, vi, beforeEach } from 'vitest'
import { mount, flushPromises } from '@vue/test-utils'

vi.mock('@/api/auth', () => ({
  resetPassword: vi.fn(),
  loginApi: vi.fn(),
  registerApi: vi.fn(),
  refreshTokenApi: vi.fn(),
}))

import ResetPasswordDialog from '@/views/auth/ResetPasswordDialog.vue'
import { resetPassword } from '@/api/auth'

describe('ResetPasswordDialog', () => {
  beforeEach(() => {
    vi.mocked(resetPassword).mockReset()
  })

  it('renders email form field', async () => {
    const wrapper = mount(ResetPasswordDialog, {
      props: { visible: true },
    })
    await flushPromises()

    // Should have an email input
    const emailInput = wrapper.find('input[type="email"]')
    expect(emailInput.exists()).toBe(true)
  })

  it('closes dialog on successful submission', async () => {
    vi.mocked(resetPassword).mockResolvedValue(undefined as any)

    const wrapper = mount(ResetPasswordDialog, {
      props: { visible: true },
    })
    await flushPromises()

    // Fill in email
    const emailInput = wrapper.find('input[type="email"]')
    await emailInput.setValue('user@example.com')
    await flushPromises()

    // Click submit
    const submitBtn = wrapper.findAll('.el-button').find(b => b.text().includes('Submit'))
    expect(submitBtn).toBeDefined()
    await submitBtn!.trigger('click')
    await flushPromises()
    await new Promise(r => setTimeout(r, 100))
    await flushPromises()

    expect(resetPassword).toHaveBeenCalledWith('user@example.com')
    // Should emit update:visible with false to close dialog
    const emitted = wrapper.emitted('update:visible')
    expect(emitted).toBeDefined()
    expect(emitted![emitted!.length - 1]).toEqual([false])
  })

  it('keeps dialog open on failed submission', async () => {
    vi.mocked(resetPassword).mockRejectedValue(new Error('Server error'))

    const wrapper = mount(ResetPasswordDialog, {
      props: { visible: true },
    })
    await flushPromises()

    // Fill in email
    const emailInput = wrapper.find('input[type="email"]')
    await emailInput.setValue('user@example.com')
    await flushPromises()

    // Click submit
    const submitBtn = wrapper.findAll('.el-button').find(b => b.text().includes('Submit'))
    await submitBtn!.trigger('click')
    await flushPromises()
    await new Promise(r => setTimeout(r, 100))
    await flushPromises()

    expect(resetPassword).toHaveBeenCalledWith('user@example.com')
    // Should NOT emit update:visible with false (dialog stays open)
    const emitted = wrapper.emitted('update:visible')
    const closeCalls = emitted?.filter(args => args[0] === false) ?? []
    expect(closeCalls.length).toBe(0)
  })
})
