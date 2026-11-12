import axios from 'axios'
import request from './request'
import { ENV } from '@/config/constants'

const FLASK_DOCUMENT_API = `${ENV.ALGORITHM_API_BASE_URL}/api/document`

export interface UploadFileResult {
  bucket: string
  objectKey: string
  fileName?: string
  url: string
}

export const fileApi = {
  uploadFile(file: File, bucket = 'chat-images') {
    const formData = new FormData()
    formData.append('file', file)
    formData.append('bucket', bucket)

    return request.post<any, { data: UploadFileResult }>('/file/upload', formData, {
      headers: {
        'Content-Type': 'multipart/form-data'
      }
    })
  },

  getFileUrl(bucket: string, objectKey: string) {
    return request.get<any, any>('/file/url', {
      params: { bucket, objectKey }
    })
  },

  extractDocumentText(file: File) {
    const formData = new FormData()
    formData.append('file', file)

    return axios
      .post(`${FLASK_DOCUMENT_API}/extract-text`, formData, {
        headers: {
          'Content-Type': 'multipart/form-data'
        }
      })
      .then((response) => response.data)
  }
}
