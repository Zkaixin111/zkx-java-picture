<template>
  <div class="picture-list">
    <!-- 骨架屏：初始加载且无数据时显示 shimmer 网格 -->
    <div v-if="loading && dataList.length === 0" class="picture-skeleton-grid">
      <div v-for="n in 12" :key="n" class="skeleton-card">
        <div class="skeleton skeleton-card-cover" />
        <div class="skeleton-card-body">
          <div class="skeleton skeleton-line skeleton-line-title" />
          <div class="skeleton skeleton-line skeleton-line-tag" />
        </div>
      </div>
    </div>
    <!-- 图片列表 -->
    <a-list
      v-else
      :grid="{ gutter: 16, xs: 1, sm: 2, md: 3, lg: 4, xl: 5, xxl: 6 }"
      :data-source="dataList"
      :loading="loading"
    >
      <template #renderItem="{ item: picture, index }">
        <a-list-item style="padding: 0">
          <a-card
            hoverable
            class="image-card"
            :class="{ 'image-card--selected': selectable && selectedIds.has(picture.id) }"
            @click="doClickPicture(picture, index)"
          >
            <template #cover>
              <div class="image-card-cover">
                <img
                  :alt="picture.name"
                  :src="picture.thumbnailUrl ?? picture.url"
                  style="height: 180px; object-fit: cover; width: 100%; display: block"
                  loading="lazy"
                  class="img-fade-in"
                  @load="($event.target as HTMLImageElement).classList.add('is-loaded')"
                />
                <div class="image-card-overlay">
                  <span class="image-card-overlay-text">
                    {{ picture.tags?.length ?? 0 }}个标签
                    <template v-if="picture.picSize"> · {{ formatSize(picture.picSize) }}</template>
                  </span>
                </div>
                <!-- 选中角标 -->
                <div v-if="selectable && selectedIds.has(picture.id)" class="selected-badge">✓</div>
              </div>
            </template>
            <a-card-meta>
              <template #title>
                <a-typography-text ellipsis class="image-card-title">
                  {{ picture.name }}
                </a-typography-text>
              </template>
              <template #description>
                <a-flex>
                  <a-tag color="green">
                    {{ picture.category ?? '默认' }}
                  </a-tag>
                  <template v-if="picture.tags?.length">
                    <a-tag
                      v-for="(tag, idx) in picture.tags"
                      :key="tag"
                      :color="tagColors[idx % tagColors.length]"
                    >
                      {{ tag }}
                    </a-tag>
                  </template>
                  <a-tag v-else class="tag-placeholder">无标签</a-tag>
                </a-flex>
              </template>
            </a-card-meta>
            <template v-if="showOp && !selectable" #actions>
              <ShareAltOutlined
                v-if="picture.userId === loginUserId"
                @click="(e) => doShare(picture, e)"
              />
              <SearchOutlined @click="(e) => doSearch(picture, e)" />
              <EditOutlined
                v-if="canEdit && picture.userId === loginUserId"
                @click="(e) => doEdit(picture, e)"
              />
              <a-popconfirm
                v-if="canDelete && picture.userId === loginUserId"
                title="确定删除该图片？"
                ok-text="确定"
                cancel-text="取消"
                @confirm="() => doDelete(picture)"
              >
                <DeleteOutlined @click.stop />
              </a-popconfirm>
            </template>
          </a-card>
        </a-list-item>
      </template>
    </a-list>
    <ShareModal ref="shareModalRef" :link="shareLink" />
    <PictureLightbox
      ref="lightboxRef"
      :pictures="dataList"
      @close="onLightboxClose"
    />
  </div>
</template>

<script setup lang="ts">
import { useRouter } from 'vue-router'
import {
  DeleteOutlined,
  EditOutlined,
  SearchOutlined,
  ShareAltOutlined,
} from '@ant-design/icons-vue'
import { deletePictureUsingPost } from '@/api/pictureController.ts'
import { message } from 'ant-design-vue'
import ShareModal from '@/components/ShareModal.vue'
import PictureLightbox from '@/components/PictureLightbox.vue'
import { formatSize } from '@/utils'
import { ref } from 'vue'

const tagColors = ['blue', 'purple', 'orange', 'cyan', 'green', 'red']

interface Props {
  dataList?: API.PictureVO[]
  loading?: boolean
  showOp?: boolean
  canEdit?: boolean
  canDelete?: boolean
  loginUserId?: number
  onReload?: () => void
  // 批量选择
  selectable?: boolean
  selectedIds?: Set<number>
  onSelectChange?: (id: number, selected: boolean) => void
}

const props = withDefaults(defineProps<Props>(), {
  dataList: () => [],
  loading: false,
  showOp: false,
  canEdit: false,
  canDelete: false,
  selectable: false,
  selectedIds: () => new Set(),
})

const router = useRouter()
const lightboxRef = ref()

// 点击图片：选择模式下切换选中，否则打开预览
const doClickPicture = (picture: API.PictureVO, index: number) => {
  if (props.selectable) {
    const isSelected = props.selectedIds.has(picture.id)
    props.onSelectChange?.(picture.id, !isSelected)
    return
  }
  lightboxRef.value?.open(index)
}

const onLightboxClose = () => {}

const doSearch = (picture, e) => {
  e.stopPropagation()
  router.push(`/search_picture?pictureId=${picture.id}`)
}

const doEdit = (picture, e) => {
  e.stopPropagation()
  router.push({ path: '/add_picture', query: { id: picture.id, spaceId: picture.spaceId } })
}

const doDelete = async (picture: API.PictureVO, e?: Event) => {
  e?.stopPropagation?.()
  if (!picture.id) return
  const res = await deletePictureUsingPost({ id: picture.id })
  if (res.data.code === 0) {
    message.success('删除成功')
    props.onReload?.()
  } else {
    message.error('删除失败')
  }
}

const shareModalRef = ref()
const shareLink = ref<string>()
const doShare = (picture, e) => {
  e.stopPropagation()
  shareLink.value = `${window.location.protocol}//${window.location.host}/picture/${picture.id}`
  shareModalRef.value?.openModal()
}
</script>

<style scoped>
/* 选中状态：品牌色边框 + 轻微上浮（暗色自适应） */
:deep(.image-card.image-card--selected) {
  border: 2px solid var(--dt-color-accent);
  box-shadow: 0 0 0 2px rgba(26, 115, 232, 0.2);
  transform: translateY(-2px);
  transition: all 0.2s ease;
}
[data-theme='dark'] :deep(.image-card.image-card--selected) {
  box-shadow: 0 0 0 2px rgba(77, 163, 255, 0.25);
}

/* 选中角标 */
.selected-badge {
  position: absolute;
  top: 8px;
  right: 8px;
  width: 24px;
  height: 24px;
  background: var(--dt-color-accent);
  color: #fff;
  border-radius: 50%;
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 14px;
  font-weight: bold;
  z-index: 10;
}

/* 骨架屏网格（初始加载，shimmer 复用 design-tokens .skeleton） */
.picture-skeleton-grid {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(180px, 1fr));
  gap: 16px;
}
.skeleton-card {
  border-radius: var(--dt-radius-xl);
  overflow: hidden;
  border: 1px solid var(--dt-color-border-default);
  background: var(--dt-color-bg-container);
}
.skeleton-card-cover {
  height: 180px;
  width: 100%;
}
.skeleton-card-body {
  padding: 12px;
  display: flex;
  flex-direction: column;
  gap: 8px;
}
.skeleton-line {
  height: 12px;
  border-radius: var(--dt-radius-sm);
}
.skeleton-line-title {
  width: 70%;
}
.skeleton-line-tag {
  width: 40%;
  height: 20px;
}
</style>
