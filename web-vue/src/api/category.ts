import request from './request'
import type { Category } from '@/types'

export const categoryApi = {
  // 获取所有分类
  getCategoryList() {
    return request.get<any, any>('/category/list')
  },

  // 获取分类详情
  getCategoryById(id: number) {
    return request.get<any, any>(`/category/${id}`)
  },

  // 创建分类
  createCategory(data: Category) {
    return request.post<any, any>('/category', data)
  },

  // 更新分类
  updateCategory(data: Category) {
    return request.put<any, any>('/category', data)
  },

  // 删除分类
  deleteCategory(id: number) {
    return request.delete<any, any>(`/category/${id}`)
  }
}

// 兼容旧的导出方式
export const getCategoryList = categoryApi.getCategoryList
export const getCategoryById = categoryApi.getCategoryById
export const createCategory = categoryApi.createCategory
export const updateCategory = categoryApi.updateCategory
export const deleteCategory = categoryApi.deleteCategory
