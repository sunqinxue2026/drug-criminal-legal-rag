import request from './request'
import type { RecommendationResult } from '@/types'

export const recommendApi = {
  getCases(userId?: number, limit = 6) {
    return request.get<any, { data: RecommendationResult }>('/recommend/cases', {
      params: {
        userId,
        limit
      }
    })
  }
}
