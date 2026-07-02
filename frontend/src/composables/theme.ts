import { ref, watchEffect } from 'vue'

export type ThemeMode = 'light' | 'dark' | 'auto'

const KEY = 'libris.theme'
const stored = localStorage.getItem(KEY)
export const themeMode = ref<ThemeMode>(stored === 'dark' || stored === 'auto' ? stored : 'light')

const media = window.matchMedia('(prefers-color-scheme: dark)')

function apply() {
  const dark = themeMode.value === 'dark' || (themeMode.value === 'auto' && media.matches)
  document.documentElement.classList.toggle('dark', dark)
}

media.addEventListener('change', apply)
watchEffect(() => {
  localStorage.setItem(KEY, themeMode.value)
  apply()
})

export function cycleTheme() {
  themeMode.value = themeMode.value === 'light' ? 'dark' : themeMode.value === 'dark' ? 'auto' : 'light'
}
