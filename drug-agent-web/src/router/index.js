import { createRouter, createWebHistory } from 'vue-router'

const routes = [
    { path: '/', redirect: '/agent' },
    { path: '/agent', name: 'DrugAgentWorkbench', component: () => import('../views/DrugAgentWorkbench.vue') },
    { path: '/agent/tasks', name: 'TaskBoard', component: () => import('../views/TaskBoard.vue') },
    { path: '/agent/tasks/:caseId', name: 'TaskDetail', component: () => import('../views/TaskDetail.vue') },
    { path: '/agent/knowledge', name: 'ComplianceKnowledgeBase', component: () => import('../views/ComplianceKnowledgeBase.vue') },
    { path: '/agent/audit', name: 'AuditLog', component: () => import('../views/AuditLog.vue') },
    { path: '/agent/settings', name: 'SystemSettings', component: () => import('../views/SystemSettings.vue') },
    { path: '/agent/help', name: 'HelpCenter', component: () => import('../views/HelpCenter.vue') }
]

export default createRouter({
    history: createWebHistory(),
    routes
})
