import { createRouter, createWebHistory } from 'vue-router'

const router = createRouter({
  history: createWebHistory(),
  routes: [
    {
      path: '/',
      redirect: '/agent/chat'
    },
    {
      path: '/agent/chat',
      name: 'AgentChat',
      component: () => import('@/module/agent/views/AgentChatView.vue')
    }
  ]
})

export default router
