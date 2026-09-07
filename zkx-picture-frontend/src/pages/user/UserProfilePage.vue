<template>
  <div id="userProfilePage">
    <h2>个人信息</h2>
    <!-- 修改理由：规则5 — 页面标题与内容区使用标准间距 -->
    <div style="margin-bottom: var(--dt-space-standard)" />

    <!-- 基本信息 -->
    <a-card title="基本信息" :bordered="false">
      <a-form layout="vertical" style="max-width: 480px">
        <a-form-item label="头像">
          <a-upload
            list-type="picture-card"
            :show-upload-list="false"
            :custom-request="handleAvatarUpload"
            :before-upload="beforeAvatarUpload"
          >
            <img v-if="formState.userAvatar" :src="formState.userAvatar" alt="avatar" style="width: 102px; height: 102px; object-fit: cover" />
            <div v-else>
              <loading-outlined v-if="avatarUploading"></loading-outlined>
              <plus-outlined v-else></plus-outlined>
              <div class="ant-upload-text">上传头像</div>
            </div>
          </a-upload>
        </a-form-item>
        <a-form-item label="用户名">
          <a-input v-model:value="formState.userName" placeholder="请输入用户名" />
        </a-form-item>
        <a-form-item label="账号">
          <a-input v-model:value="formState.userAccount" placeholder="请输入账号" />
        </a-form-item>
        <a-form-item label="个人简介">
          <a-textarea v-model:value="formState.userProfile" placeholder="请输入个人简介" :rows="3" />
        </a-form-item>
        <a-form-item>
          <a-button type="primary" :loading="saving" @click="handleSaveInfo">保存</a-button>
        </a-form-item>
      </a-form>
    </a-card>

    <!-- 修改理由：规则5 — 基本信息卡与修改密码卡为不同功能区域，使用宽松间距明确区分 -->
    <div style="margin-bottom: var(--dt-space-relaxed)" />

    <!-- 修改密码 -->
    <a-card title="修改密码" :bordered="false">
      <a-form layout="vertical" style="max-width: 480px">
        <a-form-item label="新密码">
          <a-input-password v-model:value="passwordForm.newPassword" placeholder="请输入新密码" />
        </a-form-item>
        <a-form-item label="确认密码">
          <a-input-password v-model:value="passwordForm.confirmPassword" placeholder="请再次输入新密码" />
        </a-form-item>
        <a-form-item>
          <a-button type="primary" :loading="savingPwd" @click="handleSavePassword">保存密码</a-button>
        </a-form-item>
      </a-form>
    </a-card>
  </div>
</template>

<script setup lang="ts">
import { reactive, ref } from 'vue'
import { message } from 'ant-design-vue'
import { LoadingOutlined, PlusOutlined } from '@ant-design/icons-vue'
import type { UploadProps } from 'ant-design-vue'
import { useLoginUserStore } from '@/stores/useLoginUserStore.ts'
import { editUserUsingPost } from '@/api/userController.ts'
import { uploadPictureUsingPost } from '@/api/pictureController.ts'

const loginUserStore = useLoginUserStore()

const formState = reactive({
  userAvatar: loginUserStore.loginUser.userAvatar ?? '',
  userName: loginUserStore.loginUser.userName ?? '',
  userAccount: loginUserStore.loginUser.userAccount ?? '',
  userProfile: loginUserStore.loginUser.userProfile ?? '',
})

const passwordForm = reactive({
  newPassword: '',
  confirmPassword: '',
})

const saving = ref(false)
const savingPwd = ref(false)
const avatarUploading = ref(false)

// 头像上传
const handleAvatarUpload = async ({ file }: any) => {
  avatarUploading.value = true
  try {
    const res = await uploadPictureUsingPost({}, {}, file)
    if (res.data.code === 0 && res.data.data) {
      formState.userAvatar = res.data.data.url ?? ''
      message.success('头像上传成功')
    } else {
      message.error('头像上传失败，' + res.data.message)
    }
  } catch (error: any) {
    message.error('头像上传失败，' + error.message)
  }
  avatarUploading.value = false
}

const beforeAvatarUpload = (file: UploadProps['fileList'][number]) => {
  const isJpgOrPng = file.type === 'image/jpeg' || file.type === 'image/png'
  if (!isJpgOrPng) {
    message.error('请上传 jpg 或 png 格式的图片')
  }
  const isLt2M = file.size / 1024 / 1024 < 2
  if (!isLt2M) {
    message.error('图片大小不能超过 2M')
  }
  return isJpgOrPng && isLt2M
}

// 保存基本信息
const handleSaveInfo = async () => {
  const id = loginUserStore.loginUser.id
  if (!id) {
    message.error('用户未登录')
    return
  }
  saving.value = true
  try {
    const res = await editUserUsingPost({
      id,
      userAvatar: formState.userAvatar,
      userName: formState.userName,
      userAccount: formState.userAccount,
      userProfile: formState.userProfile,
    })
    if (res.data.code === 0) {
      message.success('保存成功')
      await loginUserStore.fetchLoginUser()
    } else {
      message.error('保存失败，' + res.data.message)
    }
  } catch (error: any) {
    message.error('保存失败，' + error.message)
  }
  saving.value = false
}

// 保存密码
const handleSavePassword = async () => {
  const id = loginUserStore.loginUser.id
  if (!id) {
    message.error('用户未登录')
    return
  }
  if (!passwordForm.newPassword) {
    message.error('请输入新密码')
    return
  }
  if (passwordForm.newPassword !== passwordForm.confirmPassword) {
    message.error('两次输入的密码不一致')
    return
  }
  savingPwd.value = true
  try {
    const res = await editUserUsingPost({
      id,
      userPassword: passwordForm.newPassword,
    })
    if (res.data.code === 0) {
      message.success('密码修改成功')
      passwordForm.newPassword = ''
      passwordForm.confirmPassword = ''
    } else {
      message.error('密码修改失败，' + res.data.message)
    }
  } catch (error: any) {
    message.error('密码修改失败，' + error.message)
  }
  savingPwd.value = false
}
</script>
