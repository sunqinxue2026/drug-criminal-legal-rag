<template>
  <AdminShell active-path="/admin/users" breadcrumb="首页 / 用户管理">
    <section class="hero panel-card">
      <div>
        <p class="eyebrow">User Center</p>
        <h1>用户管理</h1>
        <p class="hero-text">当前页面全部基于真实用户表数据展示，支持新增、编辑、禁用和删除账号。</p>
      </div>
      <div class="hero-actions">
        <el-button round @click="loadUsers">刷新列表</el-button>
        <el-button type="primary" round :icon="Plus" @click="openCreateDialog">新增用户</el-button>
      </div>
    </section>

    <section class="stats-grid">
      <article class="stat-card panel-card">
        <span>账号总数</span>
        <strong>{{ users.length }}</strong>
        <small>当前用户表记录</small>
      </article>
      <article class="stat-card panel-card">
        <span>管理员</span>
        <strong>{{ adminCount }}</strong>
        <small>高权限账号</small>
      </article>
      <article class="stat-card panel-card">
        <span>启用账号</span>
        <strong>{{ activeCount }}</strong>
        <small>状态正常</small>
      </article>
    </section>

    <section class="toolbar panel-card">
      <div class="filter-grid">
        <el-input v-model="filters.username" placeholder="搜索用户名" clearable />
        <el-select v-model="filters.role" placeholder="角色筛选" clearable>
          <el-option label="管理员" value="admin" />
          <el-option label="普通用户" value="user" />
        </el-select>
        <el-button type="primary" @click="applyFilters">查询</el-button>
        <el-button @click="resetFilters">重置</el-button>
      </div>
    </section>

    <section class="panel-card table-panel">
      <el-table :data="pagedUsers" v-loading="loading">
        <el-table-column type="index" label="#" width="70" align="center" />
        <el-table-column prop="username" label="用户名" min-width="140" />
        <el-table-column prop="realName" label="姓名" min-width="120">
          <template #default="{ row }">{{ row.realName || '--' }}</template>
        </el-table-column>
        <el-table-column prop="email" label="邮箱" min-width="180">
          <template #default="{ row }">{{ row.email || '--' }}</template>
        </el-table-column>
        <el-table-column prop="phone" label="手机号" min-width="140">
          <template #default="{ row }">{{ row.phone || '--' }}</template>
        </el-table-column>
        <el-table-column label="角色" width="120" align="center">
          <template #default="{ row }">
            <el-tag :type="row.role === 'admin' ? 'danger' : 'primary'">
              {{ row.role === 'admin' ? '管理员' : '普通用户' }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column label="状态" width="120" align="center">
          <template #default="{ row }">
            <el-tag :type="normalizeStatus(row.status) === 1 ? 'success' : 'info'">
              {{ normalizeStatus(row.status) === 1 ? '正常' : '禁用' }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column label="创建时间" min-width="170">
          <template #default="{ row }">{{ formatDateTime(row.createTime) }}</template>
        </el-table-column>
        <el-table-column label="操作" width="260" fixed="right">
          <template #default="{ row }">
            <div class="row-actions">
              <el-button size="small" type="primary" plain :icon="Edit" @click="openEditDialog(row)">编辑</el-button>
              <el-button size="small" :type="normalizeStatus(row.status) === 1 ? 'warning' : 'success'" @click="toggleStatus(row)">
                {{ normalizeStatus(row.status) === 1 ? '禁用' : '启用' }}
              </el-button>
              <el-button size="small" type="danger" plain :icon="Delete" @click="removeUser(row)">删除</el-button>
            </div>
          </template>
        </el-table-column>
      </el-table>

      <div class="pagination-wrap">
        <el-pagination
          v-model:current-page="pagination.page"
          v-model:page-size="pagination.pageSize"
          :page-sizes="[10, 20, 50]"
          :total="filteredUsers.length"
          layout="total, sizes, prev, pager, next"
        />
      </div>
    </section>

    <el-dialog v-model="dialogVisible" :title="dialogMode === 'create' ? '新增用户' : '编辑用户'" width="560px">
      <el-form label-width="90px">
        <el-form-item label="用户名">
          <el-input v-model="form.username" :disabled="dialogMode === 'edit'" />
        </el-form-item>
        <el-form-item label="密码">
          <el-input v-model="form.password" type="password" show-password :placeholder="dialogMode === 'edit' ? '留空则不修改' : '请输入密码'" />
        </el-form-item>
        <el-form-item label="姓名">
          <el-input v-model="form.realName" />
        </el-form-item>
        <el-form-item label="邮箱">
          <el-input v-model="form.email" />
        </el-form-item>
        <el-form-item label="手机号">
          <el-input v-model="form.phone" />
        </el-form-item>
        <el-form-item label="角色">
          <el-select v-model="form.role" style="width: 100%">
            <el-option label="管理员" value="admin" />
            <el-option label="普通用户" value="user" />
          </el-select>
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="dialogVisible = false">取消</el-button>
        <el-button type="primary" :loading="submitting" @click="submitUser">保存</el-button>
      </template>
    </el-dialog>
  </AdminShell>
</template>

<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { Delete, Edit, Plus } from '@element-plus/icons-vue'
import { userApi } from '@/api/user'
import AdminShell from '@/components/admin/AdminShell.vue'
import type { User } from '@/types'

const loading = ref(false)
const submitting = ref(false)
const dialogVisible = ref(false)
const dialogMode = ref<'create' | 'edit'>('create')

const users = ref<User[]>([])
const filteredUsers = ref<User[]>([])

const filters = reactive({
  username: '',
  role: ''
})

const pagination = reactive({
  page: 1,
  pageSize: 10
})

const form = reactive<User>({
  id: undefined,
  username: '',
  password: '',
  realName: '',
  phone: '',
  email: '',
  role: 'user',
  status: 1
})

const pagedUsers = computed(() => {
  const start = (pagination.page - 1) * pagination.pageSize
  return filteredUsers.value.slice(start, start + pagination.pageSize)
})

const adminCount = computed(() => filteredUsers.value.filter((item) => item.role === 'admin').length)
const activeCount = computed(() => filteredUsers.value.filter((item) => normalizeStatus(item.status) === 1).length)

onMounted(loadUsers)

async function loadUsers() {
  loading.value = true
  try {
    const res = await userApi.getUserList()
    users.value = Array.isArray(res.data) ? res.data : []
    filteredUsers.value = [...users.value]
  } finally {
    loading.value = false
  }
}

function applyFilters() {
  const username = filters.username.trim().toLowerCase()
  filteredUsers.value = users.value.filter((item) => {
    return (!username || item.username.toLowerCase().includes(username))
      && (!filters.role || item.role === filters.role)
  })
  pagination.page = 1
}

function resetFilters() {
  filters.username = ''
  filters.role = ''
  filteredUsers.value = [...users.value]
  pagination.page = 1
}

function openCreateDialog() {
  dialogMode.value = 'create'
  Object.assign(form, {
    id: undefined,
    username: '',
    password: '',
    realName: '',
    phone: '',
    email: '',
    role: 'user',
    status: 1
  })
  dialogVisible.value = true
}

function openEditDialog(user: User) {
  dialogMode.value = 'edit'
  Object.assign(form, {
    id: user.id,
    username: user.username,
    password: '',
    realName: user.realName || '',
    phone: user.phone || '',
    email: user.email || '',
    role: user.role || 'user',
    status: normalizeStatus(user.status)
  })
  dialogVisible.value = true
}

async function submitUser() {
  if (!form.username) {
    ElMessage.warning('请输入用户名')
    return
  }
  if (dialogMode.value === 'create' && !form.password) {
    ElMessage.warning('请输入密码')
    return
  }

  submitting.value = true
  try {
    if (dialogMode.value === 'create') {
      await userApi.createUser(form)
      ElMessage.success('用户已新增')
    } else if (form.id) {
      await userApi.updateUserById(form.id, form)
      ElMessage.success('用户已更新')
    }
    dialogVisible.value = false
    await loadUsers()
    applyFilters()
  } finally {
    submitting.value = false
  }
}

async function toggleStatus(user: User) {
  const nextStatus = normalizeStatus(user.status) === 1 ? 0 : 1
  await userApi.updateUserStatus(user.id!, nextStatus)
  ElMessage.success('状态已更新')
  await loadUsers()
  applyFilters()
}

async function removeUser(user: User) {
  await ElMessageBox.confirm(`确认删除用户“${user.username}”吗？`, '提示', { type: 'warning' })
  await userApi.deleteUser(user.id!)
  ElMessage.success('用户已删除')
  await loadUsers()
  applyFilters()
}

function normalizeStatus(status?: number | string) {
  return status === 1 || status === '1' ? 1 : 0
}

function formatDateTime(value?: string) {
  if (!value) return '--'
  const date = new Date(value)
  if (Number.isNaN(date.getTime())) return value
  const year = date.getFullYear()
  const month = `${date.getMonth() + 1}`.padStart(2, '0')
  const day = `${date.getDate()}`.padStart(2, '0')
  const hours = `${date.getHours()}`.padStart(2, '0')
  const minutes = `${date.getMinutes()}`.padStart(2, '0')
  return `${year}-${month}-${day} ${hours}:${minutes}`
}
</script>

<style scoped>
.hero,
.toolbar,
.table-panel,
.stat-card {
  border-radius: 20px;
}

.hero {
  padding: 24px 28px;
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 16px;
}

.eyebrow {
  margin: 0 0 8px;
  color: var(--brand);
  text-transform: uppercase;
  letter-spacing: 0.18em;
  font-size: 12px;
}

.hero h1 {
  margin: 0;
  font-size: 28px;
}

.hero-text {
  margin: 10px 0 0;
  max-width: 640px;
  color: var(--text-soft);
  line-height: 1.65;
}

.hero-actions,
.row-actions {
  display: flex;
  gap: 10px;
}

.stats-grid {
  display: grid;
  grid-template-columns: repeat(3, minmax(0, 1fr));
  gap: 16px;
}

.stat-card {
  padding: 18px 20px;
}

.stat-card span,
.stat-card small {
  display: block;
  color: var(--text-soft);
}

.stat-card strong {
  display: block;
  margin: 10px 0 4px;
  font-size: 28px;
}

.toolbar {
  padding: 18px 20px;
}

.filter-grid {
  display: grid;
  grid-template-columns: 1.3fr 0.8fr auto auto;
  gap: 10px;
}

.table-panel {
  padding: 4px 16px 16px;
}

.table-panel :deep(.el-table) {
  font-size: 14px;
}

.table-panel :deep(.el-table th),
.table-panel :deep(.el-table td) {
  padding: 10px 0;
}

.pagination-wrap {
  margin-top: 14px;
  display: flex;
  justify-content: flex-end;
}

@media (max-width: 1280px) {
  .stats-grid,
  .filter-grid {
    grid-template-columns: repeat(2, minmax(0, 1fr));
  }

  .hero {
    flex-direction: column;
    align-items: flex-start;
  }
}

@media (max-width: 900px) {
  .stats-grid,
  .filter-grid {
    grid-template-columns: 1fr;
  }
}
</style>
