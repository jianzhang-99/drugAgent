import { createRouter, createWebHistory } from 'vue-router'

const routes = [
    { path: '/', redirect: '/drug' },
    { path: '/drug', name: 'DrugMonitor', component: () => import('../views/DrugMonitor.vue') },
    { path: '/compliance', name: 'ComplianceChat', component: () => import('../views/ComplianceChat.vue') },
    { path: '/knowledge', name: 'KnowledgeBase', component: () => import('../views/KnowledgeBase.vue') },
]

export default createRouter({
    history: createWebHistory(),
    routes
})
