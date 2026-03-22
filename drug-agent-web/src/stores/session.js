import { defineStore } from 'pinia'
import { ref, computed } from 'vue'
import { load, save } from '@/utils/localStorage'

export const useSessionStore = defineStore('session', () => {
  const sessions = ref(load('sessions') || [])
  const activeSessionId = ref(load('activeSessionId') || null)

  const activeSession = computed(() => {
    return sessions.value.find(s => s.id === activeSessionId.value) || null
  })

  function persist() {
    save('sessions', sessions.value)
    save('activeSessionId', activeSessionId.value)
  }

  function createSession(title = '新对话') {
    const newSession = {
      id: Date.now().toString(),
      title,
      messages: [],
      createdAt: new Date().toISOString(),
      updatedAt: new Date().toISOString()
    }
    sessions.value.unshift(newSession)
    activeSessionId.value = newSession.id
    persist()
    return newSession
  }

  function deleteSession(id) {
    const index = sessions.value.findIndex(s => s.id === id)
    if (index !== -1) {
      sessions.value.splice(index, 1)
      if (activeSessionId.value === id) {
        activeSessionId.value = sessions.value[0]?.id || null
      }
      persist()
    }
  }

  function updateSession(id, updates) {
    const session = sessions.value.find(s => s.id === id)
    if (session) {
      Object.assign(session, updates, { updatedAt: new Date().toISOString() })
      persist()
    }
  }

  function setActiveSession(id) {
    activeSessionId.value = id
    persist()
  }

  function addMessage(sessionId, message) {
    const session = sessions.value.find(s => s.id === sessionId)
    if (session) {
      session.messages.push({
        id: Date.now().toString(),
        ...message,
        timestamp: new Date().toISOString()
      })
      session.updatedAt = new Date().toISOString()
      persist()
    }
  }

  return {
    sessions,
    activeSessionId,
    activeSession,
    createSession,
    deleteSession,
    updateSession,
    setActiveSession,
    addMessage
  }
})
