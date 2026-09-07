<template>
  <div id="globalSider">
    <a-layout-sider
      v-if="loginUserStore.loginUser.id"
      width="200"
      breakpoint="lg"
      collapsed-width="0"
    >
      <a-menu
        v-model:selectedKeys="current"
        mode="inline"
        :items="menuItems"
        @click="doMenuClick"
      />
    </a-layout-sider>
  </div>
</template>
<script lang="ts" setup>
import { computed, h, ref, watchEffect } from 'vue'
import { BarChartOutlined, PictureOutlined, TeamOutlined, UserOutlined } from '@ant-design/icons-vue'
import { useRouter } from 'vue-router'
import { useLoginUserStore } from '@/stores/useLoginUserStore.ts'
import { SPACE_TYPE_ENUM } from '@/constants/space.ts'
import { listMyTeamSpaceUsingPost } from '@/api/spaceUserController.ts'
import { message } from 'ant-design-vue'

const loginUserStore = useLoginUserStore()

// 固定的菜单列表
const fixedMenuItems = [
  {
    key: '/',
    icon: () => h(PictureOutlined),
    label: '公共图库',
  },
  {
    key: '/my_space',
    label: '我的空间',
    icon: () => h(UserOutlined),
  },
]

const teamSpaceList = ref<API.SpaceUserVO[]>([])
const menuItems = computed(() => {
  // 管理员才显示的分析菜单
  const isAdmin = loginUserStore.loginUser.userRole === 'admin'
  const analyzeMenuItems = isAdmin
    ? [
        {
          key: '/space_analyze?queryPublic=1',
          icon: () => h(BarChartOutlined),
          label: '分析公共图库',
        },
        {
          key: '/space_analyze?queryAll=1',
          icon: () => h(BarChartOutlined),
          label: '分析全部空间',
        },
      ]
    : []

  // 无团队空间时，展示创建团队选项
  if (teamSpaceList.value.length < 1) {
    return [
      ...fixedMenuItems,
      ...analyzeMenuItems,
      {
        key: '/add_space?type=' + SPACE_TYPE_ENUM.TEAM,
        label: '创建团队',
        icon: () => h(TeamOutlined),
      },
    ]
  }
  // 有团队空间时，展示分组：我创建的 / 我加入的
  const loginUserId = loginUserStore.loginUser.id

  const validSpaces = teamSpaceList.value
    .filter((spaceUser) => spaceUser.space?.spaceName)

  const createdSpaces = validSpaces.filter((su) => su.space?.userId === loginUserId)
  const joinedSpaces = validSpaces.filter((su) => su.space?.userId !== loginUserId)

  const spaceMenuGroups: any[] = []

  const toMenuItem = (spaceUser: API.SpaceUserVO) => ({
    key: '/space/' + spaceUser.spaceId,
    label: spaceUser.space?.spaceName,
  })

  if (createdSpaces.length > 0) {
    spaceMenuGroups.push({
      type: 'group',
      label: '我创建的',
      key: 'createdSpace',
      children: createdSpaces.map(toMenuItem),
    })
  }

  if (joinedSpaces.length > 0) {
    spaceMenuGroups.push({
      type: 'group',
      label: '我加入的',
      key: 'joinedSpace',
      children: joinedSpaces.map(toMenuItem),
    })
  }

  // 如果没有任何有效团队空间，展示创建团队选项
  if (spaceMenuGroups.length < 1) {
    return [
      ...fixedMenuItems,
      ...analyzeMenuItems,
      {
        key: '/add_space?type=' + SPACE_TYPE_ENUM.TEAM,
        label: '创建团队',
        icon: () => h(TeamOutlined),
      },
    ]
  }

  return [...fixedMenuItems, ...analyzeMenuItems, ...spaceMenuGroups]
})

// 加载团队空间列表
const fetchTeamSpaceList = async () => {
  const res = await listMyTeamSpaceUsingPost()
  if (res.data.code === 0) {
    teamSpaceList.value = res.data.data ?? []
  } else {
    message.error('加载我的团队空间失败，' + res.data.message)
  }
}

/**
 * 监听变量，改变时触发数据的重新加载
 */
watchEffect(() => {
  // 登录才加载
  if (loginUserStore.loginUser.id) {
    fetchTeamSpaceList()
  }
})

const router = useRouter()
// 当前要高亮的菜单项
const current = ref<string[]>([])
// 监听路由变化，更新高亮菜单项，并刷新团队空间列表
router.afterEach((to, from, next) => {
  current.value = [to.path]
  if (loginUserStore.loginUser.id) {
    fetchTeamSpaceList()
  }
})

// 路由跳转事件
const doMenuClick = ({ key }) => {
  router.push(key)
}
</script>

<style scoped>
#globalSider .ant-layout-sider {
  background: none;
}
</style>
