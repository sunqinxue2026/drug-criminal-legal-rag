import request, { API_BASE_URL } from './request'
import type { ChatMessage, ChatSession, MessageAttachmentCase, MessageAttachmentDocument } from '@/types'

export interface ChatHistoryMessage {
  role: 'user' | 'assistant'
  content: string
  createTime?: string
  imageUrl?: string
  imageName?: string
}

export interface SendMessageParams {
  sessionId: number
  message: string
  model?: string
  smartConsulting?: boolean
  imageUrl?: string
  imageName?: string
  profile?: string
  history?: ChatHistoryMessage[]
  attachedCases?: MessageAttachmentCase[]
  attachedDocuments?: MessageAttachmentDocument[]
}

export interface LlmModelInfo {
  current_model: string
  allowed_models: string[]
  has_api_key: boolean
  provider: string
}

export interface VectorStatusInfo {
  model_name: string
  dimension: number
  mode: 'local' | 'bridge' | 'fallback'
  bridge_enabled: boolean
  bridge_ready: boolean
  local_model_loaded: boolean
  fallback_mode: boolean
}

export interface ChatStats {
  sessionCount: number
  messageCount: number
  dates: string[]
  sessionTrend: number[]
  messageTrend: number[]
}

export interface ChatStreamHandlers {
  onStatus?: (payload: Record<string, unknown>) => void
  onMeta?: (payload: Record<string, unknown>) => void
  onDelta?: (content: string) => void
}

export const chatApi = {
  getUserSessions(userId: number) {
    return request.get<any, { data: ChatSession[] }>(`/chat/sessions/${userId}`)
  },

  createSession(userId: number, title: string) {
    return request.post<any, { data: ChatSession }>('/chat/session', null, {
      params: { userId, title }
    })
  },

  getSessionMessages(sessionId: number) {
    return request.get<any, { data: ChatMessage[] }>(`/chat/messages/${sessionId}`)
  },

  sendMessage(data: SendMessageParams) {
    return request.post<any, { data: ChatMessage }>('/chat/send', data, {
      timeout: 120000
    })
  },

  async sendMessageStream(data: SendMessageParams, handlers: ChatStreamHandlers = {}) {
    const token = localStorage.getItem('token')
    const response = await fetch(`${API_BASE_URL}/chat/send-stream`, {
      method: 'POST',
      headers: {
        'Content-Type': 'application/json',
        ...(token ? { Authorization: `Bearer ${token}` } : {})
      },
      body: JSON.stringify(data)
    })

    if (!response.ok) {
      const contentType = response.headers.get('content-type') || ''
      if (contentType.includes('application/json')) {
        const payload = await response.json()
        throw new Error(payload?.message || '流式请求失败')
      }
      throw new Error(`流式请求失败: HTTP ${response.status}`)
    }

    if (!response.body) {
      throw new Error('浏览器未返回可读数据流')
    }

    const reader = response.body.getReader()
    const decoder = new TextDecoder('utf-8')
    let buffer = ''
    let eventName = 'message'
    let dataLines: string[] = []
    let finalMessage: ChatMessage | null = null

    const dispatchEvent = () => {
      if (!dataLines.length) return
      const raw = dataLines.join('\n')
      dataLines = []
      let payload: any = {}
      try {
        payload = JSON.parse(raw)
      } catch {
        payload = { message: raw }
      }

      if (eventName === 'status') {
        handlers.onStatus?.(payload)
      } else if (eventName === 'meta') {
        handlers.onMeta?.(payload)
      } else if (eventName === 'delta') {
        handlers.onDelta?.(String(payload?.content || ''))
      } else if (eventName === 'done') {
        finalMessage = payload as ChatMessage
      } else if (eventName === 'error') {
        throw new Error(payload?.message || '流式回答失败')
      }
      eventName = 'message'
    }

    while (true) {
      const { done, value } = await reader.read()
      buffer += decoder.decode(value || new Uint8Array(), { stream: !done })
      const lines = buffer.split(/\r?\n/)
      buffer = lines.pop() || ''

      for (const line of lines) {
        if (!line.trim()) {
          dispatchEvent()
          continue
        }
        if (line.startsWith('event:')) {
          eventName = line.slice(6).trim()
          continue
        }
        if (line.startsWith('data:')) {
          dataLines.push(line.slice(5).trim())
        }
      }

      if (done) {
        if (buffer.trim()) {
          const finalLines = buffer.split(/\r?\n/)
          for (const line of finalLines) {
            if (!line.trim()) continue
            if (line.startsWith('event:')) {
              eventName = line.slice(6).trim()
            } else if (line.startsWith('data:')) {
              dataLines.push(line.slice(5).trim())
            }
          }
        }
        dispatchEvent()
        break
      }
    }

    if (!finalMessage) {
      throw new Error('流式回答未返回最终结果')
    }
    return finalMessage
  },

  getLlmModels() {
    return request.get<any, { data: LlmModelInfo }>('/llm/models')
  },

  getVectorStatus() {
    return request.get<any, { data: VectorStatusInfo }>('/vector/status')
  },

  deleteSession(sessionId: number) {
    return request.delete<any, any>(`/chat/session/${sessionId}`)
  },

  getStats() {
    return request.get<any, { data: ChatStats }>('/chat/stats')
  }
}
