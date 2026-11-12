import request from './request'
import type {
  LegalKnowledge,
  LegalKnowledgeDetail,
  LegalKnowledgeImportResult,
  LegalKnowledgeImportResponse,
  PageResponse
} from '@/types'

export interface LegalKnowledgePageParams {
  pageNum?: number
  pageSize?: number
  categoryId?: number
  keyword?: string
  knowledgeType?: string
  businessDomain?: string
}

export interface LegalKnowledgeRecordPayload {
  record: Record<string, any>
  sourceFileName?: string
  categoryId?: number
}

export interface LegalKnowledgeReindexResponse {
  success?: boolean
  message?: string
}

export const legalKnowledgeApi = {
  getKnowledgePage(params: LegalKnowledgePageParams) {
    return request.get<any, { data: PageResponse<LegalKnowledge> }>('/legal-knowledge/page', { params })
  },

  getKnowledgeDetail(id: number) {
    return request.get<any, { data: LegalKnowledgeDetail }>(`/legal-knowledge/${id}`)
  },

  createKnowledge(data: LegalKnowledge, rebuildIndex = true) {
    return request.post<any, { message?: string; data?: { knowledgeId?: number; reindexed?: boolean } }>(
      '/legal-knowledge',
      data,
      {
        params: { rebuildIndex }
      }
    )
  },

  updateKnowledge(data: LegalKnowledge, rebuildIndex = true) {
    return request.put<any, { message?: string; data?: { knowledgeId?: number; reindexed?: boolean } }>(
      '/legal-knowledge',
      data,
      {
        params: { rebuildIndex }
      }
    )
  },

  importKnowledgeJson(file: File, categoryId?: number, rebuildIndex = true) {
    const formData = new FormData()
    formData.append('file', file)
    if (categoryId) {
      formData.append('categoryId', String(categoryId))
    }
    formData.append('rebuildIndex', String(rebuildIndex))

    return request.post<any, { data: LegalKnowledgeImportResponse }>(
      '/legal-knowledge/import/file',
      formData,
      {
        headers: {
          'Content-Type': 'multipart/form-data'
        }
      }
    )
  },

  importOfficialKnowledge(categoryId?: number, rebuildIndex = true) {
    return request.post<any, { data: LegalKnowledgeImportResponse; message?: string }>(
      '/legal-knowledge/import/official',
      null,
      {
        params: {
          categoryId,
          rebuildIndex
        }
      }
    )
  },

  previewKnowledgeRecord(payload: LegalKnowledgeRecordPayload) {
    return request.post<any, { data: LegalKnowledgeDetail }>('/legal-knowledge/preview/record', payload)
  },

  importKnowledgeRecord(payload: LegalKnowledgeRecordPayload) {
    return request.post<any, { data: LegalKnowledgeImportResult }>('/legal-knowledge/import/record', payload)
  },

  deleteKnowledge(id: number, rebuildIndex = true) {
    return request.delete<any, { message?: string; data?: LegalKnowledgeReindexResponse }>(`/legal-knowledge/${id}`, {
      params: { rebuildIndex }
    })
  },

  rebuildIndex() {
    return request.post<any, { data: LegalKnowledgeReindexResponse }>('/legal-knowledge/reindex', null)
  }
}
