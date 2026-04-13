import { describe, it, expect, vi, beforeEach } from 'vitest'
import { mount, flushPromises } from '@vue/test-utils'
import { createPinia, setActivePinia } from 'pinia'
import LoginView from '@/views/auth/LoginView.vue'

// Mock vue-router
const pushMock = vi.fn()
vi.mock('vue-router', () => ({
  useRouter: () => ({ push: pushMock }),
  useRoute: () => ({ query: {} }),
}))

// Mock the auth API at the store level
vi.mock('@/api/auth', () => ({
  loginApi: vi.fn(),
  registerApi: vi.fn(),
  refreshTokenApi: vi.fn(),
}))

describe('LoginView', () => {
  beforeEach(() => {
    setActivePinia(createPinia())
    pushMock.mockReset()
  })

  it('renders login form with username and password fields', () => {
    const wrapper = mount(LoginView)

    // Title
    expect(wrapper.find('h2').text()).toBe('Login')

    // Two input fields (username + password)
    const inputs = wrapper.findAll('.el-input')
    expect(inputs.length).toBeGreaterThanOrEqual(2)

    // Submit button
    const btn = wrapper.find('.el-button--primary')
    expect(btn.exists()).toBe(true)
    expect(btn.text()).toBe('Login')
  })

  it('renders a link to the register page', () => {
    const wrapper = mount(LoginView)
    const link = wrapper.find('a')
    expect(link.exists()).toBe(true)
    expect(link.text()).toContain('Register')
  })

  it('has empty form fields initially', () => {
    const wrapper = mount(LoginView)
    const inputs = wrapper.findAll('input')
    for (const input of inputs) {
      expect(input.element.value).toBe('')
    }
  })

  it('allows typing into username and password fields', async () => {
    const wrapper = mount(LoginView)
    const inputs = wrapper.findAll('input')

    await inputs[0].setValue('testuser')
    await inputs[1].setValue('Password123!')

    expect(inputs[0].element.value).toBe('testuser')
    expect(inputs[1].element.value).toBe('Password123!')
  })

  it('calls login and navigates on successful submission', async () => {
    const { loginApi } = await import('@/api/auth')
    const mockedLogin = vi.mocked(loginApi)
    mockedLogin.mockResolvedValue({
      accessToken: 'eyJhbGciOiJIUzI1NiJ9.eyJ1c2VySWQiOjEsInVzZXJuYW1lIjoidGVzdCIsInJvbGUiOiJVU0VSIiwidGVuYW50SWQiOjF9.abc',
      refreshToken: 'refresh-token',
    })

    const wrapper = mount(LoginView)
    const inputs = wrapper.findAll('input')

    await inputs[0].setValue('testuser')
    await inputs[1].setValue('Password123!')
    await flushPromises()

    await wrapper.find('.el-button--primary').trigger('click')
    await flushPromises()
    await new Promise((r) => setTimeout(r, 100))
    await flushPromises()

    expect(mockedLogin).toHaveBeenCalledWith({
      username: 'testuser',
      password: 'Password123!',
    })
    expect(pushMock).toHaveBeenCalledWith('/')
  })

  it('shows loading state on the button during login', async () => {
    const { loginApi } = await import('@/api/auth')
    const mockedLogin = vi.mocked(loginApi)

    // Create a promise that we control
    let resolveLogin!: (value: any) => void
    mockedLogin.mockReturnValue(
      new Promise((resolve) => {
        resolveLogin = resolve
      }),
    )

    const wrapper = mount(LoginView)
    const inputs = wrapper.findAll('input')

    await inputs[0].setValue('testuser')
    await inputs[1].setValue('Password123!')
    await flushPromises()

    await wrapper.find('.el-button--primary').trigger('click')
    await flushPromises()

    // Button should be in loading state
    const btn = wrapper.find('.el-button--primary')
    expect(btn.classes()).toContain('is-loading')

    // Resolve the login
    resolveLogin({
      accessToken: 'eyJhbGciOiJIUzI1NiJ9.eyJ1c2VySWQiOjF9.abc',
      refreshToken: 'rt',
    })
    await flushPromises()
    await new Promise((r) => setTimeout(r, 50))
    await flushPromises()

    // Loading should be done
    expect(wrapper.find('.el-button--primary').classes()).not.toContain('is-loading')
  })
})
