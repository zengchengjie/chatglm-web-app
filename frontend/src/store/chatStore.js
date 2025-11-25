import { defineStore } from 'pinia'

export const useChatStore = defineStore('chat', {
  state: () => ({
    messages: [],
    isLoading: false,
    error: null
  }),
  
  getters: {
    hasMessages: (state) => state.messages.length > 0,
    chatHistory: (state) => {
      // 转换为后端API需要的格式
      return state.messages.map(msg => ({
        role: msg.role,
        content: msg.content
      }))
    }
  },
  
  actions: {
    addUserMessage(content) {
      this.messages.push({
        id: Date.now() + '_user',
        role: 'user',
        content: content,
        timestamp: new Date().toLocaleTimeString()
      })
      this.error = null
    },
    
    addAssistantMessage(content, isStreaming = false) {
      const id = isStreaming ? 'streaming_assistant' : Date.now() + '_assistant'
      
      // 如果是流式消息且已有流式消息，则更新它
      const existingIndex = this.messages.findIndex(m => m.id === id)
      if (existingIndex !== -1) {
        this.messages[existingIndex].content = content
      } else {
        this.messages.push({
          id: id,
          role: 'assistant',
          content: content,
          timestamp: new Date().toLocaleTimeString(),
          isStreaming: isStreaming
        })
      }
    },
    
    finishStreamingMessage() {
      const index = this.messages.findIndex(m => m.id === 'streaming_assistant')
      if (index !== -1) {
        // 重命名ID并移除流式标记
        this.messages[index].id = Date.now() + '_assistant'
        this.messages[index].isStreaming = false
      }
    },
    
    setLoading(status) {
      this.isLoading = status
    },
    
    setError(error) {
      this.error = error
    },
    
    clearChat() {
      this.messages = []
      this.error = null
    }
  }
})