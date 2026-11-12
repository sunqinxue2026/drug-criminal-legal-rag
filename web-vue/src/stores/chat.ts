import { defineStore } from 'pinia'
import { ref } from 'vue'
import type { ChatSession, ChatMessage } from '@/types'

export const useChatStore = defineStore('chat', () => {
  const sessions = ref<ChatSession[]>([])
  const currentSession = ref<ChatSession | null>(null)
  const messages = ref<ChatMessage[]>([])
  const loading = ref(false)

  function setSessions(sessionList: ChatSession[]) {
    sessions.value = sessionList
  }

  function setCurrentSession(session: ChatSession | null) {
    currentSession.value = session
  }

  function setMessages(messageList: ChatMessage[]) {
    messages.value = messageList
  }

  function addMessage(message: ChatMessage) {
    messages.value.push(message)
  }

  function addSession(session: ChatSession) {
    sessions.value.unshift(session)
  }

  function removeSession(sessionId: number) {
    sessions.value = sessions.value.filter(s => s.id !== sessionId)
  }

  function setLoading(value: boolean) {
    loading.value = value
  }

  function clearMessages() {
    messages.value = []
  }

  return {
    sessions,
    currentSession,
    messages,
    loading,
    setSessions,
    setCurrentSession,
    setMessages,
    addMessage,
    addSession,
    removeSession,
    setLoading,
    clearMessages
  }
})
