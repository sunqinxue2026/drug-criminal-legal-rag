<template>
  <AdminShell active-path="/admin/user-actions" breadcrumb="首页 / 用户行为历史管理">
    <section class="hero panel-card">
      <div>
        <p class="eyebrow">Behavior Insight</p>
        <h1>用户行为历史管理</h1>
        <p class="hero-text">
          这里汇总真实的咨询会话、咨询消息、案例浏览、案例搜索和收藏行为，方便快速定位关键操作记录与用户使用趋势。
        </p>
      </div>
      <div class="hero-actions">
        <el-button round @click="loadActions">刷新记录</el-button>
      </div>
    </section>

    <section class="stats-grid">
      <article class="stat-card panel-card">
        <span>浏览次数</span>
        <strong>{{ stats.totalViews }}</strong>
        <small>案例详情页访问量</small>
      </article>
      <article class="stat-card panel-card">
        <span>咨询次数</span>
        <strong>{{ stats.totalChats }}</strong>
        <small>AI律师咨询会话数</small>
      </article>
      <article class="stat-card panel-card">
        <span>搜索次数</span>
        <strong>{{ stats.totalSearches }}</strong>
        <small>包含案例检索与 AI律师咨询页引用搜索</small>
      </article>
      <article class="stat-card panel-card">
        <span>收藏次数</span>
        <strong>{{ stats.totalFavorites }}</strong>
        <small>用户收藏与取消收藏行为</small>
      </article>
    </section>

    <section class="toolbar panel-card">
      <el-form :inline="true" class="toolbar-form">
        <el-form-item label="用户名">
          <el-input v-model="searchForm.username" placeholder="搜索用户" clearable />
        </el-form-item>
        <el-form-item label="行为类型">
          <el-select v-model="searchForm.actionType" placeholder="全部行为" clearable class="toolbar-select">
            <el-option label="咨询会话" value="chat" />
            <el-option label="咨询消息" value="message" />
            <el-option label="案例浏览" value="view" />
            <el-option label="案例搜索" value="search" />
            <el-option label="收藏案例" value="favorite" />
            <el-option label="取消收藏" value="unfavorite" />
          </el-select>
        </el-form-item>
        <el-form-item label="时间范围">
          <el-date-picker
            v-model="searchForm.dateRange"
            type="daterange"
            range-separator="至"
            start-placeholder="开始日期"
            end-placeholder="结束日期"
            value-format="YYYY-MM-DD"
          />
        </el-form-item>
        <el-form-item>
          <el-button type="primary" :icon="Search" @click="handleSearch">查询</el-button>
          <el-button :icon="Refresh" @click="handleReset">重置</el-button>
        </el-form-item>
      </el-form>
    </section>

    <section class="panel-card table-panel">
      <el-table :data="actions" v-loading="loading">
        <el-table-column type="index" label="#" width="64" align="center" />
        <el-table-column prop="username" label="用户名" min-width="100" />
        <el-table-column prop="actionType" label="行为类型" width="108" align="center">
          <template #default="{ row }">
            <el-tag :type="getActionTypeColor(row.actionType)">
              {{ getActionTypeName(row.actionType) }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="actionTarget" label="操作对象" min-width="150" show-overflow-tooltip />
        <el-table-column prop="actionDetail" label="详细信息" min-width="220" show-overflow-tooltip />
        <el-table-column prop="ipAddress" label="IP 地址" width="120" />
        <el-table-column prop="userAgent" label="来源说明" min-width="130" show-overflow-tooltip />
        <el-table-column prop="createTime" label="操作时间" min-width="156" />
        <el-table-column label="操作" width="92" fixed="right">
          <template #default="{ row }">
            <el-button type="primary" plain size="small" :icon="View" @click="handleView(row)">详情</el-button>
          </template>
        </el-table-column>
      </el-table>

      <div class="pagination">
        <el-pagination
          v-model:current-page="pagination.page"
          v-model:page-size="pagination.size"
          :page-sizes="[10, 20, 50, 100]"
          :total="pagination.total"
          layout="total, sizes, prev, pager, next, jumper"
          @size-change="handleSizeChange"
          @current-change="handlePageChange"
        />
      </div>
    </section>

    <el-dialog v-model="detailDialogVisible" title="行为详情" width="720px">
      <div v-if="currentAction" class="detail-box">
        <el-descriptions :column="2" border>
          <el-descriptions-item label="用户名">{{ currentAction.username }}</el-descriptions-item>
          <el-descriptions-item label="行为类型">
            <el-tag :type="getActionTypeColor(currentAction.actionType)">
              {{ getActionTypeName(currentAction.actionType) }}
            </el-tag>
          </el-descriptions-item>
          <el-descriptions-item label="操作对象" :span="2">{{ currentAction.actionTarget || '--' }}</el-descriptions-item>
          <el-descriptions-item label="详细信息" :span="2">{{ currentAction.actionDetail || '--' }}</el-descriptions-item>
          <el-descriptions-item label="IP 地址">{{ currentAction.ipAddress || '--' }}</el-descriptions-item>
          <el-descriptions-item label="操作时间">{{ currentAction.createTime || '--' }}</el-descriptions-item>
          <el-descriptions-item label="来源说明" :span="2">{{ currentAction.userAgent || '--' }}</el-descriptions-item>
        </el-descriptions>
      </div>
    </el-dialog>
  </AdminShell>
</template>

<script setup lang="ts">
import { onMounted, reactive, ref } from 'vue'
import { ElMessage } from 'element-plus'
import { Refresh, Search, View } from '@element-plus/icons-vue'
import AdminShell from '@/components/admin/AdminShell.vue'
import { userActionApi } from '@/api/userAction'
import type { UserActionListParams, UserActionRecord, UserActionStats } from '@/api/userAction'

type ActionType = 'chat' | 'message' | 'view' | 'search' | 'favorite' | 'unfavorite'

type SearchForm = {
  username: string
  actionType: ActionType | ''
  dateRange: string[]
}

const loading = ref(false)
const detailDialogVisible = ref(false)
const currentAction = ref<UserActionRecord | null>(null)

const searchForm = reactive<SearchForm>({
  username: '',
  actionType: '',
  dateRange: []
})

const pagination = reactive({
  page: 1,
  size: 10,
  total: 0
})

const stats = reactive<UserActionStats>({
  totalViews: 0,
  totalChats: 0,
  totalSearches: 0,
  totalFavorites: 0
})

const actions = ref<UserActionRecord[]>([])

onMounted(() => {
  loadStats()
  loadActions()
})

function resolveApiMessage(error: unknown, fallback: string) {
  if (error instanceof Error) {
    return error.message
  }
  return fallback
}

const loadStats = async () => {
  try {
    const res = await userActionApi.getStats()
    Object.assign(stats, res.data)
  } catch (error) {
    console.error(error)
  }
}

const loadActions = async () => {
  loading.value = true
  try {
    const params: UserActionListParams = {
      page: pagination.page,
      size: pagination.size
    }

    if (searchForm.username.trim()) {
      params.username = searchForm.username.trim()
    }
    if (searchForm.actionType) {
      params.actionType = searchForm.actionType
    }
    if (searchForm.dateRange.length === 2) {
      params.startDate = searchForm.dateRange[0]
      params.endDate = searchForm.dateRange[1]
    }

    const res = await userActionApi.getList(params)
    actions.value = res.data.records || []
    pagination.total = Number(res.data.total || 0)
  } catch (error) {
    ElMessage.error(`加载行为列表失败：${resolveApiMessage(error, '未知错误')}`)
  } finally {
    loading.value = false
  }
}

const handleSearch = () => {
  pagination.page = 1
  loadActions()
}

const handleReset = () => {
  searchForm.username = ''
  searchForm.actionType = ''
  searchForm.dateRange = []
  pagination.page = 1
  loadActions()
}

const handleView = (row: UserActionRecord) => {
  currentAction.value = row
  detailDialogVisible.value = true
}

const getActionTypeName = (type: string) => {
  const typeMap: Record<string, string> = {
    chat: '咨询会话',
    message: '咨询消息',
    view: '案例浏览',
    search: '案例搜索',
    favorite: '收藏案例',
    unfavorite: '取消收藏'
  }
  return typeMap[type] || type
}

const getActionTypeColor = (type: string) => {
  const colorMap: Record<string, string> = {
    chat: 'warning',
    message: 'primary',
    view: 'info',
    search: 'success',
    favorite: 'danger',
    unfavorite: 'info'
  }
  return colorMap[type] || ''
}

const handleSizeChange = (size: number) => {
  pagination.size = size
  loadActions()
}

const handlePageChange = (page: number) => {
  pagination.page = page
  loadActions()
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
  line-height: 1.2;
}

.hero-text {
  margin: 10px 0 0;
  max-width: 640px;
  color: var(--text-soft);
  line-height: 1.65;
}

.hero-actions {
  display: flex;
  gap: 10px;
}

.stats-grid {
  display: grid;
  grid-template-columns: repeat(4, minmax(0, 1fr));
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
  padding: 18px 20px 4px;
}

.toolbar-form {
  display: grid;
  grid-template-columns: minmax(180px, 1fr) minmax(160px, 180px) minmax(320px, 1.2fr) auto;
  gap: 10px 14px;
  align-items: end;
}

.toolbar-form :deep(.el-form-item) {
  margin-right: 0;
  margin-bottom: 14px;
}

.toolbar-form :deep(.el-form-item__content) {
  width: 100%;
}

.toolbar-form :deep(.el-input),
.toolbar-form :deep(.el-select),
.toolbar-form :deep(.el-date-editor) {
  width: 100%;
}

.table-panel {
  padding: 16px;
}

.pagination {
  margin-top: 16px;
  display: flex;
  justify-content: flex-end;
}

.detail-box {
  padding-top: 8px;
}

@media (max-width: 1280px) {
  .stats-grid {
    grid-template-columns: repeat(2, minmax(0, 1fr));
  }

  .toolbar-form {
    grid-template-columns: 1fr 1fr;
  }
}

@media (max-width: 900px) {
  .hero {
    flex-direction: column;
    align-items: flex-start;
  }

  .stats-grid,
  .toolbar-form {
    grid-template-columns: 1fr;
  }
}
</style>
