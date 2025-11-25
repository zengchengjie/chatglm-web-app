import { createRouter, createWebHistory } from 'vue-router'

const router = createRouter({
  history: createWebHistory(import.meta.env.BASE_URL),
  routes: [
    {
      path: '/login',
      name: 'login',
      component: () => import('../views/LoginView.vue'),
      meta: {
        title: '用户登录 - ChatGLM AI助手'
      }
    },
    {
      path: '/',
      name: 'chat',
      component: () => import('../views/ChatView.vue'),
      meta: {
        title: 'ChatGLM AI助手',
        requiresAuth: true
      }
    },
    {
      path: '/:pathMatch(.*)*',
      redirect: '/'
    }
  ]
})

// 路由守卫 - 检查认证状态
router.beforeEach(async (to, from, next) => {
  const token = localStorage.getItem('chatglm_token')
  
  // 如果路由需要认证但没有token，重定向到登录页
  if (to.meta.requiresAuth && !token) {
    next('/login')
    return
  }
  
  // 如果已登录且访问登录页，重定向到聊天页
  if (to.path === '/login' && token) {
    // 验证token是否仍然有效
    try {
      const response = await fetch('/api/auth/me', {
        headers: {
          'Authorization': `Bearer ${token}`
        }
      })
      
      if (response.ok) {
        next('/')
        return
      } else {
        // token无效，清除并跳转到登录页
        localStorage.removeItem('chatglm_token')
        next('/login')
        return
      }
    } catch (error) {
      // 请求失败，清除token并跳转到登录页
      localStorage.removeItem('chatglm_token')
      next('/login')
      return
    }
  }
  
  next()
})
// 设置页面标题
router.afterEach((to) => {
  document.title = to.meta.title || 'ChatGLM AI助手'
})

export default router