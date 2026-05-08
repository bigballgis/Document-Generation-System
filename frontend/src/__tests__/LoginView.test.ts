import { describe, it, expect, vi, beforeEach } from 'vitest'
import { mount, flushPromises } from '@vue/test-utils'
import { createPinia, setActivePinia } from 'pinia'
import { createI18n } from 'vue-i18n'
import LoginView from '@/views/auth/LoginView.vue'

// Mock vue-router
const pushMock = vi.fn()
vi.mock('vue-router', () => ({
  useRouter: () => ({ push: pushMock }),
  useRoute: () => ({ query: {} }),
}))

const mockedStoreLogin = vi.fn()
vi.mock('@/stores/user', () => ({
  useUserStore: () => ({ login: mockedStoreLogin }),
}))

const i18n = createI18n({
  legacy: false,
  locale: 'en-US',
  messages: {
    'en-US': {
      auth: {
        login: 'Login',
        username: 'Username',
        password: 'Password',
        goRegister: 'Register',
        forgotPassword: 'Forgot password',
        loginSuccess: 'Login success',
        usernameRequired: 'Required',
        passwordRequired: 'Required',
      },
      common: { loading: 'Loading...' },
    },
  },
})

describe('LoginView', () => {
  beforeEach(() => {
    setActivePinia(createPinia())
    pushMock.mockReset()
    mockedStoreLogin.mockReset()
  })

  it('renders login form with username and password fields', () => {
    const wrapper = mount(LoginView, { global: { plugins: [i18n] } })

    // Title
    expect(wrapper.find('h2').text()).toBe('DocGen')

    // Two input fields (username + password)
    const inputs = wrapper.findAll('.el-input')
    expect(inputs.length).toBeGreaterThanOrEqual(2)

    // Submit button
    const btn = wrapper.find('.el-button--primary')
    expect(btn.exists()).toBe(true)
    expect(btn.text()).toContain('Login')
  })

  it('renders a link to the register page', () => {
    const wrapper = mount(LoginView, { global: { plugins: [i18n] } })
    const link = wrapper.find('a, .form-links a, a[href]')
    expect(link.exists()).toBe(true)
    expect(link.text()).toContain('Register')
  })

  it('has empty form fields initially', () => {
    const wrapper = mount(LoginView, { global: { plugins: [i18n] } })
    const inputs = wrapper.findAll('input')
    for (const input of inputs) {
      expect(input.element.value).toBe('')
    }
  })

  it('allows typing into username and password fields', async () => {
    const wrapper = mount(LoginView, { global: { plugins: [i18n] } })
    const inputs = wrapper.findAll('input')

    await inputs[0].setValue('testuser')
    await inputs[1].setValue('Password123!')

    expect(inputs[0].element.value).toBe('testuser')
    expect(inputs[1].element.value).toBe('Password123!')
  })

  it('calls login and navigates on successful submission', async () => {
    mockedStoreLogin.mockResolvedValue(undefined)
    const wrapper = mount(LoginView, { global: { plugins: [i18n] } })
    const inputs = wrapper.findAll('input')

    await inputs[0].setValue('testuser')
    await inputs[1].setValue('Password123!')
    await flushPromises()

    await wrapper.find('form').trigger('submit')
    await flushPromises()

    expect(mockedStoreLogin).toHaveBeenCalledWith({
      username: 'testuser',
      password: 'Password123!',
    })
    expect(pushMock).toHaveBeenCalledWith('/')
  })

  it('shows loading state on the button during login', async () => {
    // Create a promise that we control
    let resolveLogin!: (value: any) => void
    mockedStoreLogin.mockReturnValue(
      new Promise((resolve) => {
        resolveLogin = resolve
      }),
    )

    const wrapper = mount(LoginView, { global: { plugins: [i18n] } })
    const inputs = wrapper.findAll('input')

    await inputs[0].setValue('testuser')
    await inputs[1].setValue('Password123!')
    await flushPromises()

    await wrapper.find('form').trigger('submit')
    await flushPromises()

    // Button should be in loading state
    const btn = wrapper.find('.el-button--primary')
    expect(btn.attributes('aria-busy') === 'true' || btn.classes().includes('is-loading')).toBe(true)

    // Resolve the login
    resolveLogin(undefined)
    await flushPromises()

    // Loading should be done
    expect(wrapper.find('.el-button--primary').attributes('aria-busy')).not.toBe('true')
  })
})
