<template>
  <div id="spaceDetailPage">
    <!-- 空间信息头部卡片 -->
    <div class="space-header-card">
      <div class="space-header-bar" />
      <a-flex justify="space-between" align="center" class="space-header-inner" wrap>
        <h2 class="space-title">
          {{ space.spaceName }}
          <span class="space-type-chip">{{ SPACE_TYPE_MAP[space.spaceType] }}</span>
        </h2>
        <a-space size="middle" class="header-actions" wrap>
        <a-button
          v-if="canUploadPicture && !selectMode"
          type="primary"
          @click="router.push(`/add_picture?spaceId=${id}`)"
        >
          + 创建图片
        </a-button>
        <a-button
          v-if="canManageSpaceUser && space.spaceType !== SPACE_TYPE_ENUM.PRIVATE && !selectMode"
          type="primary"
          ghost
          :icon="h(TeamOutlined)"
          @click="router.push(`/spaceUserManage/${id}`)"
        >
          成员管理
        </a-button>
        <a-button
          v-if="canManageSpaceUser && !selectMode"
          type="primary"
          ghost
          :icon="h(BarChartOutlined)"
          @click="router.push(`/space_analyze?spaceId=${id}`)"
        >
          空间分析
        </a-button>
        <!-- 正常模式：点"批量编辑"进入选择模式 -->
        <template v-if="canEditPicture && !selectMode">
          <a-button :icon="h(EditOutlined)" @click="enterSelectMode">
            批量编辑
          </a-button>
        </template>
        <!-- 选择模式：显示选中数 + 确认/取消 -->
        <template v-if="canEditPicture && selectMode">
          <span style="color: #1890ff; font-weight: 500">
            已选 {{ selectedIds.size }} 张
          </span>
          <a-button size="small" @click="selectAllCurrentPage">全选本页</a-button>
          <a-button type="primary" :disabled="selectedIds.size === 0" @click="doBatchEdit">
            确认编辑
          </a-button>
          <a-button @click="exitSelectMode">取消</a-button>
        </template>
        <!-- 团队空间聊天入口 -->
        <a-button
          v-if="space.spaceType === SPACE_TYPE_ENUM.TEAM"
          :icon="h(MessageOutlined)"
          @click="chatVisible = true"
        >
          聊天
        </a-button>
        <a-tooltip
          :title="`占用空间 ${formatSize(space.totalSize)} / ${formatSize(space.maxSize)}`"
        >
          <a-progress
            type="circle"
            :size="42"
            :percent="((space.totalSize * 100) / space.maxSize).toFixed(1)"
          />
        </a-tooltip>
      </a-space>
      </a-flex>
    </div>
    <!-- 搜索表单 -->
    <PictureSearchForm :onSearch="onSearch" />
    <!-- 修改理由：规则5 — 搜索区与颜色筛选区为等层级元素，使用标准间距 -->
    <div style="margin-bottom: var(--dt-space-standard)" />
    <!-- 按颜色搜索，跟其他搜索条件独立 -->
    <a-form-item label="按颜色搜索">
      <color-picker format="hex" @pureColorChange="onColorChange" />
    </a-form-item>
    <!-- 图片列表 -->
    <PictureList
      :dataList="dataList"
      :loading="loading"
      :showOp="true"
      :canEdit="canEditPicture"
      :canDelete="canDeletePicture"
      :loginUserId="loginUserStore.loginUser.id"
      :onReload="fetchData"
      :selectable="selectMode"
      :selectedIds="selectedIds"
      :onSelectChange="onSelectChange"
    />
    <!-- 分页 -->
    <a-pagination
      style="text-align: right"
      v-model:current="searchParams.current"
      v-model:pageSize="searchParams.pageSize"
      :total="total"
      @change="onPageChange"
    />
    <BatchEditPictureModal
      ref="batchEditPictureModalRef"
      :spaceId="id"
      :pictureIdList="Array.from(selectedIds)"
      :onSuccess="onBatchEditPictureSuccess"
    />
    <!-- 团队空间聊天抽屉 -->
    <a-drawer
      v-model:visible="chatVisible"
      title="团队聊天"
      placement="right"
      :width="360"
      destroy-on-close
    >
      <SpaceChatPanel v-if="chatVisible && space.spaceType === SPACE_TYPE_ENUM.TEAM" :spaceId="String(id)" />
    </a-drawer>
  </div>
</template>

<script setup lang="ts">
import { computed, h, onMounted, ref, watch } from 'vue'
import { useRouter } from 'vue-router'
import { getSpaceVoByIdUsingGet } from '@/api/spaceController.ts'
import { message } from 'ant-design-vue'
import {
  listPictureVoByPageUsingPost,
  searchPictureByColorUsingPost,
} from '@/api/pictureController.ts'
import { formatSize } from '@/utils'
import PictureList from '@/components/PictureList.vue'
import PictureSearchForm from '@/components/PictureSearchForm.vue'
import { ColorPicker } from 'vue3-colorpicker'
import 'vue3-colorpicker/style.css'
import BatchEditPictureModal from '@/components/BatchEditPictureModal.vue'
import SpaceChatPanel from '@/components/SpaceChatPanel.vue'
import { BarChartOutlined, EditOutlined, MessageOutlined, TeamOutlined } from '@ant-design/icons-vue'
import { SPACE_PERMISSION_ENUM, SPACE_TYPE_ENUM, SPACE_TYPE_MAP } from '../constants/space.ts'
import { useLoginUserStore } from '@/stores/useLoginUserStore.ts'

interface Props {
  id: string | number
}

const props = defineProps<Props>()
const loginUserStore = useLoginUserStore()
const router = useRouter()
const space = ref<API.SpaceVO>({})

// 聊天抽屉显隐
const chatVisible = ref(false)

// 通用权限检查函数
function createPermissionChecker(permission: string) {
  return computed(() => {
    return (space.value.permissionList ?? []).includes(permission)
  })
}

// 定义权限检查
const canManageSpaceUser = createPermissionChecker(SPACE_PERMISSION_ENUM.SPACE_USER_MANAGE)
const canUploadPicture = createPermissionChecker(SPACE_PERMISSION_ENUM.PICTURE_UPLOAD)
const canEditPicture = createPermissionChecker(SPACE_PERMISSION_ENUM.PICTURE_EDIT)
const canDeletePicture = createPermissionChecker(SPACE_PERMISSION_ENUM.PICTURE_DELETE)

// -------- 获取空间详情 --------
const fetchSpaceDetail = async () => {
  try {
    const res = await getSpaceVoByIdUsingGet({
      id: props.id,
    })
    if (res.data.code === 0 && res.data.data) {
      space.value = res.data.data
    } else {
      message.error('获取空间详情失败，' + res.data.message)
    }
  } catch (e: any) {
    message.error('获取空间详情失败：' + e.message)
  }
}

onMounted(() => {
  fetchSpaceDetail()
})

// --------- 获取图片列表 --------

// 定义数据
const dataList = ref<API.PictureVO[]>([])
const total = ref(0)
const loading = ref(true)

// 搜索条件
const searchParams = ref<API.PictureQueryRequest>({
  current: 1,
  pageSize: 12,
  sortField: 'createTime',
  sortOrder: 'descend',
})

// 获取数据
const fetchData = async () => {
  loading.value = true
  // 转换搜索参数
  const params = {
    spaceId: props.id,
    ...searchParams.value,
  }
  const res = await listPictureVoByPageUsingPost(params)
  if (res.data.code === 0 && res.data.data) {
    dataList.value = res.data.data.records ?? []
    total.value = Number(res.data.data.total) || 0
  } else {
    message.error('获取数据失败，' + res.data.message)
  }
  loading.value = false
}

// 页面加载时获取数据，请求一次
onMounted(() => {
  fetchData()
})

// 分页参数
const onPageChange = (page: number, pageSize: number) => {
  searchParams.value.current = page
  searchParams.value.pageSize = pageSize
  fetchData()
}

// 搜索
const onSearch = (newSearchParams: API.PictureQueryRequest) => {
  console.log('new', newSearchParams)

  searchParams.value = {
    ...searchParams.value,
    ...newSearchParams,
    current: 1,
  }
  console.log('searchparams', searchParams.value)
  fetchData()
}

// 按照颜色搜索
const onColorChange = async (color: string) => {
  loading.value = true
  const res = await searchPictureByColorUsingPost({
    picColor: color,
    spaceId: props.id,
  })
  if (res.data.code === 0 && res.data.data) {
    const data = res.data.data ?? []
    dataList.value = data
    total.value = data.length
  } else {
    message.error('获取数据失败，' + res.data.message)
  }
  loading.value = false
}

// ---- 批量编辑图片 -----
const batchEditPictureModalRef = ref()

// 选择模式
const selectMode = ref(false)
const selectedIds = ref<Set<number>>(new Set())

// 进入选择模式
const enterSelectMode = () => {
  selectMode.value = true
  selectedIds.value = new Set()
}

// 退出选择模式
const exitSelectMode = () => {
  selectMode.value = false
  selectedIds.value = new Set()
}

// 单个图片选中/取消
const onSelectChange = (id: number, selected: boolean) => {
  if (selected) {
    selectedIds.value.add(id)
  } else {
    selectedIds.value.delete(id)
  }
  selectedIds.value = new Set(selectedIds.value)
}

// 全选当前页
const selectAllCurrentPage = () => {
  dataList.value.forEach((p) => selectedIds.value.add(p.id))
  selectedIds.value = new Set(selectedIds.value)
}

// 批量编辑图片成功
const onBatchEditPictureSuccess = () => {
  fetchData()
  exitSelectMode()
}

// 确认编辑 → 打开弹窗
const doBatchEdit = () => {
  if (selectedIds.value.size === 0) {
    message.warning('请先选择要编辑的图片')
    return
  }
  batchEditPictureModalRef.value?.openModal()
}

// 空间 id 改变时，必须重新获取数据
watch(
  () => props.id,
  (newSpaceId) => {
    fetchSpaceDetail()
    fetchData()
  },
)
</script>

<style scoped>
#spaceDetailPage {
  margin-bottom: var(--dt-space-standard);
}
#spaceDetailPage .header-actions {
  padding-right: var(--dt-space-relaxed);
}

/* 空间信息头部卡片：玻璃拟态容器 */
.space-header-card {
  position: relative;
  background: var(--dt-color-bg-container);
  border: 1px solid var(--dt-color-border-default);
  border-radius: var(--dt-radius-xl);
  padding: var(--dt-space-relaxed) var(--dt-space-relaxed) var(--dt-space-relaxed)
    calc(var(--dt-space-relaxed) + 6px);
  box-shadow: var(--dt-shadow-elevated);
  margin-bottom: var(--dt-space-standard);
  overflow: hidden;
}

/* 左侧品牌渐变装饰条 */
.space-header-bar {
  position: absolute;
  left: 0;
  top: 0;
  bottom: 0;
  width: 6px;
  background: linear-gradient(180deg, var(--dt-color-accent), var(--dt-color-accent-hover));
}

.space-header-inner {
  gap: var(--dt-space-standard);
}

.space-title {
  display: flex;
  align-items: center;
  gap: var(--dt-space-tight);
  margin: 0;
  font-size: 22px;
  font-weight: 700;
  color: var(--dt-color-text-primary);
}

/* 类型标签 chip */
.space-type-chip {
  font-size: 12px;
  font-weight: 500;
  padding: 2px 10px;
  border-radius: 10px;
  background: rgba(26, 115, 232, 0.1);
  color: var(--dt-color-accent);
  border: 1px solid transparent;
}

[data-theme='dark'] .space-type-chip {
  background: rgba(77, 163, 255, 0.15);
}

/* 移动端：操作区换行后左对齐，避免拥挤 */
@media (max-width: 768px) {
  .space-header-inner {
    flex-direction: column;
    align-items: flex-start;
  }
  .space-title {
    font-size: 19px;
  }
}
</style>
