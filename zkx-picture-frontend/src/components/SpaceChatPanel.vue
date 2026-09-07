<template>
  <div class="space-chat-panel">
    <!-- 聊天消息列表 -->
    <div ref="messageListRef" class="chat-messages">
      <div v-if="messages.length === 0" class="chat-empty">
        还没有消息，快来发起第一条吧
      </div>
      <div
        v-for="(msg, idx) in messages"
        :key="idx"
        class="chat-message"
        :class="{
          'chat-message--system': msg.type !== 'SEND',
          'chat-message--mine': isMine(msg),
        }"
      >
        <template v-if="msg.type === 'SEND'">
          <!-- 自己发的：头像在右，气泡靠右 -->
          <div v-if="isMine(msg)" class="chat-message-row">
            <div class="chat-message-body">
              <div class="chat-message-content chat-message-content--mine">{{ msg.message }}</div>
              <div class="chat-message-time">{{ formatTime(msg.createTime) }}</div>
            </div>
            <a-avatar :src="msg.user?.userAvatar" :size="28" />
          </div>
          <!-- 别人发的：头像在左，气泡靠左 -->
          <div v-else class="chat-message-row">
            <a-avatar :src="msg.user?.userAvatar" :size="28" />
            <div class="chat-message-body">
              <div class="chat-message-name">{{ msg.user?.userName }}</div>
              <div class="chat-message-content">{{ msg.message }}</div>
            </div>
          </div>
        </template>
        <template v-else>
          <span class="chat-system-text">{{ msg.message }}</span>
        </template>
      </div>
    </div>

    <!-- 输入框 -->
    <div class="chat-input-area">
      <a-input
        v-model:value="inputValue"
        placeholder="输入消息，按 Enter 发送"
        @pressEnter="doSend"
        :disabled="!connected"
      />
      <a-button type="primary" @click="doSend" :disabled="!connected || !inputValue.trim()">
        发送
      </a-button>
    </div>
  </div>
</template>

<script setup lang="ts">
import { nextTick, onBeforeUnmount, onMounted, ref } from 'vue'
import SpaceChatWebSocket from '@/utils/spaceChatWebSocket.ts'
import { message } from 'ant-design-vue'
import { useLoginUserStore } from '@/stores/useLoginUserStore.ts'

interface Props {
  spaceId: string | number
}

const props = defineProps<Props>()

// 当前登录用户（判断消息是否自己发的）
const loginUserStore = useLoginUserStore()

// 转字符串避免雪花 ID 精度丢失
const spaceIdStr = String(props.spaceId)

// 聊天消息列表
const messages = ref<any[]>([])
// 输入框内容
const inputValue = ref('')
// 连接状态
const connected = ref(false)
// 消息列表 DOM 引用（用于自动滚动到底部）
const messageListRef = ref()

// WS 客户端
let chatWs: SpaceChatWebSocket | null = null

// 判断消息是否自己发的（Long 序列化成字符串，需转 Number 对比）
const isMine = (msg: any) => {
  const myId = loginUserStore.loginUser?.id
  const senderId = msg.user?.id
  if (!myId || !senderId) return false
  return Number(myId) === Number(senderId)
}

// 格式化时间（后端 Long 序列化为字符串，必须转 Number 再 new Date）
const formatTime = (timestamp: number | string) => {
  if (!timestamp) return ''
  const d = new Date(Number(timestamp))
  if (isNaN(d.getTime())) return ''
  const pad = (n: number) => String(n).padStart(2, '0')
  return `${pad(d.getHours())}:${pad(d.getMinutes())}:${pad(d.getSeconds())}`
}

// 滚动到底部
const scrollToBottom = async () => {
  await nextTick()
  if (messageListRef.value) {
    messageListRef.value.scrollTop = messageListRef.value.scrollHeight
  }
}

// 初始化 WS 连接
const initWebSocket = () => {
  chatWs = new SpaceChatWebSocket(spaceIdStr)

  chatWs.on('open', () => {
    connected.value = true
  })

  chatWs.on('SEND', (data: any) => {
    messages.value.push(data)
    scrollToBottom()
  })

  chatWs.on('JOIN', (data: any) => {
    messages.value.push(data)
    scrollToBottom()
  })

  chatWs.on('LEAVE', (data: any) => {
    messages.value.push(data)
    scrollToBottom()
  })

  chatWs.on('ERROR', (data: any) => {
    message.error(data?.message || '消息发送失败')
  })

  chatWs.on('close', () => {
    connected.value = false
  })

  chatWs.connect()
}

// 发送消息
const doSend = () => {
  const content = inputValue.value?.trim()
  if (!content || !chatWs) return
  chatWs.sendMessage({
    type: 'SEND',
    content,
  })
  inputValue.value = ''
}

onMounted(() => {
  initWebSocket()
})

onBeforeUnmount(() => {
  chatWs?.disconnect()
})
</script>

<style scoped>
.space-chat-panel {
  display: flex;
  flex-direction: column;
  height: 100%;
  min-height: 300px;
}

/* 消息列表区 */
.chat-messages {
  flex: 1;
  overflow-y: auto;
  padding: 12px;
  background: var(--color-background-secondary, #fafafa);
  border-radius: 8px;
  margin-bottom: 8px;
}

.chat-empty {
  text-align: center;
  color: var(--color-text-tertiary, #999);
  padding: 40px 0;
  font-size: 13px;
}

/* 单条消息 */
.chat-message {
  margin-bottom: 12px;
}

/* 消息行：头像 + 内容体（左右布局） */
.chat-message-row {
  display: flex;
  align-items: flex-start;
  gap: 8px;
}

/* 自己发的：整行右对齐 */
.chat-message--mine .chat-message-row {
  justify-content: flex-end;
}

/* 消息体 */
.chat-message-body {
  display: flex;
  flex-direction: column;
  max-width: 75%;
}

/* 自己发的消息体：内容右对齐 */
.chat-message--mine .chat-message-body {
  align-items: flex-end;
}

.chat-message-name {
  font-weight: 500;
  font-size: 12px;
  color: var(--color-text-secondary, #666);
  margin-bottom: 3px;
}

.chat-message-time {
  font-size: 11px;
  color: var(--color-text-tertiary, #999);
  margin-top: 3px;
}

.chat-message-content {
  background: var(--color-background-primary, #fff);
  border: 1px solid var(--color-border-tertiary, #eee);
  border-radius: 8px;
  padding: 6px 10px;
  font-size: 13px;
  display: inline-block;
  word-break: break-word;
}

/* 自己发的气泡：蓝色 + 白色文字 */
.chat-message-content--mine {
  background: #1890ff;
  border-color: #1890ff;
  color: #fff;
}

/* 系统消息（加入/离开） */
.chat-message--system {
  text-align: center;
}

.chat-system-text {
  font-size: 12px;
  color: var(--color-text-tertiary, #999);
  background: var(--color-background-tertiary, #f0f0f0);
  padding: 2px 10px;
  border-radius: 10px;
  display: inline-block;
}

/* 输入区 */
.chat-input-area {
  display: flex;
  gap: 8px;
}
</style>
