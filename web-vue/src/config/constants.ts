// 环境变量配置
export const ENV = {
  // API 基础地址
  API_BASE_URL: import.meta.env.VITE_API_BASE_URL || 'http://localhost:8080/api',
  ALGORITHM_API_BASE_URL: import.meta.env.VITE_ALGORITHM_API_BASE_URL || 'http://localhost:5000',
  FILE_SERVICE_BASE_URL: import.meta.env.VITE_FILE_SERVICE_BASE_URL || 'http://localhost:5001',
  
  // 应用标题
  APP_TITLE: import.meta.env.VITE_APP_TITLE || '涉毒刑事法律咨询问答系统',
  
  // 是否开发环境
  IS_DEV: import.meta.env.DEV,
  
  // 是否生产环境
  IS_PROD: import.meta.env.PROD
}

// 分页配置
export const PAGINATION = {
  PAGE_SIZE: 10,
  PAGE_SIZES: [10, 20, 50, 100]
}

// 文件上传配置
export const UPLOAD = {
  MAX_SIZE: 10 * 1024 * 1024, // 10MB
  ALLOWED_TYPES: [
    'image/jpeg',
    'image/png',
    'image/gif',
    'application/pdf',
    'application/msword',
    'application/vnd.openxmlformats-officedocument.wordprocessingml.document'
  ]
}

// 本地存储键名
export const STORAGE_KEYS = {
  TOKEN: 'token',
  USER: 'user',
  THEME: 'theme',
  LANGUAGE: 'language'
}

// 路由配置
export const ROUTES = {
  LOGIN: '/login',
  HOME: '/home',
  CHAT: '/chat',
  ITEMS: '/items',
  ADMIN: '/admin'
}

// 用户角色
export const USER_ROLES = {
  ADMIN: 'admin',
  USER: 'user'
}

// 消息类型
export const MESSAGE_TYPES = {
  USER: 'user',
  ASSISTANT: 'assistant',
  SYSTEM: 'system'
}

// 案例状态
export const ITEM_STATUS = {
  DRAFT: 0,
  PUBLISHED: 1,
  ARCHIVED: 2
}

// 请求超时时间
export const REQUEST_TIMEOUT = 30000

// 防抖延迟
export const DEBOUNCE_DELAY = 300

// 节流延迟
export const THROTTLE_DELAY = 1000
