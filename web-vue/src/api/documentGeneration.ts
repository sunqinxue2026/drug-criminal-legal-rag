import request from './request'
import type { DocumentGenerationResult } from '@/types'

export interface DocumentGenerationParams {
  userId?: number
  sessionId: number
  documentType: 'summary' | 'report'
  template: 'brief' | 'standard' | 'detailed'
  title?: string
  customContent?: string
  format: 'markdown' | 'text' | 'html'
}

export const documentGenerationApi = {
  generate(data: DocumentGenerationParams) {
    return request.post<any, { data: DocumentGenerationResult }>('/document-generation/generate', data, {
      timeout: 120000
    })
  }
}
