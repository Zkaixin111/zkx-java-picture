import { onMounted, onUnmounted } from 'vue'

/**
 * 全局键盘快捷键
 * - / 聚焦搜索框
 * - Escape 关闭任何打开的模态框/Lightbox（由各组件自行处理）
 */
export function useGlobalShortcuts() {
  const onKeydown = (e: KeyboardEvent) => {
    // 不在输入框内时，按 / 聚焦搜索
    if (
      e.key === '/' &&
      !isInputFocused() &&
      !e.ctrlKey &&
      !e.metaKey &&
      !e.altKey
    ) {
      e.preventDefault()
      const searchInput = document.querySelector<HTMLInputElement>(
        '.ant-input-search input, [data-search-focus]'
      )
      searchInput?.focus()
    }
  }

  onMounted(() => {
    window.addEventListener('keydown', onKeydown)
  })

  onUnmounted(() => {
    window.removeEventListener('keydown', onKeydown)
  })
}

function isInputFocused(): boolean {
  const tag = document.activeElement?.tagName.toLowerCase()
  const isEditable = document.activeElement?.getAttribute('contenteditable') === 'true'
  return tag === 'input' || tag === 'textarea' || tag === 'select' || isEditable
}
