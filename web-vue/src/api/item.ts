import request from './request'
import type { FavoriteItem, Item, ItemAsset, ItemImportResponse, OfficialCaseSyncResponse } from '@/types'

export interface ItemPageParams {
  pageNum?: number
  pageSize?: number
  categoryId?: number
  keyword?: string
  tags?: string
}

export interface ItemImportParams {
  files: File[]
  categoryId?: number
  rebuildIndex?: boolean
}

export interface ItemPreviewResponse {
  item?: Item
  charCount?: number
  supported?: boolean
  message?: string
}

export interface ItemReindexResponse {
  success?: boolean
  message?: string
}

export const itemApi = {
  getItemPage(params: ItemPageParams) {
    return request.get<any, any>('/item/page', { params })
  },

  getItemDetail(id: number, userId?: number) {
    return request.get<any, any>(`/item/${id}`, {
      params: userId ? { userId } : undefined
    })
  },

  createItem(data: Item) {
    return request.post<any, any>('/item', data)
  },

  updateItem(data: Item) {
    return request.put<any, any>('/item', data)
  },

  deleteItem(id: number) {
    return request.delete<any, any>(`/item/${id}`)
  },

  previewItem(file: File, categoryId?: number) {
    const formData = new FormData()
    formData.append('file', file)
    if (categoryId) {
      formData.append('categoryId', String(categoryId))
    }

    return request.post<any, { data: ItemPreviewResponse }>(
      '/item/preview/file',
      formData,
      {
        headers: {
          'Content-Type': 'multipart/form-data'
        }
      }
    )
  },

  importItems(data: ItemImportParams) {
    const formData = new FormData()
    data.files.forEach((file) => formData.append('files', file))
    if (data.categoryId) {
      formData.append('categoryId', String(data.categoryId))
    }
    formData.append('rebuildIndex', String(data.rebuildIndex ?? true))

    return request.post<any, { code: number; message: string; data: ItemImportResponse }>(
      '/item/import/files',
      formData,
      {
        headers: {
          'Content-Type': 'multipart/form-data'
        }
      }
    )
  },

  importSingleItem(file: File, categoryId?: number, rebuildIndex = false) {
    const formData = new FormData()
    formData.append('file', file)
    if (categoryId) {
      formData.append('categoryId', String(categoryId))
    }
    formData.append('rebuildIndex', String(rebuildIndex))

    return request.post<any, { data: ItemImportResponse }>(
      '/item/import/file',
      formData,
      {
        headers: {
          'Content-Type': 'multipart/form-data'
        }
      }
    )
  },

  rebuildIndex() {
    return request.post<any, { data: ItemReindexResponse }>('/item/reindex', null)
  },

  syncOfficialCases() {
    return request.post<any, { data: OfficialCaseSyncResponse; message?: string }>('/item/sync/official', null)
  },

  getItemAssets(itemId: number) {
    return request.get<any, { data: ItemAsset[] }>(`/item/${itemId}/assets`)
  },

  addItemAsset(itemId: number, data: ItemAsset) {
    return request.post<any, { data: ItemAsset }>(`/item/${itemId}/assets`, data)
  },

  deleteItemAsset(assetId: number) {
    return request.delete<any, any>(`/item/assets/${assetId}`)
  },

  searchItems(query: string, topK?: number) {
    return request.get<any, any>('/search', {
      params: { query, topK: topK || 5 }
    })
  },

  getFavorites(userId: number) {
    return request.get<any, { data: FavoriteItem[] }>('/favorite/list', {
      params: { userId }
    })
  },

  getFavoriteStatus(userId: number, itemId: number) {
    return request.get<any, { data: { isFavorite: boolean } }>('/favorite/status', {
      params: { userId, itemId }
    })
  },

  addFavorite(userId: number, itemId: number) {
    return request.post<any, any>('/favorite', null, {
      params: { userId, itemId }
    })
  },

  removeFavorite(userId: number, itemId: number) {
    return request.delete<any, any>(`/favorite/${itemId}`, {
      params: { userId }
    })
  }
}
