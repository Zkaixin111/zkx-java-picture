<template>
  <div id="app">
    <a-config-provider :locale="zhCN" :theme="antTheme">
      <BasicLayout />
    </a-config-provider>
  </div>
</template>

<script setup lang="ts">
import { computed } from 'vue'
import zhCN from 'ant-design-vue/es/locale/zh_CN'
import { theme as antdTheme } from 'ant-design-vue'
import dayjs from 'dayjs'
import 'dayjs/locale/zh-cn'
dayjs.locale('zh-cn')

import BasicLayout from '@/layouts/BasicLayout.vue'
import { useTheme } from '@/composables/useTheme'

const { theme } = useTheme()
const { defaultAlgorithm, darkAlgorithm } = antdTheme

// 双主题 ConfigProvider：浅色/暗色联动 Ant Design 算法和设计令牌
const antTheme = computed(() => ({
  algorithm: theme.value === 'dark' ? darkAlgorithm : defaultAlgorithm,
  token: {
    colorPrimary: theme.value === 'dark' ? '#4da3ff' : '#1a73e8',
    colorBgContainer: theme.value === 'dark' ? '#16181c' : '#ffffff',
    colorBgLayout: theme.value === 'dark' ? '#0d0f12' : '#f5f6f8',
    colorBorder: theme.value === 'dark' ? '#2d3136' : '#e8eaed',
    colorText: theme.value === 'dark' ? '#e8eaed' : '#141516',
    colorTextSecondary: theme.value === 'dark' ? '#9aa0a6' : '#6b7280',
    colorTextTertiary: theme.value === 'dark' ? '#6b7280' : '#9ca3af',
    borderRadius: 6,
    borderRadiusSM: 4,
    borderRadiusLG: 12,
    fontFamily: "'Inter', -apple-system, 'PingFang SC', 'Microsoft YaHei', sans-serif",
  },
}))
</script>

<style scoped></style>
