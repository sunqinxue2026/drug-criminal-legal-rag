<template>
  <AdminShell active-path="/admin/items" breadcrumb="首页 / 法律案件管理">
    <section class="hero panel-card">
      <div>
        <p class="eyebrow">Official Cases</p>
        <h1>法律案件管理</h1>
        <p class="hero-text">案例现在支持基础信息维护、官方案例同步和多模态附件绑定，方便前台直接展示图片、PDF 和证据材料。</p>
      </div>
      <div class="hero-actions">
        <el-button round @click="loadItems">刷新列表</el-button>
        <el-button type="success" round @click="openCreateDialog">新增案件</el-button>
        <el-button type="primary" round :loading="syncing" @click="syncOfficialCases">同步官方案例</el-button>
        <el-button round @click="openImportDialog">手动导入</el-button>
      </div>
    </section>

    <section class="stats-grid">
      <article class="stat-card panel-card">
        <span>案件总数</span>
        <strong>{{ total }}</strong>
        <small>当前筛选条件下返回</small>
      </article>
      <article class="stat-card panel-card">
        <span>附件覆盖</span>
        <strong>{{ assetCoverage }}</strong>
        <small>当前页已绑定材料的案例占比</small>
      </article>
      <article class="stat-card panel-card">
        <span>摘要完整数</span>
        <strong>{{ summaryCount }}</strong>
        <small>适合直接进入检索和问答</small>
      </article>
    </section>

    <section class="toolbar panel-card">
      <div class="filter-grid">
        <el-input v-model="filters.keyword" placeholder="输入标题、摘要或正文关键词" clearable @keyup.enter="applyFilters" />
        <el-select v-model="filters.categoryId" placeholder="选择专题分类" clearable>
          <el-option v-for="category in categories" :key="category.id" :label="category.name" :value="category.id" />
        </el-select>
        <el-input v-model="filters.tags" placeholder="输入标签关键词" clearable @keyup.enter="applyFilters" />
        <el-button type="primary" @click="applyFilters">搜索</el-button>
        <el-button @click="resetFilters">重置</el-button>
      </div>
    </section>

    <section class="panel-card table-panel">
      <el-table :data="items" v-loading="loading">
        <el-table-column label="案件标题" min-width="340">
          <template #default="{ row }">
            <div class="case-cell">
              <img :src="row.assets?.[0]?.fileUrl || getFallbackImage(row.id)" alt="" class="case-thumb" />
              <div class="case-copy">
                <strong>{{ row.title }}</strong>
                <p>{{ row.summary || crop(row.content, 80) }}</p>
              </div>
            </div>
          </template>
        </el-table-column>
        <el-table-column label="所属专题" min-width="180">
          <template #default="{ row }">{{ getCategoryName(row.categoryId) }}</template>
        </el-table-column>
        <el-table-column label="附件数" width="96" align="center">
          <template #default="{ row }">{{ row.assets?.length || 0 }}</template>
        </el-table-column>
        <el-table-column label="标签" min-width="220">
          <template #default="{ row }">
            <div class="tag-list">
              <span v-for="tag in buildTags(row)" :key="tag" class="law-tag">{{ tag }}</span>
            </div>
          </template>
        </el-table-column>
        <el-table-column label="更新时间" width="170">
          <template #default="{ row }">{{ formatDateTime(row.updateTime || row.createTime) }}</template>
        </el-table-column>
        <el-table-column label="操作" width="220" fixed="right">
          <template #default="{ row }">
            <div class="row-actions">
              <el-button size="small" @click="openDetail(row)">详情</el-button>
              <el-button size="small" type="primary" link @click="openEditDialog(row)">编辑</el-button>
              <el-button size="small" type="danger" link @click="removeItem(row)">删除</el-button>
            </div>
          </template>
        </el-table-column>
      </el-table>

      <div class="pagination-wrap">
        <el-pagination
          v-model:current-page="pagination.page"
          v-model:page-size="pagination.pageSize"
          :page-sizes="[10, 20, 50]"
          :total="total"
          layout="total, sizes, prev, pager, next"
          @current-change="loadItems"
          @size-change="handleSizeChange"
        />
      </div>
    </section>

    <el-dialog v-model="detailVisible" title="案件详情与附件" width="980px">
      <ItemDetailPanel
        v-if="currentItem"
        :item="currentItem"
        :category-name="getCategoryName(currentItem.categoryId)"
        :tags="buildTags(currentItem)"
        :source-name="extractSourceName(currentItem)"
        editable
        :uploading="assetUploading"
        @upload-asset="handleAssetUpload"
        @remove-asset="handleAssetRemove"
      />
    </el-dialog>

    <ItemEditorDialog
      v-model="editorVisible"
      :mode="editorMode"
      :form="editorForm"
      :categories="categories"
      :submitting="editorSubmitting"
      @submit="submitEditor"
    />

    <el-dialog v-model="importVisible" title="批量导入法律案件" width="920px">
      <div class="import-layout">
        <section class="import-panel">
          <div class="import-header">
            <div>
              <h3>案件语料导入</h3>
              <p>支持 PDF、Word、TXT、Markdown、HTML、CSV、JSON 等文件，系统会提取正文、生成摘要，并自动匹配专题分类。</p>
            </div>
          </div>

          <div class="import-options">
            <el-select v-model="importForm.categoryId" clearable placeholder="自动识别专题分类，或手动指定">
              <el-option v-for="category in categories" :key="category.id" :label="category.name" :value="category.id" />
            </el-select>
            <el-switch
              v-model="importForm.rebuildIndex"
              inline-prompt
              active-text="导入后重建"
              inactive-text="仅入库"
            />
          </div>

          <el-upload
            ref="uploadRef"
            v-model:file-list="uploadFiles"
            class="upload-area"
            drag
            multiple
            :auto-upload="false"
            :limit="20"
            accept=".pdf,.doc,.docx,.txt,.md,.markdown,.html,.htm,.csv,.json"
          >
            <el-icon class="upload-icon"><UploadFilled /></el-icon>
            <div class="el-upload__text">
              把法律案件材料拖到这里，或 <em>点击选择文件</em>
            </div>
          </el-upload>

          <div class="import-submit">
            <el-button @click="clearImportFiles">清空文件</el-button>
            <el-button type="primary" :loading="importing" @click="submitImport">开始导入</el-button>
          </div>

          <section v-if="uploadPreviewRows.length" class="upload-preview panel-card">
            <div class="preview-head">
              <h3>导入前预览</h3>
              <span>已选择 {{ uploadPreviewRows.length }} 个文件</span>
            </div>
            <div class="preview-grid">
              <article v-for="file in uploadPreviewRows" :key="file.uid" class="preview-card">
                <strong>{{ file.name }}</strong>
                <div class="preview-meta">
                  <span>{{ file.extLabel }}</span>
                  <span>{{ file.sizeLabel }}</span>
                </div>
              </article>
            </div>
          </section>
        </section>

        <aside class="import-side panel-card">
          <h3>导入说明</h3>
          <p>“手动导入”适合补充非官方同步来源的案例材料，例如本地整理的判例、办案记录摘要或专题案例包。</p>
          <div class="import-metrics">
            <div>
              <span>文件上限</span>
              <strong>20</strong>
            </div>
            <div>
              <span>索引策略</span>
              <strong>{{ importForm.rebuildIndex ? '自动刷新' : '仅入库' }}</strong>
            </div>
          </div>
        </aside>
      </div>

      <section v-if="importResult" class="import-result panel-card">
        <div class="preview-head">
          <h3>导入回执</h3>
          <span>{{ importResult.indexMessage || (importResult.reindexed ? 'RAG 索引已更新' : 'RAG 索引未更新') }}</span>
        </div>
        <div class="result-summary">
          <span>共 {{ importResult.totalFiles }} 个文件</span>
          <span>成功 {{ importResult.importedCount }}</span>
          <span>跳过 {{ importResult.skippedCount }}</span>
          <span>失败 {{ importResult.failedCount }}</span>
        </div>
        <el-table :data="importResult.results" max-height="280">
          <el-table-column label="文件" prop="fileName" min-width="220" show-overflow-tooltip />
          <el-table-column label="案件标题" min-width="220" show-overflow-tooltip>
            <template #default="{ row }">{{ row.title || row.existingTitle || '--' }}</template>
          </el-table-column>
          <el-table-column label="专题" min-width="150">
            <template #default="{ row }">{{ row.categoryName || getCategoryName(row.categoryId) }}</template>
          </el-table-column>
          <el-table-column label="字数" width="100">
            <template #default="{ row }">{{ row.charCount || 0 }}</template>
          </el-table-column>
          <el-table-column label="状态" width="110">
            <template #default="{ row }">
              <el-tag :type="getImportStatusType(row)" effect="plain">{{ getImportStatusText(row) }}</el-tag>
            </template>
          </el-table-column>
          <el-table-column label="说明" min-width="220" show-overflow-tooltip>
            <template #default="{ row }">{{ row.message || '--' }}</template>
          </el-table-column>
        </el-table>
      </section>
    </el-dialog>
  </AdminShell>
</template>

<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue'
import { useRoute } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import type { UploadInstance, UploadUserFile } from 'element-plus'
import { UploadFilled } from '@element-plus/icons-vue'
import { categoryApi } from '@/api/category'
import { fileApi } from '@/api/file'
import { itemApi } from '@/api/item'
import AdminShell from '@/components/admin/AdminShell.vue'
import ItemDetailPanel from '@/components/admin/ItemDetailPanel.vue'
import ItemEditorDialog from '@/components/admin/ItemEditorDialog.vue'
import type { Category, Item, ItemAsset, ItemImportResponse, ItemImportResult } from '@/types'
import cover1 from '@/assets/images/1.jpg'
import cover2 from '@/assets/images/2.jpg'
import cover3 from '@/assets/images/3.jpg'
import cover4 from '@/assets/images/4.jpg'
import cover5 from '@/assets/images/5.jpg'
import cover6 from '@/assets/images/6.jpg'

const route = useRoute()

const loading = ref(false)
const syncing = ref(false)
const detailVisible = ref(false)
const editorVisible = ref(false)
const importVisible = ref(false)
const editorSubmitting = ref(false)
const assetUploading = ref(false)
const importing = ref(false)
const editorMode = ref<'create' | 'edit'>('create')
const uploadRef = ref<UploadInstance>()

const categories = ref<Category[]>([])
const items = ref<Item[]>([])
const total = ref(0)
const currentItem = ref<Item | null>(null)
const uploadFiles = ref<UploadUserFile[]>([])
const importResult = ref<ItemImportResponse | null>(null)
const cardImages = [cover1, cover2, cover3, cover4, cover5, cover6]

const editorForm = reactive<Item>({
  id: undefined,
  title: '',
  content: '',
  summary: '',
  categoryId: undefined,
  tags: '',
  status: 1
})

const pagination = reactive({
  page: 1,
  pageSize: 10
})

const filters = reactive({
  keyword: '',
  categoryId: undefined as number | undefined,
  tags: ''
})

const importForm = reactive({
  categoryId: undefined as number | undefined,
  rebuildIndex: true
})

const summaryCount = computed(() => items.value.filter((item) => Boolean(item.summary?.trim())).length)
const assetCoverage = computed(() => {
  if (!items.value.length) return '0%'
  const covered = items.value.filter((item) => (item.assets?.length || 0) > 0).length
  return `${Math.round((covered / items.value.length) * 100)}%`
})
const uploadPreviewRows = computed(() =>
  uploadFiles.value.map((file, index) => ({
    uid: String(file.uid || `${file.name}-${index}`),
    name: file.name,
    extLabel: getFileExtensionLabel(file.name),
    sizeLabel: formatFileSize(file.size || file.raw?.size || 0),
    rawFile: file.raw
  }))
)

onMounted(async () => {
  await Promise.all([loadCategories(), loadItems()])
  applyRouteCategoryFilter()
})

async function loadCategories() {
  const res = await categoryApi.getCategoryList()
  categories.value = Array.isArray(res.data) ? res.data : []
}

async function loadItems() {
  loading.value = true
  try {
    const res = await itemApi.getItemPage({
      pageNum: pagination.page,
      pageSize: pagination.pageSize,
      categoryId: filters.categoryId,
      keyword: filters.keyword.trim() || undefined,
      tags: filters.tags.trim() || undefined
    })
    const records = (res.data?.records || []) as Item[]
    items.value = await Promise.all(records.map(enrichItemAssets))
    total.value = Number(res.data?.total || 0)
  } finally {
    loading.value = false
  }
}

async function enrichItemAssets(item: Item) {
  if (!item.id) return item
  const assetRes = await itemApi.getItemAssets(item.id)
  return {
    ...item,
    assets: assetRes.data || []
  }
}

function applyRouteCategoryFilter() {
  const categoryIdQuery = route.query.categoryId
  if (typeof categoryIdQuery === 'string' && categoryIdQuery) {
    const matchedId = Number(categoryIdQuery)
    if (!Number.isNaN(matchedId)) {
      filters.categoryId = matchedId
      void applyFilters()
    }
  }
}

async function applyFilters() {
  pagination.page = 1
  await loadItems()
}

function resetFilters() {
  filters.keyword = ''
  filters.categoryId = undefined
  filters.tags = ''
  pagination.page = 1
  void loadItems()
}

function handleSizeChange(size: number) {
  pagination.pageSize = size
  pagination.page = 1
  void loadItems()
}

async function syncOfficialCases() {
  syncing.value = true
  try {
    const res = await itemApi.syncOfficialCases()
    await loadItems()
    ElMessage.success(res.message || res.data?.message || '官方案例同步完成')
  } finally {
    syncing.value = false
  }
}

function openImportDialog() {
  importVisible.value = true
  importResult.value = null
}

function clearImportFiles() {
  uploadFiles.value = []
  uploadRef.value?.clearFiles()
  importResult.value = null
}

async function submitImport() {
  const files = uploadPreviewRows.value
    .map((file) => file.rawFile)
    .filter((file): file is File => file instanceof File)

  if (!files.length) {
    ElMessage.warning('请先选择要导入的法律案件材料')
    return
  }

  importing.value = true
  importResult.value = null
  try {
    const res = await itemApi.importItems({
      files,
      categoryId: importForm.categoryId,
      rebuildIndex: importForm.rebuildIndex
    })
    importResult.value = res.data || null
    await loadItems()
    ElMessage.success(res.message || res.data?.indexMessage || '法律案件导入完成')
  } finally {
    importing.value = false
  }
}

async function openDetail(item: Item) {
  if (!item.id) return
  const res = await itemApi.getItemDetail(item.id)
  currentItem.value = {
    ...(res.data || item),
    assets: (await itemApi.getItemAssets(item.id)).data || []
  }
  detailVisible.value = true
}

function resetEditorForm() {
  Object.assign(editorForm, {
    id: undefined,
    title: '',
    content: '',
    summary: '',
    categoryId: undefined,
    tags: '',
    status: 1
  })
}

function openCreateDialog() {
  editorMode.value = 'create'
  resetEditorForm()
  editorVisible.value = true
}

async function openEditDialog(item: Item) {
  if (!item.id) return
  const res = await itemApi.getItemDetail(item.id)
  const detail = res.data || item
  editorMode.value = 'edit'
  Object.assign(editorForm, {
    id: detail.id,
    title: detail.title || '',
    content: detail.content || '',
    summary: detail.summary || '',
    categoryId: detail.categoryId,
    tags: detail.tags || '',
    status: detail.status ?? 1
  })
  editorVisible.value = true
}

async function submitEditor() {
  if (!editorForm.title?.trim()) {
    ElMessage.warning('请输入案件标题')
    return
  }
  if (!editorForm.content?.trim()) {
    ElMessage.warning('请输入案件正文')
    return
  }

  editorSubmitting.value = true
  try {
    const payload: Item = {
      ...editorForm,
      title: editorForm.title.trim(),
      content: editorForm.content.trim(),
      summary: editorForm.summary?.trim() || crop(editorForm.content, 220),
      tags: editorForm.tags?.trim() || '',
      status: editorForm.status ?? 1
    }

    if (editorMode.value === 'create') {
      await itemApi.createItem(payload)
      ElMessage.success('法律案件已新增')
    } else {
      await itemApi.updateItem(payload)
      ElMessage.success('法律案件已更新')
    }

    editorVisible.value = false
    await loadItems()
  } finally {
    editorSubmitting.value = false
  }
}

async function handleAssetUpload(file: File) {
  if (!currentItem.value?.id) {
    ElMessage.warning('请先保存案例，再上传附件')
    return
  }

  assetUploading.value = true
  try {
    const uploadRes = await fileApi.uploadFile(file, 'item-assets')
    const uploaded = uploadRes.data
    const assetType = resolveAssetType(file.name)
    const payload: ItemAsset = {
      fileName: file.name,
      fileUrl: uploaded.url,
      bucket: uploaded.bucket,
      objectKey: uploaded.objectKey,
      fileExt: file.name.includes('.') ? file.name.split('.').pop() : '',
      fileSize: file.size,
      assetType,
      summary: `${assetType}材料`
    }
    const assetRes = await itemApi.addItemAsset(currentItem.value.id, payload)
    currentItem.value.assets = [...(currentItem.value.assets || []), assetRes.data]
    items.value = items.value.map((item) => item.id === currentItem.value?.id ? { ...item, assets: currentItem.value?.assets || [] } : item)
    ElMessage.success('案例附件已绑定')
  } finally {
    assetUploading.value = false
  }
}

async function handleAssetRemove(asset: ItemAsset) {
  if (!asset.id) return
  await itemApi.deleteItemAsset(asset.id)
  if (currentItem.value) {
    currentItem.value.assets = (currentItem.value.assets || []).filter((item) => item.id !== asset.id)
    items.value = items.value.map((item) => item.id === currentItem.value?.id ? { ...item, assets: currentItem.value?.assets || [] } : item)
  }
  ElMessage.success('案例附件已移除')
}

async function removeItem(item: Item) {
  if (!item.id) return
  await ElMessageBox.confirm(`确认删除案件“${item.title}”吗？`, '删除案件', { type: 'warning' })
  await itemApi.deleteItem(item.id)
  ElMessage.success('法律案件已删除')
  await loadItems()
}

function getCategoryName(categoryId?: number) {
  return categories.value.find((category) => category.id === categoryId)?.name || '未分类'
}

function buildTags(item: Partial<Item>) {
  const source = item.tags || item.summary || ''
  const tags = source
    .split(/[，、\s]+/)
    .map((entry) => entry.trim())
    .filter(Boolean)
  if (tags.length) return tags.slice(0, 4)
  const fallback = getCategoryName(item.categoryId)
  return fallback && fallback !== '未分类' ? [fallback] : []
}

function extractSourceName(item: Item) {
  if (item.assets?.length) {
    return `已绑定 ${item.assets.length} 份材料`
  }
  return '暂无附件'
}

function crop(text?: string, length = 88) {
  if (!text) return '暂无摘要'
  return text.length > length ? `${text.slice(0, length)}...` : text
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

function getFallbackImage(id?: number) {
  return cardImages[Number(id || 0) % cardImages.length]
}

function getFileExtensionLabel(filename: string) {
  const parts = filename.split('.')
  return parts.length > 1 ? parts.pop()!.toUpperCase() : 'FILE'
}

function formatFileSize(size: number) {
  if (size < 1024) return `${size} B`
  if (size < 1024 * 1024) return `${(size / 1024).toFixed(1)} KB`
  return `${(size / (1024 * 1024)).toFixed(1)} MB`
}

function getImportStatusType(row: ItemImportResult) {
  if (row.success) return 'success'
  if (row.skipped || row.duplicate) return 'warning'
  return 'danger'
}

function getImportStatusText(row: ItemImportResult) {
  if (row.success) return '成功'
  if (row.duplicate) return '重复'
  if (row.skipped) return '跳过'
  return '失败'
}

function resolveAssetType(name: string) {
  const lower = name.toLowerCase()
  if (/\.(png|jpe?g|gif|webp|bmp)$/.test(lower)) return 'image'
  if (/\.pdf$/.test(lower)) return 'pdf'
  if (/\.(doc|docx)$/.test(lower)) return 'document'
  return 'attachment'
}
</script>

<style scoped>
.hero,
.toolbar,
.table-panel,
.stat-card,
.import-panel,
.import-side,
.upload-preview,
.import-result {
  border-radius: var(--radius-xl);
}

.hero {
  min-height: 214px;
  padding: 32px 36px;
  display: grid;
  grid-template-columns: minmax(0, 1fr) auto;
  align-items: center;
  gap: 24px;
}

.eyebrow {
  margin: 0 0 14px;
  color: var(--brand);
  text-transform: uppercase;
  letter-spacing: 0.18em;
  font-size: 13px;
  font-weight: 600;
  line-height: 1;
}

.hero h1 {
  margin: 0;
  color: var(--text-main);
  font-size: 36px;
  font-weight: 700;
  line-height: 1.2;
}

.hero-text {
  margin: 18px 0 0;
  max-width: 1080px;
  color: var(--text-muted);
  font-size: 17px;
  line-height: 1.8;
}

.hero-actions,
.row-actions {
  display: flex;
  gap: 12px;
  align-items: center;
}

.hero-actions {
  justify-content: flex-end;
  flex-wrap: wrap;
}

.hero-actions :deep(.el-button) {
  height: 42px;
  margin-left: 0;
  padding: 0 22px;
  font-size: 16px;
}

.stats-grid {
  display: grid;
  grid-template-columns: repeat(3, minmax(0, 1fr));
  gap: 16px;
}

.stat-card {
  min-height: 142px;
  padding: 26px 28px;
  display: grid;
  align-content: center;
  gap: 12px;
}

.stat-card span,
.stat-card small {
  display: block;
  color: var(--text-muted);
  font-size: 16px;
  line-height: 1.5;
}

.stat-card strong {
  display: block;
  margin: 0;
  color: var(--text-main);
  font-size: 40px;
  font-weight: 700;
  line-height: 1;
}

.toolbar {
  padding: 18px 20px;
}

.filter-grid {
  display: grid;
  grid-template-columns: 1.4fr 1fr 1.1fr auto auto;
  gap: 12px;
}

.table-panel {
  padding: 6px 20px 20px;
}

.import-layout {
  display: grid;
  grid-template-columns: minmax(0, 1fr) 260px;
  gap: 16px;
}

.import-panel,
.import-side,
.upload-preview,
.import-result {
  padding: 18px;
}

.import-panel,
.import-side,
.upload-preview,
.import-result {
  background: #fff;
  border: 1px solid #e4ebf5;
}

.import-header,
.import-options,
.import-submit,
.preview-head,
.preview-meta,
.result-summary {
  display: flex;
  gap: 12px;
  flex-wrap: wrap;
  align-items: center;
}

.import-header {
  justify-content: space-between;
}

.import-header h3,
.import-side h3,
.preview-head h3 {
  margin: 0;
}

.import-header p,
.import-side p,
.preview-head span,
.preview-meta {
  margin: 8px 0 0;
  color: var(--text-muted);
  line-height: 1.7;
}

.import-options {
  margin-top: 16px;
}

.import-options :deep(.el-select) {
  width: min(360px, 100%);
}

.upload-area {
  margin-top: 16px;
}

.upload-icon {
  font-size: 38px;
  color: var(--brand);
}

.import-submit {
  margin-top: 16px;
  justify-content: flex-end;
}

.upload-preview,
.import-result {
  margin-top: 16px;
}

.preview-head {
  justify-content: space-between;
}

.preview-grid {
  margin-top: 12px;
  display: grid;
  grid-template-columns: repeat(auto-fit, minmax(180px, 1fr));
  gap: 12px;
}

.preview-card {
  padding: 14px;
  border-radius: 16px;
  background: #f8fbff;
  border: 1px solid #e6eef8;
}

.preview-card strong {
  display: block;
  line-height: 1.6;
  word-break: break-all;
}

.import-side {
  align-self: start;
}

.import-metrics {
  margin-top: 18px;
  display: grid;
  gap: 12px;
}

.import-metrics div {
  display: flex;
  justify-content: space-between;
  gap: 12px;
  padding-top: 12px;
  border-top: 1px solid #edf2f7;
}

.result-summary {
  margin: 14px 0;
  color: var(--text-muted);
}

.case-cell {
  display: flex;
  gap: 14px;
  align-items: flex-start;
}

.case-thumb {
  width: 88px;
  height: 88px;
  border-radius: 18px;
  object-fit: cover;
  flex-shrink: 0;
}

.case-copy strong {
  display: block;
  line-height: 1.6;
}

.case-copy p {
  margin: 8px 0 0;
  color: var(--text-soft);
  line-height: 1.7;
}

.tag-list {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
}

.law-tag {
  padding: 4px 10px;
  border-radius: 999px;
  font-size: 12px;
  color: #15803d;
  background: #eefbf1;
  border: 1px solid #ccefd5;
}

.pagination-wrap {
  margin-top: 18px;
  display: flex;
  justify-content: flex-end;
}

@media (max-width: 1200px) {
  .stats-grid,
  .filter-grid,
  .import-layout {
    grid-template-columns: 1fr;
  }

  .hero {
    min-height: auto;
    grid-template-columns: 1fr;
    align-items: flex-start;
  }

  .hero-actions {
    width: 100%;
    justify-content: flex-start;
  }

  .import-submit {
    justify-content: flex-start;
  }
}
</style>
