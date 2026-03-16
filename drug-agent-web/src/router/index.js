import { createRouter, createWebHistory } from 'vue-router'

const routes = [
    { path: '/', redirect: '/agent' },
    { path: '/agent', name: 'DrugAgentWorkbench', component: () => import('../views/DrugAgentWorkbench.vue') },
    { path: '/drug', name: 'DrugMonitor', component: () => import('../views/DrugMonitor.vue') },
    { path: '/compliance', name: 'ComplianceChat', component: () => import('../views/ComplianceChat.vue') },
    { path: '/knowledge', name: 'KnowledgeBase', component: () => import('../views/KnowledgeBase.vue') },
]

export default createRouter({
    history: createWebHistory(),
    routes
})
