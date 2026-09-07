<template>
  <a-tooltip :title="isDark ? '切换至浅色模式' : '切换至深色模式'">
    <a-button type="text" @click="toggle" class="theme-toggle-btn">
      <span class="theme-icon">{{ isDark ? '☀️' : '🌙' }}</span>
    </a-button>
  </a-tooltip>
</template>

<script setup lang="ts">
import { computed } from 'vue'
import { useTheme } from '@/composables/useTheme'

const { theme, toggle } = useTheme()
const isDark = computed(() => theme.value === 'dark')
</script>

<style scoped>
.theme-toggle-btn {
  font-size: 18px;
  line-height: 1;
}
.theme-icon {
  display: inline-block;
  transition: transform 0.3s ease;
}
.theme-toggle-btn:hover .theme-icon {
  transform: rotate(20deg);
}
</style>
