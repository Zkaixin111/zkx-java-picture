export default class SpaceChatWebSocket {
  private spaceId: string
  private socket: WebSocket | null
  private eventHandlers: any

  constructor(spaceId: string | number) {
    this.spaceId = String(spaceId)  // 雪花 ID 超 JS 安全整数，必须用字符串，不能 Number()
    this.socket = null
    this.eventHandlers = {}
  }

  /**
   * 初始化 WebSocket 连接
   */
  connect() {
    const DEV_BASE_URL = "ws://localhost:8123";
    // 线上地址
    // const PROD_BASE_URL = "ws://81.69.229.63";
    const url = `${DEV_BASE_URL}/api/ws/space/chat?spaceId=${this.spaceId}`
    this.socket = new WebSocket(url)
    this.socket.binaryType = 'blob'

    this.socket.onopen = () => {
      console.log('聊天 WebSocket 连接已建立')
      this.triggerEvent('open')
    }

    this.socket.onmessage = (event) => {
      const message = JSON.parse(event.data)
      console.log('收到聊天消息:', message)
      this.triggerEvent(message.type, message)
    }

    this.socket.onclose = (event) => {
      console.log('聊天 WebSocket 连接已关闭:', event)
      this.triggerEvent('close', event)
    }

    this.socket.onerror = (error) => {
      console.error('聊天 WebSocket 发生错误:', error)
      this.triggerEvent('error', error)
    }
  }

  /**
   * 关闭 WebSocket 连接
   */
  disconnect() {
    if (this.socket) {
      this.socket.close()
    }
  }

  /**
   * 发送消息到后端
   */
  sendMessage(message: object) {
    if (this.socket && this.socket.readyState === WebSocket.OPEN) {
      this.socket.send(JSON.stringify(message))
    } else {
      console.error('聊天 WebSocket 未连接，无法发送消息:', message)
    }
  }

  /**
   * 添加自定义事件监听
   */
  on(type: string, handler: (data?: any) => void) {
    if (!this.eventHandlers[type]) {
      this.eventHandlers[type] = []
    }
    this.eventHandlers[type].push(handler)
  }

  /**
   * 触发事件
   */
  triggerEvent(type: string, data?: any) {
    const handlers = this.eventHandlers[type]
    if (handlers) {
      handlers.forEach((handler: any) => handler(data))
    }
  }
}
