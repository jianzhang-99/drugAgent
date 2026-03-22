import { defineStore } from 'pinia'
import { ref, computed } from 'vue'
import { load, save } from '@/utils/localStorage'

export const useTaskStore = defineStore('task', () => {
  const tasks = ref(load('tasks') || [])

  const pendingTasks = computed(() =>
    tasks.value.filter(t => t.status === 'pending')
  )

  const inProgressTasks = computed(() =>
    tasks.value.filter(t => t.status === 'in_progress')
  )

  const completedTasks = computed(() =>
    tasks.value.filter(t => t.status === 'completed')
  )

  function persist() {
    save('tasks', tasks.value)
  }

  function addTask(task) {
    const newTask = {
      id: Date.now().toString(),
      title: task.title || '新任务',
      description: task.description || '',
      status: 'pending',
      progress: 0,
      priority: task.priority || 'medium',
      workflow: task.workflow || null,
      result: null,
      createdAt: new Date().toISOString(),
      updatedAt: new Date().toISOString()
    }
    tasks.value.unshift(newTask)
    persist()
    return newTask
  }

  function updateTask(id, updates) {
    const task = tasks.value.find(t => t.id === id)
    if (task) {
      Object.assign(task, updates, { updatedAt: new Date().toISOString() })
      persist()
    }
  }

  function updateProgress(id, progress) {
    const task = tasks.value.find(t => t.id === id)
    if (task) {
      task.progress = Math.min(100, Math.max(0, progress))
      if (progress >= 100) {
        task.status = 'completed'
      } else if (progress > 0) {
        task.status = 'in_progress'
      }
      task.updatedAt = new Date().toISOString()
      persist()
    }
  }

  function deleteTask(id) {
    const index = tasks.value.findIndex(t => t.id === id)
    if (index !== -1) {
      tasks.value.splice(index, 1)
      persist()
    }
  }

  function setTaskStatus(id, status) {
    const task = tasks.value.find(t => t.id === id)
    if (task) {
      task.status = status
      task.updatedAt = new Date().toISOString()
      persist()
    }
  }

  return {
    tasks,
    pendingTasks,
    inProgressTasks,
    completedTasks,
    addTask,
    updateTask,
    updateProgress,
    deleteTask,
    setTaskStatus
  }
})
