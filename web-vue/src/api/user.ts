import request from './request'
import type { ApiResponse, LoginResponse, User } from '@/types'

export interface LoginParams {
  username: string
  password: string
}

export interface RegisterParams {
  username: string
  password: string
  realName?: string
  phone?: string
  email?: string
}

export interface UserListParams {
  username?: string
  role?: string
  status?: number
}

export const userApi = {
  login(data: LoginParams) {
    return request.post<LoginParams, ApiResponse<LoginResponse>>('/user/login', data)
  },

  register(data: RegisterParams) {
    return request.post<RegisterParams, ApiResponse<User>>('/user/register', data)
  },

  getUserInfo(id: number) {
    return request.get<never, ApiResponse<User>>(`/user/info/${id}`)
  },

  updateUser(data: User) {
    return request.put<User, ApiResponse<User>>('/user/update', data)
  },

  getUserList(params?: UserListParams) {
    return request.get<never, ApiResponse<User[]>>('/user/list', { params })
  },

  createUser(data: User) {
    return request.post<User, ApiResponse<User>>('/user', data)
  },

  updateUserById(id: number, data: User) {
    return request.put<User, ApiResponse<User>>(`/user/${id}`, data)
  },

  updateUserStatus(id: number, status: number) {
    return request.put<{ status: number }, ApiResponse<null>>(`/user/${id}/status`, { status })
  },

  deleteUser(id: number) {
    return request.delete<never, ApiResponse<null>>(`/user/${id}`)
  }
}
