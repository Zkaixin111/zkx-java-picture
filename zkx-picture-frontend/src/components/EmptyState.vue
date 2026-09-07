<template>
  <div class="empty-state">
    <div class="empty-icon">
      <slot name="icon">
        <span class="empty-icon-default">
          <svg width="64" height="64" viewBox="0 0 64 64" fill="none">
            <rect x="8" y="12" width="48" height="40" rx="4" stroke="currentColor" stroke-width="1.5" />
            <circle cx="22" cy="28" r="5" stroke="currentColor" stroke-width="1.5" />
            <path d="M8 44l12-10 8 6 16-12 12 8v8a4 4 0 01-4 4H12a4 4 0 01-4-4v0z" stroke="currentColor" stroke-width="1.5" />
          </svg>
        </span>
      </slot>
    </div>
    <h3 class="empty-title">{{ title }}</h3>
    <p v-if="description" class="empty-description">{{ description }}</p>
    <div v-if="$slots.action" class="empty-action">
      <slot name="action" />
    </div>
  </div>
</template>

<script setup lang="ts">
withDefaults(defineProps<{
  title?: string
  description?: string
}>(), {
  title: '暂无内容',
  description: '',
})
</script>

<style scoped>
.empty-state {
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  padding: 64px 24px;
  text-align: center;
}

.empty-icon {
  margin-bottom: 20px;
  color: var(--dt-color-text-tertiary);
  opacity: 0.5;
}

.empty-icon-default svg {
  display: block;
}

.empty-title {
  font-size: 16px;
  font-weight: 600;
  color: var(--dt-color-text-primary);
  margin: 0 0 8px;
}

.empty-description {
  font-size: 14px;
  color: var(--dt-color-text-secondary);
  margin: 0 0 24px;
  max-width: 360px;
  line-height: 1.5;
}

.empty-action {
  margin-top: 4px;
}
</style>
