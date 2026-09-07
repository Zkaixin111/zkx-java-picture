import { ref, watchEffect } from 'vue'

type Theme = 'light' | 'dark'

const stored = localStorage.getItem('dt-theme') as Theme | null
const systemDark = window.matchMedia('(prefers-color-scheme: dark)').matches

const theme = ref<Theme>(stored ?? (systemDark ? 'dark' : 'light'))

watchEffect(() => {
  document.documentElement.setAttribute('data-theme', theme.value)
  localStorage.setItem('dt-theme', theme.value)
})

// 监听系统主题变化
window.matchMedia('(prefers-color-scheme: dark)').addEventListener('change', (e) => {
  if (!localStorage.getItem('dt-theme')) {
    theme.value = e.matches ? 'dark' : 'light'
  }
})

export function useTheme() {
  const toggle = () => {
    theme.value = theme.value === 'light' ? 'dark' : 'light'
  }
  return { theme, toggle }
}
