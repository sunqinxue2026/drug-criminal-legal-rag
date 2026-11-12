<template>
  <div class="admin-page">
    <aside class="admin-sidebar panel-card">
      <div class="sidebar-brand" @click="router.push('/admin')">
        <div class="brand-icon">
          <el-icon><ScaleToOriginal /></el-icon>
        </div>
        <div class="brand-copy">
          <strong>{{ brandTitle }}</strong>
          <span>{{ brandSubtitle }}</span>
        </div>
      </div>

      <nav class="sidebar-nav">
        <section
          v-for="section in navSections"
          :key="section.label"
          class="nav-section"
        >
          <p class="nav-section-title">{{ section.label }}</p>
          <div class="nav-section-items">
            <button
              v-for="item in section.items"
              :key="item.path"
              class="nav-item"
              :class="{ active: item.path === activePath }"
              @click="router.push(item.path)"
            >
              <el-icon><component :is="item.icon" /></el-icon>
              {{ item.label }}
            </button>
          </div>
        </section>
      </nav>
    </aside>

    <section class="admin-content">
      <header class="admin-header panel-card">
        <div class="header-left">
          <el-icon><Menu /></el-icon>
          <el-breadcrumb separator="/">
            <el-breadcrumb-item
              v-for="(item, index) in breadcrumbItems"
              :key="`${item.label}-${index}`"
              :to="item.path ? { path: item.path } : undefined"
            >
              {{ item.label }}
            </el-breadcrumb-item>
          </el-breadcrumb>
        </div>
        <div class="header-right">
          <span class="status-badge">
            <span class="dot"></span>
            {{ serviceStatusText }}
          </span>
          <span class="status-badge">{{ systemStatusText }}</span>
          <div class="user-chip" @click="router.push('/profile')">
            <el-avatar :size="36">{{ displayUserName.slice(0, 1).toUpperCase() }}</el-avatar>
            <span>{{ displayUserName }}</span>
          </div>
        </div>
      </header>

      <main class="page-body">
        <slot />
      </main>
    </section>
  </div>
</template>

<script setup lang="ts">
import type { Component } from 'vue'
import { computed } from 'vue'
import { useRouter } from 'vue-router'
import {
  Clock,
  DataBoard,
  Document,
  Files,
  Folder,
  Menu,
  Operation,
  ScaleToOriginal,
  User
} from '@element-plus/icons-vue'
import { useUserStore } from '@/stores/user'

type NavItem = {
  path: string
  label: string
  icon: Component
}

type NavSection = {
  label: string
  items: NavItem[]
}

const props = defineProps<{
  activePath: string
  breadcrumb: string
}>()

const router = useRouter()
const userStore = useUserStore()

const brandTitle = '\u0041\u0049\u6d89\u6bd2\u5211\u4e8b\u666e\u6cd5\u7cfb\u7edf'
const brandSubtitle = '\u4e13\u9898\u8bed\u6599\u5e93\u7ba1\u7406\u53f0'
const serviceStatusText = '\u7b97\u6cd5\u670d\u52a1\u5728\u7ebf'
const systemStatusText = '\u7cfb\u7edf\u6b63\u5e38'

const displayUserName = computed(() => userStore.user?.username || 'admin')

const breadcrumbItems = computed(() => {
  const labels = props.breadcrumb
    .split('/')
    .map((item) => item.trim())
    .filter(Boolean)

  return labels.map((label, index) => ({
    label,
    path: index === 0 ? '/home' : ''
  }))
})

const navSections: NavSection[] = [
  {
    label: '\u603b\u89c8',
    items: [
      { path: '/admin', label: '\u7ba1\u7406\u63a7\u5236\u53f0', icon: DataBoard }
    ]
  },
  {
    label: '\u8bed\u6599\u6cbb\u7406',
    items: [
      { path: '/admin/categories', label: '\u4e13\u9898\u5206\u7c7b\u7ba1\u7406', icon: Folder },
      { path: '/admin/items', label: '\u6cd5\u5f8b\u6848\u4ef6\u7ba1\u7406', icon: Document },
      { path: '/admin/legal-documents', label: '\u6cd5\u5f8b\u6587\u4e66\u7ba1\u7406', icon: Files },
      { path: '/admin/legal-knowledge', label: '\u6cd5\u5f8b\u77e5\u8bc6\u7ba1\u7406', icon: Document }
    ]
  },
  {
    label: '\u68c0\u7d22\u8d28\u91cf',
    items: [
      { path: '/admin/indexes', label: '\u7d22\u5f15\u4e0e\u53ec\u56de\u7ba1\u7406', icon: Operation }
    ]
  },
  {
    label: '\u7cfb\u7edf\u8fd0\u8425',
    items: [
      { path: '/admin/users', label: '\u7528\u6237\u7ba1\u7406', icon: User },
      { path: '/admin/user-actions', label: '\u7528\u6237\u884c\u4e3a\u5386\u53f2\u7ba1\u7406', icon: Clock }
    ]
  }
]
</script>

<style scoped>
.admin-page {
  min-height: 100vh;
  display: grid;
  grid-template-columns: clamp(256px, 18vw, 292px) minmax(0, 1fr);
  gap: 14px;
  padding: 14px;
  background: transparent;
}

.admin-sidebar,
.admin-header {
  border-radius: var(--radius-xl);
}

.admin-sidebar {
  padding: 18px 14px;
  display: flex;
  flex-direction: column;
  min-width: 0;
}

.sidebar-brand {
  display: flex;
  align-items: flex-start;
  gap: 12px;
  padding: 10px 10px 18px;
  cursor: pointer;
}

.brand-copy {
  display: grid;
  gap: 4px;
  min-width: 0;
  flex: 1;
}

.brand-icon {
  width: 44px;
  height: 44px;
  border-radius: 12px;
  background: linear-gradient(135deg, var(--brand), var(--brand-2));
  color: #fff;
  display: grid;
  place-items: center;
  flex-shrink: 0;
}

.sidebar-brand strong,
.sidebar-brand span,
.user-chip span {
  display: block;
}

.sidebar-brand strong {
  font-size: 16px;
  line-height: 1.4;
  white-space: normal;
  overflow-wrap: anywhere;
}

.sidebar-brand span {
  color: var(--text-soft);
  font-size: 12px;
  line-height: 1.6;
  white-space: normal;
}

.sidebar-nav {
  display: flex;
  flex-direction: column;
  gap: 14px;
}

.nav-section {
  display: grid;
  gap: 8px;
}

.nav-section-title {
  margin: 0;
  padding: 0 12px;
  font-size: 11px;
  letter-spacing: 0.08em;
  color: var(--text-soft);
  text-transform: uppercase;
}

.nav-section-items {
  display: grid;
  gap: 6px;
}

.nav-item {
  border: none;
  border-radius: var(--radius-md);
  background: transparent;
  color: var(--text-muted);
  display: flex;
  align-items: center;
  gap: 10px;
  padding: 11px 13px;
  text-align: left;
  cursor: pointer;
  transition: 0.24s ease;
  font-size: 13px;
}

.nav-item:hover {
  background: #edf3ff;
  color: var(--brand);
}

.nav-item.active {
  background: linear-gradient(135deg, var(--brand), #5994ff);
  color: #fff;
  box-shadow: 0 14px 28px rgba(62, 123, 250, 0.22);
}

.admin-content {
  display: flex;
  flex-direction: column;
  gap: 14px;
  min-width: 0;
}

.admin-header {
  min-height: 68px;
  padding: 0 18px;
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 14px;
}

.header-left,
.header-right,
.user-chip {
  display: flex;
  align-items: center;
  gap: 12px;
}

.header-left {
  color: var(--text-muted);
  min-width: 0;
}

.header-left :deep(.el-breadcrumb) {
  min-width: 0;
}

.header-left :deep(.el-breadcrumb__inner) {
  white-space: normal;
}

.header-right {
  min-width: 0;
  flex-wrap: wrap;
  justify-content: flex-end;
}

.status-badge {
  display: inline-flex;
  align-items: center;
  gap: 8px;
  padding: 8px 12px;
  border-radius: 999px;
  background: #f4fbf6;
  color: #1b7f46;
  font-size: 13px;
}

.dot {
  width: 8px;
  height: 8px;
  border-radius: 50%;
  background: #22c55e;
}

.user-chip {
  padding: 8px 12px;
  border-radius: 999px;
  background: #f8fbff;
  cursor: pointer;
  font-size: 13px;
}

.page-body {
  display: flex;
  flex-direction: column;
  gap: 14px;
  min-width: 0;
}

@media (max-width: 1440px) {
  .admin-page {
    grid-template-columns: 248px minmax(0, 1fr);
  }

  .admin-header {
    padding: 0 16px;
  }

  .header-right {
    gap: 8px;
  }

  .status-badge {
    padding: 6px 10px;
    font-size: 12px;
  }
}

@media (max-width: 1280px) {
  .admin-page {
    grid-template-columns: 1fr;
  }

  .admin-header {
    min-height: auto;
    padding: 14px 16px;
    align-items: flex-start;
    gap: 12px;
  }

  .header-right {
    width: 100%;
    justify-content: space-between;
    flex-wrap: wrap;
  }
}
</style>
