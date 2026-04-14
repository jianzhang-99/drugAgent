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
    },
    {
      path: '/agent/tasks',
      name: 'AgentTasks',
      component: () => import('@/module/agent/views/AgentChatView.vue')
    },
    {
      path: '/agent/knowledge',
      name: 'AgentKnowledge',
      component: () => import('@/module/agent/views/AgentChatView.vue')
    },
    {
      path: '/agent/benchmark',
      name: 'AgentBenchmark',
      component: () => import('@/module/agent/views/AgentChatView.vue')
    }
  ]
})

export default router
