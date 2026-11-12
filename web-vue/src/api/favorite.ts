import request from './request'

export const favoriteApi = {
  getList(userId: number) {
    return request.get<any, any>('/favorite/list', { params: { userId } })
  },

  check(userId: number, itemId: number) {
    return request.get<any, any>('/favorite/status', { params: { userId, itemId } })
  },

  add(userId: number, itemId: number) {
    return request.post<any, any>('/favorite', null, { params: { userId, itemId } })
  },

  remove(userId: number, itemId: number) {
    return request.delete<any, any>(`/favorite/${itemId}`, { params: { userId } })
  }
}
