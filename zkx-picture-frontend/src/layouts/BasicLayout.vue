<template>
  <div id="basicLayout">
    <a-layout style="min-height: 100vh">
      <a-layout-header
        class="header"
        :class="{ 'header-frameless': isFrameless, 'header-hidden': isFrameless && headerHidden }"
      >
        <GlobalHeader />
      </a-layout-header>
      <a-layout>
        <GlobalSider v-if="!isFrameless" class="sider" />
        <a-layout-content
          class="content"
          :class="{ 'content-frameless': isFrameless }"
        >
          <router-view v-slot="{ Component }">
            <transition name="page-fade" mode="out-in">
              <component :is="Component" />
            </transition>
          </router-view>
        </a-layout-content>
      </a-layout>
      <a-layout-footer v-if="!isFrameless" class="footer" />
    </a-layout>
  </div>
</template>

<script setup lang="ts">
import { computed, ref, onMounted, onUnmounted } from 'vue'
import { useRoute } from 'vue-router'
import GlobalHeader from '@/components/GlobalHeader.vue'
import GlobalSider from '@/components/GlobalSider.vue'
import { useGlobalShortcuts } from '@/composables/useGlobalShortcuts'

// 全局键盘快捷键（/ 聚焦搜索）
useGlobalShortcuts()

const route = useRoute()

// 双布局模式：frameless = 无框浏览（无侧栏、无footer、header可滚动隐藏）
const isFrameless = computed(() => route.meta?.layout === 'frameless')

// 滚动方向检测：frameless 模式下向下滚隐藏 header，向上滚显示
const headerHidden = ref(false)
let lastScrollY = 0
let scrollTicking = false

const onScroll = () => {
  if (!scrollTicking) {
    requestAnimationFrame(() => {
      const currentY = window.scrollY
      if (currentY > 80 && currentY > lastScrollY) {
        headerHidden.value = true
      } else if (currentY < lastScrollY - 10) {
        headerHidden.value = false
      }
      lastScrollY = currentY
      scrollTicking = false
    })
    scrollTicking = true
  }
}

onMounted(() => {
  window.addEventListener('scroll', onScroll, { passive: true })
})

onUnmounted(() => {
  window.removeEventListener('scroll', onScroll)
})
</script>

<style scoped>
#basicLayout .header {
  padding-inline: 20px;
  background: var(--dt-color-bg-header);
  color: unset;
  margin-bottom: 1px;
  transition: transform 0.3s ease, opacity 0.3s ease;
}

/* 无框模式 header：绝对定位浮在内容上方 + backdrop模糊 */
#basicLayout .header-frameless {
  position: fixed;
  top: 0;
  left: 0;
  right: 0;
  z-index: 100;
  background: rgba(255, 255, 255, 0.85);
  backdrop-filter: blur(12px);
  -webkit-backdrop-filter: blur(12px);
  border-bottom: 1px solid var(--dt-color-border-default);
}

/* 暗色主题下 header 模糊背景 */
[data-theme='dark'] .header-frameless {
  background: rgba(13, 15, 18, 0.85);
}

/* 向下滚动时隐藏 header */
.header-hidden {
  transform: translateY(-100%);
  opacity: 0;
  pointer-events: none;
}

#basicLayout .sider {
  background: var(--dt-color-bg-sider);
  border-right: 1px solid var(--dt-color-border-default);
  padding-top: 20px;
}

#basicLayout :deep(.ant-menu-root) {
  border-bottom: none !important;
  border-inline-end: none !important;
}

#basicLayout .content {
  padding: var(--dt-space-loose);
  background: var(--dt-color-bg-app);
  margin-bottom: var(--dt-space-loose);
}

/* 无框模式内容区：全宽无边距 */
#basicLayout .content-frameless {
  padding: 0;
  margin-bottom: 0;
  /* header 高度补偿：frameless header 是 fixed 的 */
  padding-top: 65px;
}

#basicLayout .footer {
  background: var(--dt-color-bg-footer);
  padding: var(--dt-space-tight) var(--dt-space-standard);
  position: fixed;
  bottom: 0;
  left: 0;
  right: 0;
  text-align: center;
}

/* ---------- 页面过渡动画 ---------- */
.page-fade-enter-active,
.page-fade-leave-active {
  transition: opacity 0.2s ease, transform 0.2s ease;
}
.page-fade-enter-from {
  opacity: 0;
  transform: translateY(8px);
}
.page-fade-leave-to {
  opacity: 0;
  transform: translateY(-8px);
}
</style>
