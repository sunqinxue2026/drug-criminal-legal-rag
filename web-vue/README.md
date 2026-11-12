# Vue 前端

本目录是项目中的前端应用，负责用户交互、页面路由、咨询会话展示、管理后台页面以及与 Spring Boot 后端的接口联调。

## 技术栈

- Vue 3.5
- TypeScript
- Vite 6
- Element Plus
- Pinia
- Vue Router
- Axios
- ECharts
- Playwright

## 模块职责

- 提供登录、注册、首页、个人中心等用户页面
- 提供涉毒刑事专题咨询界面
- 提供案例浏览、详情和收藏页面
- 提供管理后台页面，包括用户、分类、案例、法律知识、法律文书、索引和用户行为记录
- 管理 JWT 登录态，并在请求时自动附带 `Authorization` 头

## 启动方式

```powershell
cd D:\CODE\RAG\web-vue
npm install
npm run dev
```

默认地址：

```text
http://localhost:5174
```

## 其他常用命令

```powershell
npm run build
npm run preview
npm run test:e2e
```

## 开发代理

开发环境中，Vite 会将前端的 `/api` 请求代理到：

```text
http://localhost:8080
```

因此前端联调时，通常至少需要启动：

- `web-springboot`
- `web-flask`

如果需要上传图片或文书，还需要启动：

- `web-file`

## 主要页面

### 用户侧

- `/login`
- `/register`
- `/home`
- `/profile`
- `/chat`
- `/items`
- `/item/:id`
- `/favorites`

### 管理侧

- `/admin`
- `/admin/users`
- `/admin/categories`
- `/admin/items`
- `/admin/legal-documents`
- `/admin/legal-knowledge`
- `/admin/indexes`
- `/admin/user-actions`

## 状态管理与鉴权

前端通过 Pinia 管理当前用户与 token：

- 登录成功后保存 token 和用户信息到 `localStorage`
- 请求拦截器自动附带 `Authorization: Bearer <token>`
- 当接口返回 `401` 时，前端会清空登录态并跳转到登录页

## 目录结构

```text
src/
├── api/          # API 封装
├── assets/       # 静态资源
├── components/   # 通用组件与管理端组件
├── config/       # 常量与环境配置
├── directives/   # 自定义指令
├── router/       # 路由配置
├── stores/       # Pinia 状态管理
├── styles/       # 样式文件
├── types/        # TypeScript 类型
├── utils/        # 工具函数
├── views/        # 页面视图
├── App.vue
└── main.ts
```

## 默认账号

执行仓库根目录的 [init.sql](../docs/init.sql) 后可使用：

- 管理员：`admin / admin123`
- 普通用户：`user1 / 123456`
- 普通用户：`user2 / 123456`

## 当前说明

- 当前前端围绕“涉毒刑事法律风险分析与普法”专题设计，不是通用法律门户模板。
- 智能问答页面支持图片、文书和引用案例一并发送到后端参与分析。
- 若后端服务未启动，页面可以打开，但核心数据接口和咨询能力不可用。

