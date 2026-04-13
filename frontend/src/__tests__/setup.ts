import { config } from '@vue/test-utils'
import { createI18n } from 'vue-i18n'
import ElementPlus from 'element-plus'
import enUS from '@/i18n/en-US.json'

// Create a shared i18n instance for tests
const i18n = createI18n({
  legacy: false,
  locale: 'en-US',
  fallbackLocale: 'en-US',
  messages: { 'en-US': enUS },
})

// Register Element Plus and i18n globally for all test mounts
config.global.plugins = [ElementPlus, i18n]

// Stub router-link globally so template rendering doesn't fail
config.global.stubs = {
  'router-link': {
    template: '<a><slot /></a>',
    props: ['to'],
  },
}
