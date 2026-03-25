import { createRouter, createWebHistory } from 'vue-router'

const router = createRouter({
  history: createWebHistory(),
  routes: [
    {
      path: '/',
      redirect: '/agent'
    },
    {
      path: '/agent',
      name: 'Agent',
      component: () => import('@/module/agent/components/AgentPage.vue')
    }
  ]
})

export default router
