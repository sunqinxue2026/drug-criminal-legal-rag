import { createRouter, createWebHistory } from 'vue-router'
import { useUserStore } from '@/stores/user'

const router = createRouter({
  history: createWebHistory(import.meta.env.BASE_URL),
  routes: [
    {
      path: '/',
      redirect: '/home'
    },
    {
      path: '/login',
      name: 'Login',
      component: () => import('@/views/user/Login.vue'),
      meta: { requiresAuth: false }
    },
    {
      path: '/register',
      name: 'Register',
      component: () => import('@/views/user/Register.vue'),
      meta: { requiresAuth: false }
    },
    {
      path: '/home',
      name: 'Home',
      component: () => import('@/views/user/Home.vue'),
      meta: { requiresAuth: true }
    },
    {
      path: '/profile',
      name: 'Profile',
      component: () => import('@/views/user/Profile.vue'),
      meta: { requiresAuth: true }
    },
    {
      path: '/chat',
      alias: ['/user/chat'],
      name: 'Chat',
      component: () => import('@/views/chat/ChatView.vue'),
      meta: { requiresAuth: true }
    },
    {
      path: '/items',
      alias: ['/user/items'],
      name: 'ItemList',
      component: () => import('@/views/item/ItemList.vue'),
      meta: { requiresAuth: true }
    },
    {
      path: '/item/:id',
      name: 'ItemDetail',
      component: () => import('@/views/item/ItemDetail.vue'),
      meta: { requiresAuth: true }
    },
    {
      path: '/favorites',
      name: 'MyFavorites',
      component: () => import('@/views/user/MyFavorites.vue'),
      meta: { requiresAuth: true }
    },
    {
      path: '/document-generation',
      name: 'DocumentGeneration',
      component: () => import('@/views/user/DocumentGeneration.vue'),
      meta: { requiresAuth: true }
    },
    {
      path: '/admin',
      name: 'AdminDashboard',
      component: () => import('@/views/admin/Dashboard.vue'),
      meta: { requiresAuth: true, requiresAdmin: true }
    },
    {
      path: '/admin/users',
      name: 'UserManagement',
      component: () => import('@/views/admin/UserManagement.vue'),
      meta: { requiresAuth: true, requiresAdmin: true }
    },
    {
      path: '/admin/categories',
      name: 'CategoryManagement',
      component: () => import('@/views/admin/CategoryManagement.vue'),
      meta: { requiresAuth: true, requiresAdmin: true }
    },
    {
      path: '/admin/items',
      name: 'ItemManagement',
      component: () => import('@/views/admin/ItemManagement.vue'),
      meta: { requiresAuth: true, requiresAdmin: true }
    },
    {
      path: '/admin/legal-documents',
      name: 'LegalDocumentManagement',
      component: () => import('@/views/admin/LegalDocumentManagement.vue'),
      meta: { requiresAuth: true, requiresAdmin: true }
    },
    {
      path: '/admin/legal-knowledge',
      name: 'LegalKnowledgeManagement',
      component: () => import('@/views/admin/LegalKnowledgeManagement.vue'),
      meta: { requiresAuth: true, requiresAdmin: true }
    },
    {
      path: '/admin/indexes',
      name: 'IndexManagement',
      component: () => import('@/views/admin/IndexManagement.vue'),
      meta: { requiresAuth: true, requiresAdmin: true }
    },
    {
      path: '/admin/user-actions',
      name: 'UserActionHistory',
      component: () => import('@/views/admin/UserActionHistory.vue'),
      meta: { requiresAuth: true, requiresAdmin: true }
    }
  ]
})

router.beforeEach((to, _from, next) => {
  const userStore = useUserStore()

  if (to.meta.requiresAuth && !userStore.isLoggedIn) {
    next('/login')
    return
  }

  if (to.meta.requiresAdmin && userStore.user?.role !== 'admin') {
    next('/home')
    return
  }

  next()
})

export default router
