<template>
  <div id="addSpacePage">
    <div class="page-header">
      <h2 class="page-title">
        {{ route.query?.id ? '修改' : '创建' }} {{ SPACE_TYPE_MAP[spaceType] }}
      </h2>
      <p class="page-desc">
        {{ route.query?.id ? '修改空间配置' : '开通一个新的空间，开始管理你的图片' }}
      </p>
    </div>

    <!-- 空间信息表单 -->
    <div class="form-card">
      <a-form name="spaceForm" layout="vertical" :model="spaceForm" @finish="handleSubmit">
        <a-form-item name="spaceName" label="空间名称">
          <a-input
            v-model:value="spaceForm.spaceName"
            placeholder="请输入空间名称"
            size="large"
            allow-clear
          />
        </a-form-item>
        <a-form-item name="spaceLevel" label="空间级别">
          <a-select
            v-model:value="spaceForm.spaceLevel"
            placeholder="请选择空间级别"
            size="large"
            :options="SPACE_LEVEL_OPTIONS"
            allow-clear
          />
        </a-form-item>
        <a-form-item>
          <a-button type="primary" html-type="submit" :loading="loading" size="large" block>
            {{ route.query?.id ? '保存修改' : '创建空间' }}
          </a-button>
        </a-form-item>
      </a-form>
    </div>

    <!-- 空间级别介绍 -->
    <div class="level-card">
      <h3 class="level-title">空间级别说明</h3>
      <p class="level-note">目前仅支持开通普通版，如需升级空间，请联系管理员</p>
      <div class="level-list">
        <div v-for="spaceLevel in spaceLevelList" :key="spaceLevel.value" class="level-item">
          <span class="level-name">{{ spaceLevel.text }}</span>
          <span class="level-spec">
            {{ formatSize(spaceLevel.maxSize) }} · 最多 {{ spaceLevel.maxCount }} 张
          </span>
        </div>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue'
import { message } from 'ant-design-vue'
import {
  addSpaceUsingPost,
  getSpaceVoByIdUsingGet,
  listSpaceLevelUsingGet,
  updateSpaceUsingPost,
} from '@/api/spaceController.ts'
import { useRoute, useRouter } from 'vue-router'
import { SPACE_LEVEL_OPTIONS, SPACE_TYPE_ENUM, SPACE_TYPE_MAP } from '@/constants/space.ts'
import { formatSize } from '../utils'

const space = ref<API.SpaceVO>()
const spaceForm = reactive<API.SpaceAddRequest | API.SpaceEditRequest>({})
const loading = ref(false)

const route = useRoute()
const spaceType = computed(() => {
  if (route.query?.type) {
    return Number(route.query.type)
  } else {
    return SPACE_TYPE_ENUM.PRIVATE
  }
})

const spaceLevelList = ref<API.SpaceLevel[]>([])

const fetchSpaceLevelList = async () => {
  const res = await listSpaceLevelUsingGet()
  if (res.data.code === 0 && res.data.data) {
    spaceLevelList.value = res.data.data
  } else {
    message.error('获取空间级别失败，' + res.data.message)
  }
}

onMounted(() => {
  fetchSpaceLevelList()
})

const router = useRouter()

const handleSubmit = async (values: any) => {
  const spaceId = space.value?.id
  loading.value = true
  let res
  if (spaceId) {
    res = await updateSpaceUsingPost({ id: spaceId, ...spaceForm })
  } else {
    res = await addSpaceUsingPost({ ...spaceForm, spaceType: spaceType.value })
  }
  if (res.data.code === 0 && res.data.data) {
    message.success('操作成功')
    router.push({ path: '/admin/spaceManage' })
  } else {
    message.error('操作失败，' + res.data.message)
  }
  loading.value = false
}

const getOldSpace = async () => {
  const id = route.query?.id
  if (id) {
    const res = await getSpaceVoByIdUsingGet({ id })
    if (res.data.code === 0 && res.data.data) {
      const data = res.data.data
      space.value = data
      spaceForm.spaceName = data.spaceName
      spaceForm.spaceLevel = data.spaceLevel
    }
  }
}

onMounted(() => {
  getOldSpace()
})
</script>

<style scoped>
#addSpacePage {
  max-width: 560px;
  margin: 0 auto;
  padding: var(--dt-space-relaxed) var(--dt-space-standard);
}

.page-header {
  text-align: center;
  margin-bottom: var(--dt-space-relaxed);
}
.page-title {
  font-size: 22px;
  font-weight: 700;
  color: var(--dt-color-text-primary);
  margin: 0 0 4px 0;
}
.page-desc {
  font-size: 14px;
  color: var(--dt-color-text-secondary);
  margin: 0;
}

.form-card {
  background: var(--dt-color-bg-container);
  border: 1px solid var(--dt-color-border-default);
  border-radius: var(--dt-radius-xl);
  padding: var(--dt-space-relaxed);
  margin-bottom: var(--dt-space-standard);
  box-shadow: var(--dt-shadow-hover);
}

.level-card {
  background: var(--dt-color-bg-container);
  border: 1px solid var(--dt-color-border-default);
  border-radius: var(--dt-radius-xl);
  padding: var(--dt-space-standard) var(--dt-space-relaxed);
}
.level-title {
  font-size: 15px;
  font-weight: 600;
  color: var(--dt-color-text-primary);
  margin: 0 0 4px 0;
}
.level-note {
  font-size: 13px;
  color: var(--dt-color-text-tertiary);
  margin: 0 0 var(--dt-space-standard) 0;
}
.level-list {
  display: flex;
  flex-direction: column;
  gap: var(--dt-space-tight);
}
.level-item {
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding: 10px 14px;
  background: var(--dt-color-bg-app);
  border-radius: var(--dt-radius-md);
}
.level-name {
  font-size: 14px;
  font-weight: 500;
  color: var(--dt-color-text-primary);
}
.level-spec {
  font-size: 13px;
  color: var(--dt-color-text-tertiary);
}
</style>
