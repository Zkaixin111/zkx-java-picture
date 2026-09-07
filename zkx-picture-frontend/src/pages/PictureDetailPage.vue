<template>
  <div id="pictureDetailPage">
    <div class="detail-layout">
      <!-- 左侧：图片预览区（全屏沉浸） -->
      <div class="detail-preview">
        <div class="preview-bg">
          <img
            :src="picture.url"
            :alt="picture.name"
            class="preview-image"
            loading="lazy"
          />
        </div>
        <!-- 图片下方信息条 -->
        <div class="preview-info-bar">
          <a-space>
            <a-avatar :size="32" :src="picture.user?.userAvatar" />
            <div>
              <div class="preview-author">{{ picture.user?.userName ?? '未知作者' }}</div>
              <div class="preview-name">{{ picture.name ?? '未命名' }}</div>
            </div>
          </a-space>
          <a-space size="small">
            <a-button type="primary" @click="doDownload">
              <template #icon><DownloadOutlined /></template>
              下载
            </a-button>
            <a-button :icon="h(ShareAltOutlined)" ghost @click="doShare">分享</a-button>
          </a-space>
        </div>
      </div>

      <!-- 右侧：元数据面板 -->
      <div class="detail-panel">
        <div class="panel-inner">
          <!-- 操作按钮组 -->
          <div class="panel-actions">
            <a-space direction="vertical" style="width: 100%">
              <a-button type="primary" block @click="doDownload">
                <template #icon><DownloadOutlined /></template>
                免费下载原图
              </a-button>
              <a-button block :icon="h(ShareAltOutlined)" @click="doShare">分享图片</a-button>
              <a-button v-if="canEdit" block :icon="h(EditOutlined)" @click="doEdit">编辑图片</a-button>
              <a-popconfirm
                v-if="canDelete"
                title="确定删除该图片？"
                ok-text="确定"
                cancel-text="取消"
                @confirm="doDelete"
              >
                <a-button block danger :icon="h(DeleteOutlined)">删除图片</a-button>
              </a-popconfirm>
            </a-space>
          </div>

          <!-- 图片元数据 -->
          <div class="panel-section">
            <div class="panel-label">图片信息</div>
            <div class="meta-grid">
              <div class="meta-item">
                <span class="meta-key">格式</span>
                <span class="meta-value">{{ picture.picFormat?.toUpperCase() ?? '-' }}</span>
              </div>
              <div class="meta-item">
                <span class="meta-key">尺寸</span>
                <span class="meta-value">{{ picture.picWidth }} × {{ picture.picHeight }}</span>
              </div>
              <div class="meta-item">
                <span class="meta-key">宽高比</span>
                <span class="meta-value">{{ picture.picScale ?? '-' }}</span>
              </div>
              <div class="meta-item">
                <span class="meta-key">大小</span>
                <span class="meta-value">{{ formatSize(picture.picSize) }}</span>
              </div>
              <div class="meta-item">
                <span class="meta-key">主色调</span>
                <span class="meta-value">
                  <span class="color-swatch" :style="{ background: toHexColor(picture.picColor ?? '') }" />
                  {{ picture.picColor ?? '-' }}
                </span>
              </div>
              <div class="meta-item">
                <span class="meta-key">分类</span>
                <span class="meta-value">{{ picture.category ?? '默认' }}</span>
              </div>
            </div>
          </div>

          <!-- 标签 -->
          <div class="panel-section">
            <div class="panel-label">标签</div>
            <a-flex wrap gap="4">
              <template v-if="picture.tags?.length">
                <a-tag
                  v-for="(tag, idx) in picture.tags"
                  :key="tag"
                  :color="['blue','purple','orange','cyan','green','red'][idx % 6]"
                >{{ tag }}</a-tag>
              </template>
              <a-tag v-else class="tag-placeholder">无标签</a-tag>
            </a-flex>
          </div>

          <!-- 简介 -->
          <div class="panel-section" v-if="picture.introduction">
            <div class="panel-label">简介</div>
            <p class="panel-intro">{{ picture.introduction }}</p>
          </div>
        </div>
      </div>
    </div>

    <ShareModal ref="shareModalRef" :link="shareLink" />
  </div>
</template>

<script setup lang="ts">
import { computed, h, onMounted, ref } from 'vue'
import { deletePictureUsingPost, getPictureVoByIdUsingGet } from '@/api/pictureController.ts'
import { message } from 'ant-design-vue'
import {
  DeleteOutlined,
  DownloadOutlined,
  EditOutlined,
  ShareAltOutlined,
} from '@ant-design/icons-vue'
import { useRouter } from 'vue-router'
import { downloadImage, formatSize, toHexColor } from '@/utils'
import ShareModal from '@/components/ShareModal.vue'
import { SPACE_PERMISSION_ENUM } from '@/constants/space.ts'

interface Props {
  id: string | number
}

const props = defineProps<Props>()
const picture = ref<API.PictureVO>({})

function createPermissionChecker(permission: string) {
  return computed(() => {
    return (picture.value.permissionList ?? []).includes(permission)
  })
}

const canEdit = createPermissionChecker(SPACE_PERMISSION_ENUM.PICTURE_EDIT)
const canDelete = createPermissionChecker(SPACE_PERMISSION_ENUM.PICTURE_DELETE)

const fetchPictureDetail = async () => {
  try {
    const res = await getPictureVoByIdUsingGet({ id: props.id })
    if (res.data.code === 0 && res.data.data) {
      picture.value = res.data.data
    } else {
      message.error('获取图片详情失败，' + res.data.message)
    }
  } catch (e: any) {
    message.error('获取图片详情失败：' + e.message)
  }
}

onMounted(() => {
  fetchPictureDetail()
})

const router = useRouter()

const doEdit = () => {
  router.push({
    path: '/add_picture',
    query: { id: picture.value.id, spaceId: picture.value.spaceId },
  })
}

const doDelete = async () => {
  const id = picture.value.id
  if (!id) return
  const res = await deletePictureUsingPost({ id })
  if (res.data.code === 0) {
    message.success('删除成功')
  } else {
    message.error('删除失败')
  }
}

const doDownload = () => {
  downloadImage(picture.value.url)
}

const shareModalRef = ref()
const shareLink = ref<string>()

const doShare = () => {
  shareLink.value = `${window.location.protocol}//${window.location.host}/picture/${picture.value.id}`
  if (shareModalRef.value) {
    shareModalRef.value.openModal()
  }
}
</script>

<style scoped>
#pictureDetailPage {
  min-height: calc(100vh - 65px);
  background: var(--dt-color-bg-app);
}

.detail-layout {
  display: flex;
  min-height: calc(100vh - 65px);
}

/* ---- 左侧预览区 ---- */
.detail-preview {
  flex: 1;
  display: flex;
  flex-direction: column;
  min-width: 0;
}

.preview-bg {
  flex: 1;
  display: flex;
  align-items: center;
  justify-content: center;
  background: var(--dt-color-bg-container);
  padding: 32px;
  position: relative;
}

/* 暗色下预览背景更深，突出图片色彩 */
[data-theme='dark'] .preview-bg {
  background: #0a0c10;
}

.preview-image {
  max-width: 100%;
  max-height: 65vh;
  object-fit: contain;
}

.preview-info-bar {
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding: 16px 32px;
  border-top: 1px solid var(--dt-color-border-default);
}

.preview-author {
  font-weight: 600;
  font-size: 14px;
  color: var(--dt-color-text-primary);
}

.preview-name {
  font-size: 13px;
  color: var(--dt-color-text-secondary);
  margin-top: 2px;
}

/* ---- 右侧面板 ---- */
.detail-panel {
  width: 320px;
  flex-shrink: 0;
  border-left: 1px solid var(--dt-color-border-default);
  background: var(--dt-color-bg-container);
  overflow-y: auto;
}

.panel-inner {
  padding: 24px;
}

.panel-actions {
  margin-bottom: 24px;
}

.panel-section {
  margin-bottom: 20px;
}

.panel-label {
  font-size: 12px;
  font-weight: 600;
  text-transform: uppercase;
  letter-spacing: 0.5px;
  color: var(--dt-color-text-tertiary);
  margin-bottom: 10px;
}

.panel-intro {
  font-size: 14px;
  line-height: 1.6;
  color: var(--dt-color-text-secondary);
  margin: 0;
}

/* 元数据网格 */
.meta-grid {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 12px 16px;
}

.meta-item {
  display: flex;
  flex-direction: column;
  gap: 2px;
}

.meta-key {
  font-size: 11px;
  color: var(--dt-color-text-tertiary);
  text-transform: uppercase;
  letter-spacing: 0.3px;
}

.meta-value {
  font-size: 13px;
  color: var(--dt-color-text-primary);
  display: flex;
  align-items: center;
  gap: 6px;
}

.color-swatch {
  display: inline-block;
  width: 14px;
  height: 14px;
  border-radius: 3px;
  border: 1px solid var(--dt-color-border-default);
}

/* ---- 响应式 ---- */
@media (max-width: 768px) {
  .detail-layout {
    flex-direction: column;
  }
  .detail-panel {
    width: 100%;
    border-left: none;
    border-top: 1px solid var(--dt-color-border-default);
  }
  .preview-bg {
    padding: 16px;
  }
  .preview-image {
    max-height: 50vh;
  }
  .preview-info-bar {
    padding: 12px 16px;
    flex-direction: column;
    gap: 12px;
  }
}
</style>
