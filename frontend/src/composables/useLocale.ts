import { useI18n } from 'vue-i18n'

export function useLocale() {
  const { locale, t } = useI18n()

  function setLocale(lang: string) {
    locale.value = lang
    localStorage.setItem('locale', lang)
  }

  return { locale, t, setLocale }
}
