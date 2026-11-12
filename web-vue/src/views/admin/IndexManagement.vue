<template>
  <AdminShell active-path="/admin/indexes" breadcrumb="首页 / 索引与召回管理">
    <section class="hero panel-card">
      <div>
        <p class="eyebrow">RAG Index</p>
        <h1>索引与召回管理</h1>
        <p class="hero-text">
          这里集中展示 RAG 检索链路的运行状态，支持查看索引容量、测试召回结果、重建向量索引，并校验当前案例库是否已经同步进入知识库。
        </p>
      </div>
      <div class="hero-actions">
        <el-button round @click="loadIndexes">刷新索引</el-button>
        <el-button type="primary" round :icon="Plus" @click="showCreateDialog">创建索引</el-button>
      </div>
    </section>

    <section class="stats-grid">
      <article class="stat-card panel-card">
        <span>索引总数</span>
        <strong>{{ indexStats.totalIndexes }}</strong>
        <small>当前在线的向量索引集合</small>
      </article>
      <article class="stat-card panel-card">
        <span>来源条目数</span>
        <strong>{{ indexStats.totalSources }}</strong>
        <small>按案例、文书、知识记录去重后的总量</small>
      </article>
      <article class="stat-card panel-card">
        <span>向量总数</span>
        <strong>{{ indexStats.totalVectors }}</strong>
        <small>已经进入索引的 chunk / vector 总量</small>
      </article>
      <article class="stat-card panel-card">
        <span>默认维度</span>
        <strong>{{ indexStats.dimension }}</strong>
        <small>当前 embedding 维度</small>
      </article>
      <article class="stat-card panel-card">
        <span>最近更新</span>
        <strong class="time">{{ indexStats.lastUpdate }}</strong>
        <small>{{ lastRebuildSummary || '等待下一次重建索引' }}</small>
      </article>
    </section>

    <section class="panel-card table-panel">
      <div class="table-head">
        <div>
          <h3>索引列表</h3>
          <p>统一管理向量库，执行测试检索并检查召回质量。</p>
        </div>
      </div>

      <el-table :data="indexes" v-loading="loading">
        <el-table-column prop="name" label="索引名称" min-width="180">
          <template #default="{ row }">
            <el-tag type="primary" size="large">{{ row.name }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="count" label="向量数量" width="120" />
        <el-table-column prop="sourceCount" label="来源条目" width="120" />
        <el-table-column prop="dimension" label="向量维度" width="120" />
        <el-table-column prop="size" label="预估大小" width="120" />
        <el-table-column prop="createTime" label="创建时间" min-width="170" />
        <el-table-column prop="updateTime" label="更新时间" min-width="170" />
        <el-table-column label="操作" width="300" fixed="right">
          <template #default="{ row }">
            <el-button size="small" type="primary" plain :icon="Search" @click="testSearch(row)">测试检索</el-button>
            <el-button size="small" type="warning" plain :icon="Refresh" @click="rebuildIndex(row)">重建</el-button>
            <el-button size="small" type="danger" plain :icon="Delete" @click="deleteIndex(row)">删除</el-button>
          </template>
        </el-table-column>
      </el-table>
    </section>

    <el-dialog v-model="searchDialogVisible" title="索引测试检索" width="860px">
      <div class="search-test">
        <el-form :model="searchForm" label-width="90px">
          <el-form-item label="索引名称">
            <el-tag type="primary">{{ currentIndex?.name }}</el-tag>
          </el-form-item>
          <el-form-item label="查询问题">
            <el-input v-model="searchForm.query" type="textarea" :rows="3" placeholder="请输入待检索的法律问题" />
          </el-form-item>
          <el-form-item label="返回数量">
            <el-input-number v-model="searchForm.topK" :min="1" :max="20" />
          </el-form-item>
          <el-form-item>
            <el-button type="primary" :loading="searching" @click="performSearch">开始检索</el-button>
          </el-form-item>
        </el-form>

        <div class="result-list">
          <el-empty v-if="!searchResults.length" description="暂无检索结果" />
          <article v-for="(result, index) in searchResults" :key="`${result.title || 'snippet'}-${index}`" class="result-card">
            <div class="result-top">
              <div>
                <strong>#{{ index + 1 }} {{ result.title || '法律片段' }}</strong>
                <p>{{ result.contentPreview || result.content || '暂无摘要' }}</p>
              </div>
              <el-tag :type="getScoreType(result.score)">相似度 {{ result.score.toFixed(4) }}</el-tag>
            </div>
            <div class="result-meta">
              <span v-if="result.itemId">案例 ID #{{ result.itemId }}</span>
              <span v-if="result.chunkIndex !== undefined">切片 {{ result.chunkIndex + 1 }}</span>
              <span>文本长度 {{ result.textLength }}</span>
            </div>
          </article>
        </div>
      </div>
    </el-dialog>

    <el-dialog v-model="createDialogVisible" title="创建索引" width="520px">
      <el-form :model="createForm" label-width="90px">
        <el-form-item label="索引名称">
          <el-input v-model="createForm.name" placeholder="例如：legal_cases_drug_crime" />
        </el-form-item>
        <el-form-item label="向量维度">
          <el-input-number v-model="createForm.dimension" :min="128" :max="2048" />
        </el-form-item>
        <el-form-item label="说明">
          <el-input v-model="createForm.description" type="textarea" :rows="3" placeholder="用于备注索引用途" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="createDialogVisible = false">取消</el-button>
        <el-button type="primary" @click="createIndex">创建</el-button>
      </template>
    </el-dialog>
  </AdminShell>
</template>

<script setup lang="ts">
import { onMounted, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { Delete, Plus, Refresh, Search } from '@element-plus/icons-vue'
import axios from 'axios'
import AdminShell from '@/components/admin/AdminShell.vue'
import { ENV } from '@/config/constants'

type VectorIndexApiRecord = {
  name: string
  count?: number
  sourceCount?: number
  dimension?: number
  createTime?: string
  updateTime?: string
}

type VectorIndex = VectorIndexApiRecord & {
  size: string
}

type VectorSearchResultApi = {
  title?: string
  content_preview?: string
  content?: string
  score?: number
  item_id?: number
  chunk_index?: number
  text_length?: number
}

type VectorSearchResult = {
  title?: string
  contentPreview?: string
  content?: string
  score: number
  itemId?: number
  chunkIndex?: number
  textLength: number
}

type VectorApiResponse<T> = {
  code: number
  message?: string
  data?: T
}

type IndexStats = {
  totalIndexes: number
  totalSources: number
  totalVectors: number
  dimension: number
  lastUpdate: string
}

type SearchForm = {
  query: string
  topK: number
}

type CreateForm = {
  name: string
  dimension: number
  description: string
}

type RebuildResponse = {
  count?: number
  items_count?: number
  message?: string
}

const loading = ref(false)
const searching = ref(false)
const searchDialogVisible = ref(false)
const createDialogVisible = ref(false)
const lastRebuildSummary = ref('')

const indexStats = ref<IndexStats>({
  totalIndexes: 0,
  totalSources: 0,
  totalVectors: 0,
  dimension: 1024,
  lastUpdate: '--'
})

const indexes = ref<VectorIndex[]>([])
const currentIndex = ref<VectorIndex | null>(null)
const searchResults = ref<VectorSearchResult[]>([])

const searchForm = ref<SearchForm>({
  query: '',
  topK: 5
})

const createForm = ref<CreateForm>({
  name: '',
  dimension: 1024,
  description: ''
})

const flaskApi = ENV.ALGORITHM_API_BASE_URL

onMounted(() => {
  loadIndexes()
})

function resolveApiMessage(error: unknown, fallback: string) {
  if (axios.isAxiosError<VectorApiResponse<unknown>>(error)) {
    return error.response?.data?.message || error.message || fallback
  }
  if (error instanceof Error) {
    return error.message
  }
  return fallback
}

function normalizeVectorIndex(record: VectorIndexApiRecord): VectorIndex {
  return {
    ...record,
    sourceCount: Number(record.sourceCount || 0),
    count: Number(record.count || 0),
    dimension: Number(record.dimension || 0),
    size: formatSize(Number(record.count || 0) * Math.max(Number(record.dimension || 1), 1) * 4),
    createTime: record.createTime || '--',
    updateTime: record.updateTime || '--'
  }
}

const loadIndexes = async () => {
  loading.value = true
  try {
    const res = await axios.get<VectorApiResponse<VectorIndexApiRecord[]>>(`${flaskApi}/api/vector/indexes`)
    if (res.data.code !== 200) {
      throw new Error(res.data.message || '加载索引失败')
    }

    indexes.value = (res.data.data || []).map(normalizeVectorIndex)
    indexStats.value.totalIndexes = indexes.value.length
    indexStats.value.totalSources = indexes.value.reduce((sum, index) => sum + Number(index.sourceCount || 0), 0)
    indexStats.value.totalVectors = indexes.value.reduce((sum, index) => sum + Number(index.count || 0), 0)
    indexStats.value.dimension = indexes.value[0]?.dimension || 1024
    indexStats.value.lastUpdate = new Date().toLocaleString('zh-CN')
  } catch (error) {
    ElMessage.error(`加载索引失败：${resolveApiMessage(error, '加载索引失败')}`)
  } finally {
    loading.value = false
  }
}

const testSearch = (index: VectorIndex) => {
  currentIndex.value = index
  searchDialogVisible.value = true
  searchResults.value = []
  searchForm.value.query = ''
}

const performSearch = async () => {
  if (!searchForm.value.query.trim() || !currentIndex.value) {
    ElMessage.warning('请输入查询内容')
    return
  }

  searching.value = true
  try {
    const res = await axios.post<VectorApiResponse<VectorSearchResultApi[]>>(`${flaskApi}/api/vector/search`, {
      index_name: currentIndex.value.name,
      query: searchForm.value.query,
      top_k: searchForm.value.topK
    })

    if (res.data.code !== 200) {
      throw new Error(res.data.message || '检索失败')
    }

    searchResults.value = (res.data.data || []).map((result) => ({
      title: result.title,
      contentPreview: result.content_preview,
      content: result.content,
      score: Number(result.score || 0),
      itemId: result.item_id,
      chunkIndex: result.chunk_index,
      textLength: Number(result.text_length || 0)
    }))
    ElMessage.success(`检索完成，共返回 ${searchResults.value.length} 条结果`)
  } catch (error) {
    ElMessage.error(`检索失败：${resolveApiMessage(error, '检索失败')}`)
  } finally {
    searching.value = false
  }
}

const rebuildIndex = async (index: VectorIndex) => {
  try {
    await ElMessageBox.confirm(
      `确定要重建索引“${index.name}”吗？这会重新处理切片和向量索引。`,
      '确认重建',
      {
        confirmButtonText: '确定',
        cancelButtonText: '取消',
        type: 'warning'
      }
    )

    loading.value = true
    const res = await axios.post<VectorApiResponse<RebuildResponse>>(`${flaskApi}/api/vector/rebuild`, { index_name: index.name })
    if (res.data.code !== 200) {
      throw new Error(res.data.message || '重建索引失败')
    }

    const rebuildData = res.data.data || {}
    lastRebuildSummary.value = `最近一次重建：${rebuildData.items_count || 0} 个来源 / ${rebuildData.count || 0} 个向量`
    ElMessage.success(rebuildData.message || '索引重建成功')
    await loadIndexes()
  } catch (error) {
    if (error !== 'cancel' && error !== 'close') {
      ElMessage.error(`重建索引失败：${resolveApiMessage(error, '重建索引失败')}`)
    }
  } finally {
    loading.value = false
  }
}

const deleteIndex = async (index: VectorIndex) => {
  try {
    await ElMessageBox.confirm(
      `确定要删除索引“${index.name}”吗？此操作不可恢复。`,
      '确认删除',
      {
        confirmButtonText: '确定',
        cancelButtonText: '取消',
        type: 'error'
      }
    )

    const res = await axios.delete<VectorApiResponse<null>>(`${flaskApi}/api/vector/index/${index.name}`)
    if (res.data.code !== 200) {
      throw new Error(res.data.message || '删除索引失败')
    }

    ElMessage.success('索引删除成功')
    await loadIndexes()
  } catch (error) {
    if (error !== 'cancel' && error !== 'close') {
      ElMessage.error(`删除索引失败：${resolveApiMessage(error, '删除索引失败')}`)
    }
  }
}

const showCreateDialog = () => {
  createForm.value = {
    name: '',
    dimension: 1024,
    description: ''
  }
  createDialogVisible.value = true
}

const createIndex = async () => {
  if (!createForm.value.name.trim()) {
    ElMessage.warning('请输入索引名称')
    return
  }

  try {
    const res = await axios.post<VectorApiResponse<null>>(`${flaskApi}/api/vector/create`, createForm.value)
    if (res.data.code !== 200) {
      throw new Error(res.data.message || '创建索引失败')
    }

    ElMessage.success('索引创建成功')
    createDialogVisible.value = false
    await loadIndexes()
  } catch (error) {
    ElMessage.error(`创建索引失败：${resolveApiMessage(error, '创建索引失败')}`)
  }
}

const formatSize = (bytes: number) => {
  if (bytes < 1024) return `${bytes} B`
  if (bytes < 1024 * 1024) return `${(bytes / 1024).toFixed(2)} KB`
  return `${(bytes / (1024 * 1024)).toFixed(2)} MB`
}

const getScoreType = (score: number) => {
  if (score > 0.85) return 'success'
  if (score > 0.6) return 'warning'
  return 'info'
}
</script>

<style scoped>
.hero,
.table-panel,
.stat-card,
.result-card {
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
  max-width: 660px;
  color: var(--text-soft);
  line-height: 1.65;
}

.hero-actions {
  display: flex;
  gap: 10px;
}

.stats-grid {
  display: grid;
  grid-template-columns: repeat(auto-fit, minmax(180px, 1fr));
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

.stat-card strong.time {
  font-size: 18px;
}

.table-panel {
  padding: 16px;
}

.table-panel :deep(.el-table) {
  font-size: 14px;
}

.table-panel :deep(.el-table th),
.table-panel :deep(.el-table td) {
  padding: 10px 0;
}

.table-head {
  margin-bottom: 14px;
}

.table-head h3,
.table-head p {
  margin: 0;
}

.table-head p {
  margin-top: 8px;
  color: var(--text-soft);
}

.search-test {
  display: flex;
  flex-direction: column;
  gap: 18px;
}

.result-list {
  display: grid;
  gap: 14px;
}

.result-card {
  padding: 16px;
  background: #f8fbff;
}

.result-top {
  display: flex;
  justify-content: space-between;
  gap: 16px;
}

.result-top strong {
  display: block;
}

.result-top p {
  margin: 6px 0 0;
  color: var(--text-soft);
  line-height: 1.8;
}

.result-meta {
  margin-top: 12px;
  display: flex;
  gap: 10px;
  flex-wrap: wrap;
  color: var(--text-soft);
  font-size: 12px;
}

@media (max-width: 1280px) {
  .stats-grid {
    grid-template-columns: repeat(2, minmax(0, 1fr));
  }

  .hero {
    flex-direction: column;
    align-items: flex-start;
  }
}

@media (max-width: 900px) {
  .stats-grid {
    grid-template-columns: 1fr;
  }
}
</style>
