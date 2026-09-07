<template>
  <div id="homePage">
    <!-- Hero 区 -->
    <section class="hero">
      <div class="hero-content">
        <h1 class="hero-title">拾光云图</h1>
        <p class="hero-subtitle">发现、收藏、分享你热爱的每一张图片</p>
        <div class="hero-search">
          <a-input-search
            v-model:value="searchParams.searchText"
            placeholder="搜索图片关键词..."
            enter-button="搜索"
            size="large"
            @search="doSearch"
          />
        </div>
      </div>
    </section>

    <!-- 筛选区 -->
    <section class="filter-section">
      <!-- 分类标签页 -->
      <div class="category-row">
        <a-tabs
          v-model:active-key="selectedCategory"
          @change="doSearch"
          :animated="false"
        >
          <a-tab-pane key="all" tab="全部" />
          <a-tab-pane
            v-for="category in categoryList"
            :tab="category"
            :key="category"
          />
        </a-tabs>
      </div>

      <!-- 标签筛选栏 -->
      <div v-if="tagList.length > 0" class="tag-row">
        <span class="tag-row-label">热门标签</span>
        <a-space :size="[0, 8]" wrap class="tag-row-list">
          <a-checkable-tag
            v-for="(tag, index) in tagList"
            :key="tag"
            v-model:checked="selectedTagList[index]"
            @change="doSearch"
          >
            {{ tag }}
          </a-checkable-tag>
        </a-space>
        <a-button
          v-if="loginUserStore.loginUser.id"
          class="my-uploads-btn"
          :type="showMyUploads ? 'primary' : 'default'"
          size="small"
          @click="toggleMyUploads"
        >
          {{ showMyUploads ? '我的上传' : '仅看我的' }}
        </a-button>
      </div>
    </section>

    <!-- 图片列表 -->
    <section class="gallery-section">
      <PictureList
        :dataList="dataList"
        :loading="loading"
        :showOp="true"
        :canDelete="true"
        :loginUserId="loginUserStore.loginUser.id"
        :onReload="fetchData"
      />
      <!-- 空状态 -->
      <div v-if="!loading && dataList.length === 0" class="empty-state">
        <div class="empty-icon">
          <picture-outlined />
        </div>
        <p class="empty-title">暂无图片</p>
        <p class="empty-desc">尝试更换搜索词或清除筛选条件</p>
      </div>
    </section>

    <!-- 分页 -->
    <div v-if="total > 0" class="pagination-row">
      <a-pagination
        v-model:current="searchParams.current"
        v-model:pageSize="searchParams.pageSize"
        :total="total"
        :show-size-changer="false"
        @change="onPageChange"
      />
    </div>
  </div>
</template>

<script setup lang="ts">
import { onMounted, reactive, ref, h } from 'vue'
import {
  listPictureTagCategoryUsingGet,
  listPictureVoByPageUsingPost,
} from '@/api/pictureController.ts'
import { message } from 'ant-design-vue'
import { PictureOutlined } from '@ant-design/icons-vue'
import PictureList from '@/components/PictureList.vue'
import { useLoginUserStore } from '@/stores/useLoginUserStore.ts'

const loginUserStore = useLoginUserStore()

// "我的上传"切换
const showMyUploads = ref(false)
const toggleMyUploads = () => {
  showMyUploads.value = !showMyUploads.value
  const rawId = loginUserStore.loginUser.id
  searchParams.userId = showMyUploads.value && rawId ? rawId : undefined
  doSearch()
}

// 定义数据
const dataList = ref<API.PictureVO[]>([])
const total = ref(0)
const loading = ref(true)

// 搜索条件
const searchParams = reactive<API.PictureQueryRequest>({
  current: 1,
  pageSize: 15,
  sortField: 'createTime',
  sortOrder: 'descend',
})

// 获取数据
const fetchData = async () => {
  loading.value = true
  const params = {
    ...searchParams,
    tags: [] as string[],
  }
  if (selectedCategory.value !== 'all') {
    params.category = selectedCategory.value
  }
  selectedTagList.value.forEach((useTag, index) => {
    if (useTag) {
      params.tags.push(tagList.value[index])
    }
  })
  const res = await listPictureVoByPageUsingPost(params)
  if (res.data.code === 0 && res.data.data) {
    dataList.value = res.data.data.records ?? []
    total.value = Number(res.data.data.total) || 0
  } else {
    message.error('获取数据失败，' + res.data.message)
  }
  loading.value = false
}

onMounted(() => {
  fetchData()
})

const onPageChange = (page: number, pageSize: number) => {
  searchParams.current = page
  searchParams.pageSize = pageSize
  fetchData()
}

const doSearch = () => {
  searchParams.current = 1
  fetchData()
}

const categoryList = ref<string[]>([])
const selectedCategory = ref<string>('all')
const tagList = ref<string[]>([])
const selectedTagList = ref<boolean[]>([])

const getTagCategoryOptions = async () => {
  const res = await listPictureTagCategoryUsingGet()
  if (res.data.code === 0 && res.data.data) {
    tagList.value = res.data.data.tagList ?? []
    categoryList.value = res.data.data.categoryList ?? []
  } else {
    message.error('获取标签分类列表失败，' + res.data.message)
  }
}

onMounted(() => {
  getTagCategoryOptions()
})
</script>

<style scoped>
/* ========== Hero 区 ========== */
.hero {
  background: linear-gradient(160deg, #e8f0fe 0%, #f5f6f8 60%, #ffffff 100%);
  border-radius: var(--dt-radius-xl);
  padding: var(--dt-space-x-loose) var(--dt-space-standard);
  margin-bottom: var(--dt-space-standard);
  text-align: center;
  position: relative;
  overflow: hidden;
}
/* 背景装饰光斑 */
.hero::before {
  content: '';
  position: absolute;
  top: -60px;
  right: -80px;
  width: 260px;
  height: 260px;
  background: radial-gradient(circle, rgba(26, 115, 232, 0.08) 0%, transparent 70%);
  border-radius: 50%;
  pointer-events: none;
}
.hero::after {
  content: '';
  position: absolute;
  bottom: -40px;
  left: -60px;
  width: 200px;
  height: 200px;
  background: radial-gradient(circle, rgba(26, 115, 232, 0.06) 0%, transparent 70%);
  border-radius: 50%;
  pointer-events: none;
}
.hero-content {
  position: relative;
  z-index: 1;
  max-width: 560px;
  margin: 0 auto;
}
.hero-title {
  font-size: 32px;
  font-weight: 700;
  letter-spacing: -0.02em;
  color: var(--dt-color-text-primary);
  margin: 0 0 8px 0;
  line-height: 1.2;
}
.hero-subtitle {
  font-size: 15px;
  color: var(--dt-color-text-secondary);
  margin: 0 0 24px 0;
  line-height: 1.5;
}
.hero-search {
  margin-bottom: 16px;
}
.hero-search :deep(.ant-input-search) {
  max-width: 440px;
  margin: 0 auto;
}
.hero-search :deep(.ant-input-affix-wrapper),
.hero-search :deep(.ant-input-group .ant-input:first-child) {
  border-radius: var(--dt-radius-lg) 0 0 var(--dt-radius-lg);
}
.hero-search :deep(.ant-input-search .ant-input-group .ant-btn) {
  border-radius: 0 var(--dt-radius-lg) var(--dt-radius-lg) 0;
}

/* 暗色模式 Hero */
[data-theme='dark'] .hero {
  background: linear-gradient(160deg, #141824 0%, #0d0f12 60%, #111318 100%);
}

/* ========== 筛选区 ========== */
.filter-section {
  margin-bottom: var(--dt-space-standard);
}
.category-row :deep(.ant-tabs) {
  margin-bottom: 0;
}
.category-row :deep(.ant-tabs-nav) {
  margin-bottom: var(--dt-space-tight);
}
.category-row :deep(.ant-tabs-tab) {
  padding: 6px 16px;
  font-size: 14px;
  color: var(--dt-color-text-secondary);
  transition: color var(--dt-transition-fast);
}
.category-row :deep(.ant-tabs-tab-active .ant-tabs-tab-btn) {
  color: var(--dt-color-accent);
  font-weight: 600;
}
.category-row :deep(.ant-tabs-ink-bar) {
  background: var(--dt-color-accent);
  height: 2px;
  border-radius: 1px;
}

.tag-row {
  display: flex;
  align-items: center;
  gap: 10px;
  padding: 10px 0;
  border-top: 1px solid var(--dt-color-border-default);
}
.tag-row-label {
  font-size: 13px;
  color: var(--dt-color-text-tertiary);
  white-space: nowrap;
  flex-shrink: 0;
}
.tag-row-list {
  flex: 1;
  min-width: 0;
}
.my-uploads-btn {
  flex-shrink: 0;
  margin-left: auto;
}

/* ========== 图片展示区 ========== */
.gallery-section {
  min-height: 200px;
}

/* ========== 空状态 ========== */
.empty-state {
  text-align: center;
  padding: var(--dt-space-x-loose) var(--dt-space-standard);
}
.empty-icon {
  font-size: 48px;
  color: var(--dt-color-border-default);
  margin-bottom: var(--dt-space-standard);
}
.empty-title {
  font-size: 16px;
  font-weight: 600;
  color: var(--dt-color-text-primary);
  margin: 0 0 8px 0;
}
.empty-desc {
  font-size: 14px;
  color: var(--dt-color-text-tertiary);
  margin: 0;
}

/* ========== 分页 ========== */
.pagination-row {
  display: flex;
  justify-content: center;
  padding: var(--dt-space-standard) 0;
}

/* ========== 响应式 ========== */
@media (max-width: 640px) {
  .hero {
    padding: var(--dt-space-loose) var(--dt-space-standard);
    border-radius: var(--dt-radius-lg);
  }
  .hero-title {
    font-size: 24px;
  }
  .hero-subtitle {
    font-size: 14px;
  }
  .hero-search :deep(.ant-input-search) {
    max-width: 100%;
  }
  .tag-row {
    flex-wrap: wrap;
  }
}
</style>
