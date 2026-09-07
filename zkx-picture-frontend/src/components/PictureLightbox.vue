<template>
  <teleport to="body">
    <transition name="lightbox-fade">
      <div
        v-if="visible"
        class="lightbox-backdrop"
        @click.self="close"
        @keydown.esc="close"
        tabindex="0"
        ref="backdropRef"
      >
        <!-- 右上角操作栏 -->
        <div class="lightbox-topbar">
          <span class="lightbox-counter">{{ currentIndex + 1 }} / {{ pictures.length }}</span>
          <a-space>
            <a-button type="text" class="lightbox-action-btn" @click="goDetail">
              查看详情
            </a-button>
            <a-button type="text" class="lightbox-close-btn" @click="close">
              ✕
            </a-button>
          </a-space>
        </div>

        <!-- 左箭头 -->
        <button
          v-if="pictures.length > 1"
          class="lightbox-arrow lightbox-arrow-left"
          @click.stop="prev"
        >
          ‹
        </button>

        <!-- 主图 -->
        <div class="lightbox-image-wrap">
          <img
            :key="currentPicture?.url"
            :src="currentPicture?.url"
            :alt="currentPicture?.name"
            class="lightbox-image img-fade-in"
            @load="($event.target as HTMLImageElement).classList.add('is-loaded')"
          />
        </div>

        <!-- 右箭头 -->
        <button
          v-if="pictures.length > 1"
          class="lightbox-arrow lightbox-arrow-right"
          @click.stop="next"
        >
          ›
        </button>

        <!-- 底部缩略图条 -->
        <div v-if="pictures.length > 1" class="lightbox-thumbnails">
          <div
            v-for="(pic, index) in pictures"
            :key="pic.id"
            class="lightbox-thumb"
            :class="{ 'is-active': index === currentIndex }"
            @click="currentIndex = index"
          >
            <img :src="pic.thumbnailUrl ?? pic.url" :alt="pic.name" />
          </div>
        </div>
      </div>
    </transition>
  </teleport>
</template>

<script setup lang="ts">
import { computed, ref, watch, nextTick } from 'vue'
import { useRouter } from 'vue-router'

interface Props {
  pictures: API.PictureVO[]
  initialIndex?: number
}

const props = withDefaults(defineProps<Props>(), {
  pictures: () => [],
  initialIndex: 0,
})

const emit = defineEmits<{
  close: []
}>()

const router = useRouter()
const visible = ref(false)
const currentIndex = ref(0)
const backdropRef = ref<HTMLElement>()

const currentPicture = computed(() => props.pictures[currentIndex.value])

const open = (index = 0) => {
  currentIndex.value = index
  visible.value = true
  nextTick(() => backdropRef.value?.focus())
}

const close = () => {
  visible.value = false
  emit('close')
}

const prev = () => {
  if (currentIndex.value > 0) {
    currentIndex.value--
  } else {
    currentIndex.value = props.pictures.length - 1
  }
}

const next = () => {
  if (currentIndex.value < props.pictures.length - 1) {
    currentIndex.value++
  } else {
    currentIndex.value = 0
  }
}

const goDetail = () => {
  const pic = currentPicture.value
  if (pic?.id) {
    close()
    router.push({ path: `/picture/${pic.id}` })
  }
}

// 键盘控制
const onKeydown = (e: KeyboardEvent) => {
  if (!visible.value) return
  switch (e.key) {
    case 'ArrowLeft':
      prev()
      break
    case 'ArrowRight':
      next()
      break
    case 'Escape':
      close()
      break
  }
}

watch(visible, (val) => {
  if (val) {
    window.addEventListener('keydown', onKeydown)
    document.body.style.overflow = 'hidden'
  } else {
    window.removeEventListener('keydown', onKeydown)
    document.body.style.overflow = ''
  }
})

defineExpose({ open, close })
</script>

<style scoped>
/* 遮罩 */
.lightbox-backdrop {
  position: fixed;
  inset: 0;
  z-index: 1000;
  background: rgba(0, 0, 0, 0.92);
  display: flex;
  align-items: center;
  justify-content: center;
  outline: none;
}

/* 过渡动画 */
.lightbox-fade-enter-active {
  transition: opacity 0.25s ease;
}
.lightbox-fade-leave-active {
  transition: opacity 0.2s ease;
}
.lightbox-fade-enter-from,
.lightbox-fade-leave-to {
  opacity: 0;
}

/* 顶部栏 */
.lightbox-topbar {
  position: absolute;
  top: 0;
  left: 0;
  right: 0;
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding: 16px 24px;
  z-index: 10;
  background: linear-gradient(rgba(0, 0, 0, 0.5), transparent);
}

.lightbox-counter {
  color: rgba(255, 255, 255, 0.7);
  font-size: 14px;
  font-variant-numeric: tabular-nums;
}

.lightbox-action-btn,
.lightbox-close-btn {
  color: rgba(255, 255, 255, 0.8) !important;
  font-size: 16px;
}
.lightbox-action-btn:hover,
.lightbox-close-btn:hover {
  color: #fff !important;
  background: rgba(255, 255, 255, 0.12) !important;
}

/* 箭头按钮 */
.lightbox-arrow {
  position: absolute;
  top: 50%;
  transform: translateY(-50%);
  z-index: 10;
  background: rgba(255, 255, 255, 0.1);
  border: none;
  color: #fff;
  font-size: 36px;
  width: 48px;
  height: 72px;
  cursor: pointer;
  display: flex;
  align-items: center;
  justify-content: center;
  transition: background 0.2s ease;
  user-select: none;
}
.lightbox-arrow:hover {
  background: rgba(255, 255, 255, 0.2);
}
.lightbox-arrow-left {
  left: 16px;
  border-radius: 0 8px 8px 0;
}
.lightbox-arrow-right {
  right: 16px;
  border-radius: 8px 0 0 8px;
}

/* 图片容器 */
.lightbox-image-wrap {
  max-width: 85vw;
  max-height: 80vh;
  display: flex;
  align-items: center;
  justify-content: center;
}
.lightbox-image {
  max-width: 100%;
  max-height: 80vh;
  object-fit: contain;
}

/* 底部缩略图 */
.lightbox-thumbnails {
  position: absolute;
  bottom: 24px;
  left: 50%;
  transform: translateX(-50%);
  display: flex;
  gap: 8px;
  max-width: 80vw;
  overflow-x: auto;
  padding: 4px 8px;
  background: rgba(0, 0, 0, 0.4);
  border-radius: 8px;
  backdrop-filter: blur(8px);
}
.lightbox-thumb {
  flex-shrink: 0;
  width: 48px;
  height: 36px;
  border-radius: 4px;
  overflow: hidden;
  cursor: pointer;
  opacity: 0.5;
  transition: opacity 0.2s ease;
  border: 2px solid transparent;
}
.lightbox-thumb:hover {
  opacity: 0.8;
}
.lightbox-thumb.is-active {
  opacity: 1;
  border-color: #fff;
}
.lightbox-thumb img {
  width: 100%;
  height: 100%;
  object-fit: cover;
}
</style>
