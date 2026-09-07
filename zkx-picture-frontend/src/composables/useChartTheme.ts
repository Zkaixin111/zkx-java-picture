import { computed } from 'vue'
import { useTheme } from './useTheme'

/**
 * ECharts 图表暗色模式颜色适配
 * 在图表 options 中使用这些颜色以跟随主题变化
 */
export function useChartTheme() {
  const { theme } = useTheme()
  const isDark = computed(() => theme.value === 'dark')

  const chartColors = computed(() => ({
    textColor: isDark.value ? '#9aa0a6' : '#6b7280',
    axisColor: isDark.value ? '#3a3f45' : '#e8eaed',
    splitColor: isDark.value ? '#2d3136' : '#f0f1f2',
    tooltipBg: isDark.value ? '#1e2025' : '#fff',
    tooltipBorder: isDark.value ? '#3a3f45' : '#e8eaed',
    series1: isDark.value ? '#7baaf7' : '#5470c6',
    series2: isDark.value ? '#a5d6a7' : '#91cc75',
  }))

  return { isDark, chartColors }
}
