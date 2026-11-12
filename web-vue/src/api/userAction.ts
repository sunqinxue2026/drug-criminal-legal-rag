import request from './request'

export interface HotQuestionInsight {
  question: string
  count: number
}

export interface SearchKeywordInsight {
  keyword: string
  count: number
}

export interface CategoryPreferenceInsight {
  categoryId: number
  categoryName: string
  themeColor?: string
  count: number
}

export interface JourneyFunnelInsight {
  stage: string
  count: number
}

export interface UserActionDashboardInsights {
  hotQuestions: HotQuestionInsight[]
  searchKeywords: SearchKeywordInsight[]
  categoryPreference: CategoryPreferenceInsight[]
  journeyFunnel: JourneyFunnelInsight[]
}

export type UserActionRecord = {
  username: string
  actionType: string
  actionTarget?: string
  actionDetail?: string
  ipAddress?: string
  userAgent?: string
  createTime?: string
}

export type UserActionStats = {
  totalViews: number
  totalChats: number
  totalSearches: number
  totalFavorites: number
}

export type UserActionPage = {
  records: UserActionRecord[]
  total: number
}

export type UserActionListParams = {
  page: number
  size: number
  username?: string
  actionType?: string
  startDate?: string
  endDate?: string
}

export const userActionApi = {
  logSearch(userId: number, query: string, source = '案例检索') {
    return request.post<any, any>('/user-action/search-log', null, {
      params: { userId, query, source }
    })
  },

  getStats() {
    return request.get<any, { data: UserActionStats }>('/user-action/stats')
  },

  getList(params: UserActionListParams) {
    return request.get<any, { data: UserActionPage }>('/user-action/list', { params })
  },

  getDashboardInsights() {
    return request.get<any, { data: UserActionDashboardInsights }>('/user-action/dashboard')
  }
}
