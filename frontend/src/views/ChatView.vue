<template>
  <div class="chat-container">
    <div class="chat-header">
      <h1>智能AI助手</h1>
      <div class="user-info" v-if="currentUser">
        <span>欢迎, {{ currentUser.username }} ({{ currentUser.role }})</span>
        <el-button type="primary" link @click="handleLogout">退出</el-button>
      </div>
    </div>
    
    <div class="chat-content">
      <!-- 聊天消息区域 -->
      <div class="chat-messages" ref="messagesContainer">
        <!-- 系统欢迎消息 -->
        <div v-if="!chatStore.hasMessages" class="message-item assistant">
          <div class="message-role">AI助手</div>
          <div class="message-content">
            您好！我是智能AI助手，请问有什么可以帮助您的？
          </div>
        </div>
        
        <!-- 聊天消息列表 -->
        <div 
          v-for="message in chatStore.messages" 
          :key="message.id" 
          :class="['message-item', message.role]"
        >
          <div class="message-role">
            {{ message.role === 'user' ? '您' : 'AI助手' }}
            <span class="message-time">{{ message.timestamp }}</span>
          </div>
          <div class="message-content">
            <!-- 根据文本类型使用不同的渲染方式 -->
            <span v-if="shouldUseHtml(message.content)" v-html="formatMessage(message.content)"></span>
            <span v-else>{{ message.content }}</span>
            <!-- 调试信息 -->
            <div style="font-size: 10px; color: #999; margin-top: 5px;" v-if="false">
              Debug: {{ shouldUseHtml(message.content) ? 'HTML' : 'TEXT' }}
            </div>
          </div>
          <!-- 流式加载指示器 -->
          <span v-if="message.isStreaming" class="stream-loading">
            <span class="loading-dot"></span>
            <span class="loading-dot"></span>
            <span class="loading-dot"></span>
          </span>
        </div>
      </div>
      
      <!-- 输入区域 -->
      <div class="chat-input-container">
        <div class="chat-input-wrapper">
          <el-input
            v-model="inputMessage"
            class="chat-input"
            type="textarea"
            :rows="3"
            placeholder="请输入您的问题..."
            :disabled="chatStore.isLoading"
            @keyup.enter.ctrl="sendMessage"
          ></el-input>
          
          <div class="chat-options">
            <el-checkbox v-model="useStream">流式回复</el-checkbox>
          </div>
          
          <el-button 
            type="primary" 
            class="send-button" 
            :loading="chatStore.isLoading"
            @click="sendMessage"
          >
            发送
          </el-button>
        </div>
        
        <!-- 错误提示 -->
        <el-alert 
          v-if="chatStore.error" 
          :title="chatStore.error" 
          type="error" 
          show-icon 
          :closable="false"
          class="error-alert"
        ></el-alert>
      </div>
    </div>
  </div>
</template>

<script setup>
import { ref, nextTick, onMounted, watch } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import { useChatStore } from '../store/chatStore'
import { chatAPI } from '../utils/api'

// 状态管理
const chatStore = useChatStore()
const router = useRouter()
const inputMessage = ref('')
const useStream = ref(true) // 默认使用流式回复
const messagesContainer = ref(null)
const currentUser = ref(null)
let closeStream = null

// 获取当前用户信息
const getCurrentUser = async () => {
  try {
    const user = await chatAPI.auth.getCurrentUser()
    currentUser.value = user
  } catch (error) {
    console.error('Failed to get current user:', error)
  }
}

// 处理登出
const handleLogout = async () => {
  try {
    await ElMessageBox.confirm('确定要退出登录吗？', '提示', {
      type: 'warning'
    })
    
    // 调用后端登出API
    try {
      await chatAPI.auth.logout()
    } catch (error) {
      console.warn('Logout API call failed:', error)
    }
    
    // 清除token和用户状态
    localStorage.removeItem('chatglm_token')
    currentUser.value = null
    chatStore.clearChat()
    
    // 跳转到登录页
    router.push('/login')
  } catch (error) {
    // 用户取消操作
  }
}

// 发送消息
const sendMessage = async () => {
  const message = inputMessage.value.trim()
  if (!message) {
    ElMessage.warning('请输入问题')
    return
  }
  
  if (chatStore.isLoading) return
  
  // 清空输入框
  inputMessage.value = ''
  
  // 添加用户消息
  chatStore.addUserMessage(message)
  
  // 滚动到底部
  scrollToBottom()
  
  try {
    chatStore.setLoading(true)
    
    // 构建请求参数
    const requestData = {
      message: message,
      history: chatStore.chatHistory,
      stream: useStream.value
    }
    
    if (useStream.value) {
      // 流式请求
      let accumulatedResponse = ''
      
      closeStream = chatAPI.streamChat(
        requestData,
        // 接收消息回调
        (chunk) => {
          accumulatedResponse += chunk
          chatStore.addAssistantMessage(accumulatedResponse, true)
          scrollToBottom()
        },
        // 完成回调
        () => {
          chatStore.finishStreamingMessage()
          chatStore.setLoading(false)
          scrollToBottom()
          closeStream = null
        },
        // 错误回调
        (error) => {
          console.error('Stream error:', error)
          chatStore.setError(error.message || '流式请求失败')
          chatStore.setLoading(false)
          closeStream = null
        }
      )
    } else {
      // 普通请求
      const response = await chatAPI.chat(requestData)
      chatStore.addAssistantMessage(response)
      chatStore.setLoading(false)
      scrollToBottom()
    }
  } catch (error) {
    console.error('Chat error:', error)
    chatStore.setError(error.message || '请求失败，请稍后重试')
    chatStore.setLoading(false)
  }
}

// 检测文本是否需要使用v-html渲染
const shouldUseHtml = (content) => {
  // 检测是否包含英文字母（排除纯数字和特殊符号）
  const hasEnglish = /[a-zA-Z]/.test(content)
  // 检测是否包含中文
  const hasChinese = /[\u4e00-\u9fa5]/.test(content)
  
  // 如果包含英文且不是纯中英文混合，则使用HTML渲染
  // 这样可以保留英文单词间的空格
  return hasEnglish
}

// 格式化消息内容，保留空格和换行
const formatMessage = (content) => {
  return content
    .replace(/ /g, '&nbsp;')
    .replace(/\n/g, '<br>')
}

// 滚动到底部
const scrollToBottom = () => {
  nextTick(() => {
    if (messagesContainer.value) {
      messagesContainer.value.scrollTop = messagesContainer.value.scrollHeight
    }
  })
}

// 监听消息变化，自动滚动到底部
watch(
  () => chatStore.messages.length,
  () => {
    scrollToBottom()
  }
)

// 组件挂载时检查服务健康状态和用户信息
onMounted(async () => {
  try {
    await chatAPI.health()
    console.log('Chat service is healthy')
    
    // 获取当前用户信息
    await getCurrentUser()
  } catch (error) {
    console.error('Failed to connect to chat service:', error)
    ElMessage.warning('服务连接中，请稍后再试...')
  }
})
</script>

<style scoped>
.chat-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding: 20px;
  background: #fff;
  border-bottom: 1px solid #eee;
  box-shadow: 0 2px 5px rgba(0,0,0,0.05);
}

.user-info {
  display: flex;
  align-items: center;
  gap: 15px;
}

.message-time {
  font-size: 11px;
  margin-left: 8px;
  opacity: 0.7;
}

.chat-options {
  display: flex;
  align-items: center;
  margin-right: 10px;
}

.error-alert {
  margin-top: 10px;
}
</style>