import { createRouter, createWebHistory } from 'vue-router'

const routes = [
    { path: '/', redirect: '/workspace' },
    { path: '/workspace', name: 'Workspace', component: () => import('../../pages/WorkspacePage.vue') },
    { path: '/workbench', name: 'AgentWorkbench', component: () => import('../../components/agent/AgentWorkbenchPage.vue') },
    { path: '/tasks', name: 'TaskBoard', component: () => import('../../pages/TaskBoardPage.vue') },
    { path: '/tasks/:caseId', name: 'TaskDetail', component: () => import('../../pages/TaskDetailPage.vue') },
    { path: '/knowledge', name: 'KnowledgeBase', component: () => import('../../pages/KnowledgePage.vue') },
    { path: '/settings', name: 'Settings', component: () => import('../../pages/SettingsPage.vue') }
]

export default createRouter({
    history: createWebHistory(),
    routes
})
