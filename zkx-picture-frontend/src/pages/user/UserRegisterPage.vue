<template>
  <div id="userRegisterPage">
    <div class="auth-card">
      <!-- 品牌标识 -->
      <div class="auth-brand">
        <div class="auth-logo">☁</div>
        <h1 class="auth-title">创建账号</h1>
        <p class="auth-desc">注册拾光云图，开始管理你的图片</p>
      </div>

      <a-form :model="formState" name="basic" autocomplete="off" @finish="handleSubmit">
        <a-form-item name="userAccount" :rules="[{ required: true, message: '请输入账号' }]">
          <a-input
            v-model:value="formState.userAccount"
            placeholder="账号"
            size="large"
          />
        </a-form-item>
        <a-form-item
          name="userPassword"
          :rules="[
            { required: true, message: '请输入密码' },
            { min: 8, message: '密码长度不能小于 8 位' },
          ]"
        >
          <a-input-password
            v-model:value="formState.userPassword"
            placeholder="密码（至少 8 位）"
            size="large"
          />
        </a-form-item>
        <a-form-item
          name="checkPassword"
          :rules="[
            { required: true, message: '请再次输入密码' },
            { min: 8, message: '确认密码长度不能小于 8 位' },
          ]"
        >
          <a-input-password
            v-model:value="formState.checkPassword"
            placeholder="确认密码"
            size="large"
          />
        </a-form-item>
        <a-form-item>
          <a-button type="primary" html-type="submit" size="large" block>注册</a-button>
        </a-form-item>
      </a-form>

      <div class="auth-footer">
        已有账号？<RouterLink to="/user/login">立即登录</RouterLink>
      </div>
    </div>
  </div>
</template>
<script lang="ts" setup>
import { reactive } from 'vue'
import { userRegisterUsingPost } from '@/api/userController.ts'
import { message } from 'ant-design-vue'
import router from '@/router'

const formState = reactive<API.UserRegisterRequest>({
  userAccount: '',
  userPassword: '',
  checkPassword: '',
})

const handleSubmit = async (values: any) => {
  if (values.userPassword !== values.checkPassword) {
    message.error('两次输入的密码不一致')
    return
  }
  const res = await userRegisterUsingPost(values)
  if (res.data.code === 0 && res.data.data) {
    message.success('注册成功')
    router.push({ path: '/user/login', replace: true })
  } else {
    message.error('注册失败，' + res.data.message)
  }
}
</script>

<style scoped>
#userRegisterPage {
  min-height: calc(100vh - 56px);
  display: flex;
  align-items: center;
  justify-content: center;
  padding: var(--dt-space-standard);
}

.auth-card {
  width: 100%;
  max-width: 380px;
  background: var(--dt-color-bg-container);
  border: 1px solid var(--dt-color-border-default);
  border-radius: var(--dt-radius-xl);
  padding: var(--dt-space-loose);
  box-shadow: var(--dt-shadow-elevated);
}

.auth-brand {
  text-align: center;
  margin-bottom: var(--dt-space-relaxed);
}
.auth-logo {
  font-size: 40px;
  line-height: 1;
  margin-bottom: var(--dt-space-tight);
  filter: grayscale(0.15);
}
.auth-title {
  font-size: 22px;
  font-weight: 700;
  letter-spacing: -0.01em;
  color: var(--dt-color-text-primary);
  margin: 0 0 4px 0;
}
.auth-desc {
  font-size: 14px;
  color: var(--dt-color-text-secondary);
  margin: 0;
}

.auth-footer {
  text-align: center;
  font-size: 13px;
  color: var(--dt-color-text-tertiary);
  padding-top: var(--dt-space-tight);
  border-top: 1px solid var(--dt-color-border-default);
}
</style>
