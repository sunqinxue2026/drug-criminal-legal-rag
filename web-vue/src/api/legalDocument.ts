import request from './request'
import type {
  LegalDocument,
  LegalDocumentDetail,
  LegalDocumentImportResponse,
  PageResponse
} from '@/types'

export interface LegalDocumentPageParams {
  pageNum?: number
  pageSize?: number
  categoryId?: number
  keyword?: string
  documentType?: string
  caseType?: string
  procedureDomain?: string
}

export interface LegalDocumentImportParams {
  files: File[]
  categoryId?: number
  rebuildIndex?: boolean
}

export interface LegalDocumentReindexResponse {
  success?: boolean
  message?: string
}

export const legalDocumentApi = {
  getDocumentPage(params: LegalDocumentPageParams) {
    return request.get<any, { data: PageResponse<LegalDocument> }>('/legal-document/page', { params })
  },

  getDocumentDetail(id: number) {
    return request.get<any, { data: LegalDocumentDetail }>(`/legal-document/${id}`)
  },

  createDocument(data: LegalDocument, rebuildIndex = true) {
    return request.post<any, { message?: string; data?: { documentId?: number; reindexed?: boolean } }>(
      '/legal-document',
      data,
      {
        params: { rebuildIndex }
      }
    )
  },

  updateDocument(data: LegalDocument, rebuildIndex = true) {
    return request.put<any, { message?: string; data?: { documentId?: number; reindexed?: boolean } }>(
      '/legal-document',
      data,
      {
        params: { rebuildIndex }
      }
    )
  },

  previewDocument(file: File, categoryId?: number) {
    const formData = new FormData()
    formData.append('file', file)
    if (categoryId) {
      formData.append('categoryId', String(categoryId))
    }
    return request.post<any, { data: LegalDocumentDetail }>(
      '/legal-document/preview/file',
      formData,
      {
        headers: {
          'Content-Type': 'multipart/form-data'
        }
      }
    )
  },

  importDocuments(data: LegalDocumentImportParams) {
    const formData = new FormData()
    data.files.forEach((file) => formData.append('files', file))
    if (data.categoryId) {
      formData.append('categoryId', String(data.categoryId))
    }
    formData.append('rebuildIndex', String(data.rebuildIndex ?? true))

    return request.post<any, { data: LegalDocumentImportResponse }>(
      '/legal-document/import/files',
      formData,
      {
        headers: {
          'Content-Type': 'multipart/form-data'
        }
      }
    )
  },

  importSingleDocument(file: File, categoryId?: number, rebuildIndex = false) {
    const formData = new FormData()
    formData.append('file', file)
    if (categoryId) {
      formData.append('categoryId', String(categoryId))
    }
    formData.append('rebuildIndex', String(rebuildIndex))

    return request.post<any, { data: LegalDocumentImportResponse }>(
      '/legal-document/import/file',
      formData,
      {
        headers: {
          'Content-Type': 'multipart/form-data'
        }
      }
    )
  },

  importOfficialDocuments(categoryId?: number, rebuildIndex = true) {
    return request.post<any, { data: LegalDocumentImportResponse; message?: string }>(
      '/legal-document/import/official',
      null,
      {
        params: {
          categoryId,
          rebuildIndex
        }
      }
    )
  },

  deleteDocument(id: number, rebuildIndex = true) {
    return request.delete<any, { message?: string; data?: LegalDocumentReindexResponse }>(`/legal-document/${id}`, {
      params: { rebuildIndex }
    })
  },

  rebuildIndex() {
    return request.post<any, { data: LegalDocumentReindexResponse }>('/legal-document/reindex', null)
  }
}
