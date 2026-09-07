<template>
  <div id="spaceManagePage">
    <a-flex justify="space-between">
      <h2>空间成员管理</h2>
      <a-space>
        <a-popconfirm
          title="确定删除该空间？"
          ok-text="确定"
          cancel-text="取消"
          @confirm="doDeleteSpace"
        >
          <a-button danger>删除空间</a-button>
        </a-popconfirm>
      </a-space>
    </a-flex>
    <!-- 修改理由：规则5 — 使用标准间距分隔页面区块 -->
<div style="margin-bottom: var(--dt-space-standard)" />
    <!-- 添加成员表单 -->
    <a-form layout="inline" :model="formData" @finish="handleSubmit">
      <a-form-item label="用户 id" name="userId">
        <a-input v-model:value="formData.userId" placeholder="请输入用户 id" allow-clear />
      </a-form-item>
      <a-form-item>
        <a-button type="primary" html-type="submit">添加用户</a-button>
      </a-form-item>
    </a-form>
    <!-- 修改理由：规则5 — 使用标准间距分隔页面区块 -->
<div style="margin-bottom: var(--dt-space-standard)" />
    <!-- 表格 -->
    <a-table :columns="columns" :data-source="dataList">
      <template #bodyCell="{ column, record }">
        <template v-if="column.dataIndex === 'userInfo'">
          <a-space>
            <a-avatar :src="record.user?.userAvatar" />
            {{ record.user?.userName }}
          </a-space>
        </template>
        <template v-if="column.dataIndex === 'spaceRole'">
          <!-- 空间创建者角色固定为管理员，下拉框禁用 -->
          <a-select
            v-if="isSpaceCreator(record)"
            :value="SPACE_ROLE_ENUM.ADMIN"
            :options="SPACE_ROLE_OPTIONS"
            disabled
          />
          <a-select
            v-else
            v-model:value="record.spaceRole"
            :options="SPACE_ROLE_OPTIONS"
            @change="(value) => editSpaceRole(value, record)"
          />
        </template>
        <template v-else-if="column.dataIndex === 'createTime'">
          {{ dayjs(record.createTime).format('YYYY-MM-DD HH:mm:ss') }}
        </template>
        <template v-else-if="column.key === 'action'">
          <a-space wrap>
            <a-button type="link" @click="openDetail(record)">详情</a-button>
            <a-button
              v-if="record.userId !== loginUserStore.loginUser.id && !isSpaceCreator(record)"
              type="link"
              danger
              @click="doDelete(record.id)"
            >
              删除
            </a-button>
          </a-space>
        </template>
      </template>
    </a-table>

    <!-- 成员详情弹窗 -->
    <a-modal
      v-model:visible="detailVisible"
      title="成员详情"
      :footer="null"
      @cancel="detailVisible = false"
    >
      <a-descriptions v-if="currentRecord" :column="1" bordered size="small">
        <a-descriptions-item label="头像">
          <a-avatar :src="currentRecord.user?.userAvatar" :size="48" />
        </a-descriptions-item>
        <a-descriptions-item label="用户 id">{{ currentRecord.userId }}</a-descriptions-item>
        <a-descriptions-item label="账号">{{ currentRecord.user?.userAccount || '-' }}</a-descriptions-item>
        <a-descriptions-item label="昵称">{{ currentRecord.user?.userName || '-' }}</a-descriptions-item>
        <a-descriptions-item label="简介">{{ currentRecord.user?.userProfile || '暂无' }}</a-descriptions-item>
        <a-descriptions-item label="用户角色">
          {{ currentRecord.user?.userRole === 'admin' ? '管理员' : '普通用户' }}
        </a-descriptions-item>
        <a-descriptions-item label="空间角色">
          <a-tag :color="isSpaceCreator(currentRecord) ? 'gold' : 'blue'">
            {{ SPACE_ROLE_MAP[currentRecord.spaceRole] || currentRecord.spaceRole }}
          </a-tag>
        </a-descriptions-item>
        <a-descriptions-item label="加入时间">
          {{ dayjs(currentRecord.createTime).format('YYYY-MM-DD HH:mm:ss') }}
        </a-descriptions-item>
      </a-descriptions>
    </a-modal>
  </div>
</template>
<script lang="ts" setup>
import { onMounted, reactive, ref } from 'vue'
import { message } from 'ant-design-vue'
import { SPACE_ROLE_ENUM, SPACE_ROLE_MAP, SPACE_ROLE_OPTIONS } from '../../constants/space.ts'
import {
  addSpaceUserUsingPost,
  deleteSpaceUserUsingPost,
  editSpaceUserUsingPost,
  listSpaceUserUsingPost,
} from '@/api/spaceUserController.ts'
import { deleteSpaceUsingPost, getSpaceVoByIdUsingGet } from '@/api/spaceController.ts'
import { useLoginUserStore } from '@/stores/useLoginUserStore.ts'
import { useRouter } from 'vue-router'
import dayjs from 'dayjs'

const loginUserStore = useLoginUserStore()
const router = useRouter()

interface Props {
  id: string
}

const props = defineProps<Props>()

const columns = [
  {
    title: '用户',
    dataIndex: 'userInfo',
  },
  {
    title: '角色',
    dataIndex: 'spaceRole',
  },
  {
    title: '创建时间',
    dataIndex: 'createTime',
  },
  {
    title: '操作',
    key: 'action',
  },
]

// 定义数据
const dataList = ref<API.SpaceUserVO[]>([])

// 空间创建者用户 id
const spaceCreatorUserId = ref<number>()

// 判断某成员是否为空间创建者（角色固定为管理员，不可修改/删除）
const isSpaceCreator = (record) => {
  return spaceCreatorUserId.value != null && record.userId === spaceCreatorUserId.value
}

// 获取空间信息（拿创建者 id）
const fetchSpaceDetail = async () => {
  const spaceId = props.id
  if (!spaceId) {
    return
  }
  const res = await getSpaceVoByIdUsingGet({ id: spaceId })
  if (res.data.code === 0 && res.data.data) {
    spaceCreatorUserId.value = res.data.data.userId
  }
}

// 获取数据
const fetchData = async () => {
  const spaceId = props.id
  if (!spaceId) {
    return
  }
  const res = await listSpaceUserUsingPost({
    spaceId,
  })
  if (res.data.code === 0 && res.data.data) {
    // 排序：空间创建者排第一，其余按加入时间升序
    const list = res.data.data ?? []
    dataList.value = list.sort((a, b) => {
      const aIsCreator = spaceCreatorUserId.value != null && a.userId === spaceCreatorUserId.value
      const bIsCreator = spaceCreatorUserId.value != null && b.userId === spaceCreatorUserId.value
      if (aIsCreator && !bIsCreator) return -1
      if (!aIsCreator && bIsCreator) return 1
      return new Date(a.createTime).getTime() - new Date(b.createTime).getTime()
    })
  } else {
    message.error('获取数据失败，' + res.data.message)
  }
}

// 页面加载时获取数据，请求一次
onMounted(async () => {
  // 先拿创建者 id（排序依赖它），再加载成员列表
  await fetchSpaceDetail()
  fetchData()
})

// 添加成员表单
const formData = reactive<API.SpaceUserAddRequest>({})

// 创建成员
const handleSubmit = async () => {
  const spaceId = props.id
  if (!spaceId) {
    return
  }
  const res = await addSpaceUserUsingPost({
    spaceId,
    ...formData,
  })
  if (res.data.code === 0) {
    message.success('添加成功')
    // 刷新数据
    fetchData()
  } else {
    message.error('添加失败，' + res.data.message)
  }
}

// ---- 成员详情弹窗 ----
const detailVisible = ref(false)
const currentRecord = ref<API.SpaceUserVO>()

// 打开详情弹窗
const openDetail = (record: API.SpaceUserVO) => {
  currentRecord.value = record
  detailVisible.value = true
}

// 编辑成员角色
const editSpaceRole = async (value, record) => {
  const res = await editSpaceUserUsingPost({
    id: record.id,
    spaceRole: value,
  })
  if (res.data.code === 0) {
    message.success('修改成功')
  } else {
    message.error('修改失败，' + res.data.message)
  }
}

// 删除空间
const doDeleteSpace = async () => {
  const spaceId = props.id
  if (!spaceId) {
    return
  }
  const res = await deleteSpaceUsingPost({ id: spaceId })
  if (res.data.code === 0) {
    message.success('空间已删除')
    router.push('/')
  } else {
    message.error('删除空间失败，' + res.data.message)
  }
}

// 删除成员
const doDelete = async (id: string) => {
  if (!id) {
    return
  }
  const res = await deleteSpaceUserUsingPost({ id })
  if (res.data.code === 0) {
    message.success('删除成功')
    // 刷新数据
    fetchData()
  } else {
    message.error('删除失败')
  }
}
</script>
