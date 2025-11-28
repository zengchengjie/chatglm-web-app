import axios from 'axios'

// 创建axios实例
const api = axios.create({
  baseURL: '/api',
  timeout: 30000,
  headers: {
    'Content-Type': 'application/json'
  }
})

// 请求拦截器
api.interceptors.request.use(
  config => {
    // 登录请求不需要添加token
    if (config.url === '/auth/login') {
      return config
    }

    // 从localStorage获取token
    const token = localStorage.getItem('chatglm_token')
    if (token) {
      config.headers.Authorization = `Bearer ${token}`
    }
    return config
  },
  error => {
    return Promise.reject(error)
  }
)

// 响应拦截器
api.interceptors.response.use(
  response => {
    // 统一处理响应数据格式
    if (response.data && response.data.code !== undefined) {
      if (response.data.code === 200) {
        return response.data.data
      } else {
        return Promise.reject(new Error(response.data.message || '请求失败'))
      }
    }
    return response.data
  },
  error => {
    // 统一处理错误
    let message = '网络错误，请稍后重试'
    if (error.response) {
      // 服务器返回错误状态码
      switch (error.response.status) {
        case 401:
          message = '未授权，请重新登录'
          // 清除本地token
          localStorage.removeItem('chatglm_token')
          // 重定向到登录页
          window.location.href = '/login'
          break
        case 403:
          message = '登录已过期，请重新登录'
          // 清除本地token
          localStorage.removeItem('chatglm_token')
          // 重定向到登录页
          window.location.href = '/login'
          break
        case 404:
          message = '请求的资源不存在'
          break
        case 500:
          message = '服务器内部错误'
          break
        default:
          message = error.response.data?.message || `请求失败: ${error.response.status}`
      }
    } else if (error.request) {
      // 请求发出但没有收到响应
      message = '服务器无响应，请稍后重试'
    }
    console.error('API Error:', message)
    return Promise.reject(new Error(message))
  }
)

// 聊天相关API
export const chatAPI = {
  // 认证相关
  auth: {
    // 登录
    login: (data) => {
      return api.post('/auth/login', data)
    },
    
    // 注册
    register: (data) => {
      return api.post('/auth/register', data)
    },
    
    // 获取当前用户信息
    getCurrentUser: () => {
      return api.get('/auth/me')
    },

    // 退出登录
    logout: () => {
      return api.post('/auth/logout')
    }
  },
  
  // 普通聊天
  chat: (data) => {
    return api.post('/chat/completions', data)
  },
  
  // 流式聊天
  streamChat: (data, onMessage, onComplete, onError) => {
    let isClosed = false
    let controller = null
    
    try {
      // 创建控制器用于中止请求
      controller = new AbortController();
      const { signal } = controller;
      
      // 从localStorage获取token
      const token = localStorage.getItem('chatglm_token');
      
      // 使用fetch API进行流式请求
      fetch('/api/chat/stream', {
        method: 'POST',
        headers: {
          'Content-Type': 'application/json',
          'Accept': 'text/event-stream',
          ...(token && { 'Authorization': `Bearer ${token}` })
        },
        body: JSON.stringify(data),
        signal // 用于中止请求
      })
      .then(response => {
        if (!response.ok) {
          // 处理401和403错误（未授权和令牌过期）
          if (response.status === 401 || response.status === 403) {
            // 清除本地token
            localStorage.removeItem('chatglm_token')
            // 重定向到登录页
            window.location.href = '/login'
            throw new Error('登录已过期，请重新登录')
          }
          throw new Error(`HTTP error! status: ${response.status}`)
        }
        
        // 确保响应是SSE流
        const contentType = response.headers.get('content-type');
        if (!contentType || !contentType.includes('text/event-stream')) {
          throw new Error('Expected text/event-stream response');
        }
        
        // 获取可读流
        const reader = response.body.getReader()
        const decoder = new TextDecoder()
        
        // 处理流数据
        function processStream({ done, value }) {
          if (isClosed || done) {
            if (!isClosed) {
              onComplete()
            }
            return Promise.resolve()
          }
          
          try {
            // 解码接收到的数据
            const chunk = decoder.decode(value, { stream: false })
            
            // 按行分割数据
            const lines = chunk.split('\n')
            let currentEvent = ''
            
            // 遍历每一行进行解析
            for (let i = 0; i < lines.length; i++) {
              const line = lines[i] // 移除trim()，保留原始内容
              
              // 跳过空行
              if (!line) continue
              
              // 处理事件类型
              if (line.startsWith('event:')) {
                currentEvent = line.substring(6) // 移除trim()
              }
              // 处理数据内容
              else if (line.startsWith('data:')) {
                const data = line.substring(5) // 移除trim()
                
                // 根据事件类型处理数据
                if (currentEvent === 'message') {
                  // 处理消息内容
                  if (data) {
                    onMessage(data)
                  }
                } else if (currentEvent === 'done' || data === '[DONE]') {
                  // 处理完成事件
                  onComplete()
                  return Promise.resolve()
                } else if (currentEvent === 'error') {
                  // 处理错误事件
                  if (data) {
                    onError(new Error(data))
                  }
                } else if (!currentEvent && data) {
                  // 向后兼容：没有事件类型时，作为消息处理
                  onMessage(data)
                }
              }
            }
          } catch (error) {
            if (!isClosed) {
              onError(error)
            }
          }
          
          // 继续读取下一个数据块
          return reader.read().then(processStream)
        }
        
        // 开始处理流
        return reader.read().then(processStream)
      })
      .catch(error => {
        if (!isClosed) {
          onError(error)
        }
      })
      
      // 返回关闭函数
      return () => {
        isClosed = true
        controller.abort() // 中止请求
      }
    } catch (error) {
      if (!isClosed) {
        onError(error)
      }
      return () => {
        isClosed = true
      }
    }
  },
  
  // 健康检查
  health: () => {
    return api.get('/chat/health')
  }
}

export default api