import { createRouter, createWebHistory } from 'vue-router'

const routes = [
    { path: '/', redirect: '/agent' },
    { path: '/agent', name: 'DrugAgentWorkbench', component: () => import('../views/DrugAgentWorkbench.vue') },
    { path: '/agent/tasks', name: 'TaskBoard', component: () => import('../views/TaskBoard.vue') },
]

export default createRouter({
    history: createWebHistory(),
    routes
})
