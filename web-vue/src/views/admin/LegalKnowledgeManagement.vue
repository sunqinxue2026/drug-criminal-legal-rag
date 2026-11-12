<template>
  <AdminShell active-path="/admin/legal-knowledge" breadcrumb="首页 / 法律知识管理">
    <section class="hero panel-card">
      <div class="hero-copy">
        <p class="eyebrow">Normative Corpus</p>
        <h1>法律知识管理</h1>
        <p class="hero-text">
          这里管理涉毒专题下的结构化法律知识与规则依据，重点面向法条、司法解释及从案例、文书中提炼出的知识条目。它不是原始案件语料或原始文书语料列表，导入后的记录会自动生成 `law_basis` 检索块并进入 RAG 索引。
        </p>
      </div>
      <div class="hero-actions">
        <el-button round @click="loadKnowledge">刷新列表</el-button>
        <el-button type="primary" round @click="openCreateDialog">新增法律知识</el-button>
        <el-button type="warning" round :loading="syncingOfficial" @click="syncOfficialKnowledge">同步官方知识</el-button>
        <el-button type="success" round @click="openImportDialog">导入法律知识 JSON</el-button>
      </div>
    </section>

    <section class="stats-grid">
      <article class="stat-card panel-card">
        <span>知识总数</span>
        <strong>{{ total }}</strong>
        <small>按当前筛选条件返回</small>
      </article>
      <article class="stat-card panel-card">
        <span>当前页数量</span>
        <strong>{{ records.length }}</strong>
        <small>本页实际加载记录</small>
      </article>
      <article class="stat-card panel-card">
        <span>专题覆盖数</span>
        <strong>{{ categories.length }}</strong>
        <small>当前可归类专题</small>
      </article>
    </section>

    <section class="toolbar panel-card">
      <div class="filter-grid">
        <el-input v-model="filters.keyword" placeholder="输入禁毒法条、案例标题或专题关键词" clearable @keyup.enter="applyFilters" />
        <el-select v-model="filters.categoryId" placeholder="选择专题分类" clearable>
          <el-option v-for="category in categories" :key="category.id" :label="category.name" :value="category.id" />
        </el-select>
        <el-select v-model="filters.knowledgeType" placeholder="知识类型" clearable>
          <el-option label="法律法规" value="法律法规" />
          <el-option label="司法解释" value="司法解释" />
          <el-option label="指导案例" value="指导案例" />
          <el-option label="规范知识" value="规范知识" />
        </el-select>
        <el-input v-model="filters.businessDomain" placeholder="专题领域，如涉毒犯罪认定、家属应对、麻精药品" clearable @keyup.enter="applyFilters" />
        <el-button type="primary" @click="applyFilters">搜索</el-button>
        <el-button @click="resetFilters">重置</el-button>
      </div>
    </section>

    <section class="panel-card table-panel">
      <el-table :data="records" v-loading="loading">
        <el-table-column label="知识名称 / 条号" min-width="320">
          <template #default="{ row }">
            <div class="doc-title-cell">
              <div class="doc-dot"></div>
              <div class="doc-copy">
                <strong>{{ row.title || row.documentName || '未命名知识' }}</strong>
                <span>{{ row.summary || row.articleTitle || row.topic || '--' }}</span>
              </div>
            </div>
          </template>
        </el-table-column>
        <el-table-column label="类型" width="140">
          <template #default="{ row }">
            <el-tag effect="plain">{{ row.knowledgeType || '--' }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="专题领域" min-width="150">
          <template #default="{ row }">{{ row.businessDomain || row.topic || '--' }}</template>
        </el-table-column>
        <el-table-column label="发布机关" min-width="200">
          <template #default="{ row }">{{ row.issuer || row.sourceName || '--' }}</template>
        </el-table-column>
        <el-table-column label="生效日期" width="140">
          <template #default="{ row }">{{ row.effectiveDate || '--' }}</template>
        </el-table-column>
        <el-table-column label="操作" width="240" fixed="right">
          <template #default="{ row }">
            <el-button size="small" @click="openDetail(row)">详情</el-button>
            <el-button size="small" type="primary" link @click="openEditDialog(row)">编辑</el-button>
            <el-button size="small" type="danger" link @click="removeKnowledge(row)">删除</el-button>
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
          @current-change="loadKnowledge"
          @size-change="handleSizeChange"
        />
      </div>
    </section>

    <el-dialog v-model="detailVisible" title="法律知识详情" width="1100px">
      <LegalKnowledgeDetailPanel v-if="currentDetail" :detail="currentDetail" />
    </el-dialog>

    <LegalKnowledgeEditorDialog
      v-model="editorVisible"
      :mode="editorMode"
      :form="editorForm"
      :categories="categories"
      :submitting="editorSubmitting"
      @submit="submitEditor"
    />

    <el-dialog v-model="importVisible" title="导入法律知识 JSON" width="980px">
      <div class="import-layout">
        <section class="import-panel">
          <div class="import-header">
            <div>
              <h3>JSON 导入</h3>
              <p>支持扁平数组或带 `articles / entries / items` 的嵌套结构。适合导入法条、司法解释和从案例、文书中提炼出的结构化法律知识 JSON。</p>
            </div>
            <el-tag type="success" effect="dark">可选自动重建 RAG 索引</el-tag>
          </div>

          <div class="import-options">
            <el-select v-model="importForm.categoryId" clearable placeholder="可选：手动指定专题分类">
              <el-option v-for="category in categories" :key="category.id" :label="category.name" :value="category.id" />
            </el-select>
            <el-switch v-model="importForm.rebuildIndex" inline-prompt active-text="导入后重建" inactive-text="仅入库" />
          </div>

          <el-upload
            v-model:file-list="uploadFiles"
            class="upload-area"
            drag
            :auto-upload="false"
            :limit="1"
            accept=".json"
            :on-change="handleUploadChange"
          >
            <el-icon class="upload-icon"><UploadFilled /></el-icon>
            <div class="el-upload__text">把法律知识 JSON 拖到这里，或 <em>点击选择文件</em></div>
          </el-upload>

          <el-alert v-if="previewParseError" :title="previewParseError" type="warning" :closable="false" show-icon />

          <div class="import-submit">
            <el-button @click="clearImport">清空文件</el-button>
            <el-button type="primary" :loading="importing || parsingPreview" @click="submitImport">开始导入</el-button>
          </div>

          <section v-if="importing || importStage !== 'idle'" class="import-progress panel-card">
            <div class="preview-head">
              <h3>导入进度</h3>
              <span>{{ importStageLabel }}</span>
            </div>
            <el-progress :percentage="importProgress" :status="importStage === 'done' ? 'success' : undefined" />
            <div class="progress-meta">
              <span>已处理 {{ importRuntime.completedRecords }} / {{ importRuntime.totalRecords || knowledgePreviewRows.length }} 条记录</span>
              <span>进度按真实完成记录数计算</span>
            </div>
            <div class="stage-chip-row">
              <span class="stage-chip" :class="{ 'stage-chip--active': importStage === 'processing', 'stage-chip--done': importRuntime.completedRecords > 0 }">
                知识入库
              </span>
              <span
                class="stage-chip"
                :class="{ 'stage-chip--active': importStage === 'reindexing', 'stage-chip--done': importStage === 'done' && importResult?.reindexed }"
              >
                索引刷新
              </span>
            </div>
            <div v-if="importStage === 'reindexing'" class="reindexing-tip">
              <el-icon class="is-loading"><Loading /></el-icon>
              <span>索引刷新没有真实百分比，完成后会直接返回结果。</span>
            </div>
          </section>

          <section v-if="parsingPreview" class="upload-preview panel-card">
            <div class="preview-head">
              <h3>导入前预览</h3>
              <span>正在解析 JSON 记录...</span>
            </div>
            <el-skeleton :rows="6" animated />
          </section>

          <section v-if="knowledgePreviewRows.length" class="upload-preview panel-card">
            <div class="preview-head">
              <h3>导入前预览</h3>
              <span>已解析 {{ knowledgePreviewRows.length }} 条记录</span>
            </div>
            <div class="preview-grid">
              <article
                v-for="row in knowledgePreviewRows"
                :key="row.uid"
                class="preview-card preview-card--interactive"
                @click="openUploadPreview(row)"
              >
                <strong>{{ row.title }}</strong>
                <div class="preview-meta">
                  <span>{{ row.articleNumber }}</span>
                  <span>{{ row.knowledgeType }}</span>
                  <el-tag size="small" effect="plain" :type="row.statusType">{{ row.statusLabel }}</el-tag>
                </div>
                <small class="preview-hint">点击查看法律知识详情预览</small>
                <p class="preview-status-note">{{ row.message || row.summary }}</p>
              </article>
            </div>
          </section>
        </section>

        <aside class="import-side">
          <div class="side-card">
            <h3>导入说明</h3>
            <ul class="import-list">
              <li>系统会先把 JSON 拍平成法律知识记录，再逐条结构化入库。</li>
              <li>重复知识记录会自动跳过，新的法律知识会生成 `law_basis` 检索块。</li>
              <li>导入前可先点开预览，确认字段和详情页结构一致。</li>
              <li>开启重建索引后，会在所有记录处理完后单独刷新向量索引。</li>
            </ul>
          </div>

          <div class="side-card" v-if="importResult">
            <h3>导入结果</h3>
            <div class="import-metrics">
              <div><span>记录总数</span><strong>{{ importResult.totalRecords }}</strong></div>
              <div><span>成功导入</span><strong>{{ importResult.importedCount }}</strong></div>
              <div><span>重复跳过</span><strong>{{ importResult.skippedCount }}</strong></div>
              <div><span>失败数量</span><strong>{{ importResult.failedCount }}</strong></div>
            </div>
            <p class="index-message">{{ importStatusMessage }}</p>
          </div>
        </aside>
      </div>

      <section v-if="importResult" class="import-result panel-card">
        <div class="preview-head">
          <h3>批量导入回执</h3>
          <span>{{ importResult.reindexed ? 'RAG 索引已更新' : 'RAG 索引未更新' }}</span>
        </div>
        <el-table :data="importResult.results" max-height="320">
          <el-table-column label="来源文件" min-width="160" prop="sourceName" />
          <el-table-column label="状态" width="100">
            <template #default="{ row }">
              <el-tag :type="row.success ? 'success' : row.skipped ? 'warning' : 'danger'">
                {{ row.success ? '成功' : row.skipped ? '跳过' : '失败' }}
              </el-tag>
            </template>
          </el-table-column>
          <el-table-column label="规范标题" min-width="220" prop="title" />
          <el-table-column label="条号" min-width="120" prop="articleNumber" />
          <el-table-column label="专题分类" min-width="120" prop="categoryName" />
          <el-table-column label="分块数" width="100" prop="chunkCount" />
          <el-table-column label="说明" min-width="260">
            <template #default="{ row }">
              <span>{{ row.message }}</span>
            </template>
          </el-table-column>
          <el-table-column label="操作" width="120" fixed="right">
            <template #default="{ row }">
              <el-button
                v-if="row.success && row.knowledgeId"
                type="primary"
                link
                @click="viewImportedKnowledge(row.knowledgeId)"
              >
                查看详情
              </el-button>
              <span v-else class="action-placeholder">--</span>
            </template>
          </el-table-column>
        </el-table>
      </section>
    </el-dialog>

    <el-dialog
      v-model="previewDialogVisible"
      title="法律知识详情预览"
      width="1100px"
      @closed="resetPreviewState"
    >
      <div class="document-preview">
        <div class="document-preview__meta">
          <div class="meta-pill">
            <span>来源文件</span>
            <strong>{{ previewInfo.sourceFileName || '--' }}</strong>
          </div>
          <div class="meta-pill">
            <span>标题</span>
            <strong>{{ previewInfo.title || '--' }}</strong>
          </div>
          <div class="meta-pill">
            <span>条号</span>
            <strong>{{ previewInfo.articleNumber || '--' }}</strong>
          </div>
          <div class="meta-pill">
            <span>摘要</span>
            <strong>{{ previewInfo.summary || '--' }}</strong>
          </div>
        </div>
        <div class="preview-note">
          这里展示的是导入前的法律知识结构化详情预览，导入成功后详情页会展示同一套字段结果。
        </div>

        <el-skeleton :rows="10" animated v-if="previewLoading" />
        <el-empty v-else-if="previewError" :description="previewError" />
        <LegalKnowledgeDetailPanel
          v-else-if="previewDetail"
          :detail="previewDetail"
          :title-fallback="previewInfo.title"
          description="这里展示的是导入前的法律知识结构化详情预览，导入成功后详情页会展示同一套字段结果。"
        />
      </div>
    </el-dialog>
  </AdminShell>
</template>

<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue'
import type { UploadFile, UploadFiles } from 'element-plus'
import { ElMessage, ElMessageBox } from 'element-plus'
import { Loading, UploadFilled } from '@element-plus/icons-vue'
import AdminShell from '@/components/admin/AdminShell.vue'
import LegalKnowledgeDetailPanel from '@/components/admin/LegalKnowledgeDetailPanel.vue'
import LegalKnowledgeEditorDialog from '@/components/admin/LegalKnowledgeEditorDialog.vue'
import { categoryApi } from '@/api/category'
import { legalKnowledgeApi } from '@/api/legalKnowledge'
import type { Category, LegalKnowledge, LegalKnowledgeDetail, LegalKnowledgeImportResponse, LegalKnowledgeImportResult } from '@/types'
import {
  flattenLegalKnowledgePayload,
  pickKnowledgePreviewArticle,
  pickKnowledgePreviewSummary,
  pickKnowledgePreviewTitle
} from '@/utils/legalKnowledge'

type ImportRecordStatus = 'waiting' | 'processing' | 'success' | 'skipped' | 'failed'
type ImportStage = 'idle' | 'processing' | 'reindexing' | 'done'
type ImportRecordState = {
  status: ImportRecordStatus
  message: string
  knowledgeId?: number
}
type PreviewKnowledgeRow = {
  uid: string
  record: Record<string, any>
  title: string
  articleNumber: string
  summary: string
  knowledgeType: string
  status: ImportRecordStatus
  statusLabel: string
  statusType: 'info' | 'warning' | 'success' | 'danger'
  message: string
  knowledgeId?: number
}

const loading = ref(false)
const importing = ref(false)
const parsingPreview = ref(false)
const syncingOfficial = ref(false)
const editorVisible = ref(false)
const editorSubmitting = ref(false)
const editorMode = ref<'create' | 'edit'>('create')
const total = ref(0)
const records = ref<LegalKnowledge[]>([])
const categories = ref<Category[]>([])
const currentDetail = ref<LegalKnowledgeDetail | null>(null)
const detailVisible = ref(false)
const importVisible = ref(false)
const previewDialogVisible = ref(false)
const importResult = ref<LegalKnowledgeImportResponse | null>(null)
const uploadFiles = ref<UploadFile[]>([])
const previewDetail = ref<LegalKnowledgeDetail | null>(null)
const previewLoading = ref(false)
const previewError = ref('')
const previewParseError = ref('')
const importStage = ref<ImportStage>('idle')
const rawPreviewRecords = ref<Record<string, any>[]>([])
const editorForm = reactive<LegalKnowledge>({
  id: undefined,
  sourceType: 'manual_entry',
  sourceName: '手工录入',
  sourceUrl: '',
  title: '',
  knowledgeType: '规范知识',
  knowledgeSubtype: '',
  documentName: '',
  documentLevel: '',
  issuer: '',
  categoryId: undefined,
  businessDomain: '',
  topic: '',
  chapterTitle: '',
  sectionTitle: '',
  articleNumber: '',
  articleTitle: '',
  keywords: '',
  aliases: '',
  summary: '',
  displayText: '',
  effectiveDate: '',
  expiryDate: '',
  currentStatus: '现行有效',
  status: 1
})
const previewInfo = reactive({
  sourceFileName: '',
  title: '',
  articleNumber: '',
  summary: ''
})

const filters = reactive({
  keyword: '',
  categoryId: undefined as number | undefined,
  knowledgeType: '',
  businessDomain: ''
})

const pagination = reactive({
  page: 1,
  pageSize: 10
})

const importForm = reactive({
  categoryId: undefined as number | undefined,
  rebuildIndex: true
})

const importRuntime = reactive({
  totalRecords: 0,
  completedRecords: 0,
  currentTitle: ''
})
const importRecordStates = reactive<Record<string, ImportRecordState>>({})
const selectedFileName = computed(() => uploadFiles.value[0]?.name || '法律知识导入.json')

const knowledgePreviewRows = computed<PreviewKnowledgeRow[]>(() =>
  rawPreviewRecords.value.map((record, index) => {
    const uid = `${selectedFileName.value}-${index}`
    return {
      ...resolveImportRecordState(uid),
      uid,
      record,
      title: pickKnowledgePreviewTitle(record),
      articleNumber: pickKnowledgePreviewArticle(record),
      summary: cropText(pickKnowledgePreviewSummary(record), 70),
      knowledgeType: firstText(record.knowledgeType, record.type, '规范知识'),
      knowledgeId: importRecordStates[uid]?.knowledgeId
    }
  })
)

const importProgress = computed(() =>
  importRuntime.totalRecords ? Math.round((importRuntime.completedRecords / importRuntime.totalRecords) * 100) : 0
)

const importStageLabel = computed(() => {
  switch (importStage.value) {
    case 'processing':
      return importRuntime.currentTitle
        ? `已完成 ${importRuntime.completedRecords}/${importRuntime.totalRecords}，正在处理《${importRuntime.currentTitle}》`
        : `已完成 ${importRuntime.completedRecords}/${importRuntime.totalRecords} 条记录`
    case 'reindexing':
      return '记录处理完成，正在刷新专题 RAG 索引'
    case 'done':
      return `导入完成，共处理 ${importRuntime.completedRecords}/${importRuntime.totalRecords} 条记录`
    default:
      return '等待开始'
  }
})

const importStatusMessage = computed(() => {
  if (importStage.value === 'processing') {
    return importRuntime.currentTitle
      ? `正在处理《${importRuntime.currentTitle}》，结果会逐条写入下方回执。`
      : '正在处理知识记录，结果会逐条写入下方回执。'
  }
  if (importStage.value === 'reindexing') {
    return '所有记录已处理完成，正在刷新专题 RAG 索引。索引刷新不显示伪百分比，完成后会直接返回结果。'
  }
  return importResult.value?.indexMessage || ''
})

onMounted(async () => {
  await loadCategories()
  await loadKnowledge()
})

async function loadCategories() {
  const res = await categoryApi.getCategoryList()
  categories.value = res.data || []
}

async function loadKnowledge() {
  loading.value = true
  try {
    const res = await legalKnowledgeApi.getKnowledgePage({
      pageNum: pagination.page,
      pageSize: pagination.pageSize,
      categoryId: filters.categoryId,
      keyword: filters.keyword || undefined,
      knowledgeType: filters.knowledgeType || undefined,
      businessDomain: filters.businessDomain || undefined
    })
    records.value = res.data.records || []
    total.value = res.data.total || 0
  } finally {
    loading.value = false
  }
}

async function openDetail(row: LegalKnowledge) {
  if (!row.id) return
  const res = await legalKnowledgeApi.getKnowledgeDetail(row.id)
  currentDetail.value = res.data
  detailVisible.value = true
}

function resetEditorForm() {
  Object.assign(editorForm, {
    id: undefined,
    sourceType: 'manual_entry',
    sourceName: '手工录入',
    sourceUrl: '',
    title: '',
    knowledgeType: '规范知识',
    knowledgeSubtype: '',
    documentName: '',
    documentLevel: '',
    issuer: '',
    categoryId: undefined,
    businessDomain: '',
    topic: '',
    chapterTitle: '',
    sectionTitle: '',
    articleNumber: '',
    articleTitle: '',
    keywords: '',
    aliases: '',
    summary: '',
    displayText: '',
    effectiveDate: '',
    expiryDate: '',
    currentStatus: '现行有效',
    status: 1
  })
}

function openCreateDialog() {
  editorMode.value = 'create'
  resetEditorForm()
  editorVisible.value = true
}

async function openEditDialog(row: LegalKnowledge) {
  if (!row.id) {
    ElMessage.warning('未找到可编辑的知识记录')
    return
  }
  const res = await legalKnowledgeApi.getKnowledgeDetail(row.id)
  const knowledge = res.data?.knowledge
  if (!knowledge) {
    ElMessage.warning('未找到法律知识详情')
    return
  }

  editorMode.value = 'edit'
  Object.assign(editorForm, {
    id: knowledge.id,
    sourceType: knowledge.sourceType || 'manual_entry',
    sourceName: knowledge.sourceName || '手工录入',
    sourceUrl: knowledge.sourceUrl || '',
    title: knowledge.title || '',
    knowledgeType: knowledge.knowledgeType || '规范知识',
    knowledgeSubtype: knowledge.knowledgeSubtype || '',
    documentName: knowledge.documentName || '',
    documentLevel: knowledge.documentLevel || '',
    issuer: knowledge.issuer || '',
    categoryId: knowledge.categoryId,
    businessDomain: knowledge.businessDomain || '',
    topic: knowledge.topic || '',
    chapterTitle: knowledge.chapterTitle || '',
    sectionTitle: knowledge.sectionTitle || '',
    articleNumber: knowledge.articleNumber || '',
    articleTitle: knowledge.articleTitle || '',
    keywords: knowledge.keywords || '',
    aliases: knowledge.aliases || '',
    summary: knowledge.summary || '',
    displayText: knowledge.displayText || '',
    effectiveDate: knowledge.effectiveDate || '',
    expiryDate: knowledge.expiryDate || '',
    currentStatus: knowledge.currentStatus || '现行有效',
    status: knowledge.status ?? 1
  })
  editorVisible.value = true
}

async function submitEditor() {
  if (!editorForm.displayText?.trim() && !editorForm.summary?.trim()) {
    ElMessage.warning('请至少填写正文内容或摘要')
    return
  }

  editorSubmitting.value = true
  try {
    const payload: LegalKnowledge = {
      ...editorForm,
      sourceType: editorForm.sourceType?.trim() || 'manual_entry',
      sourceName: editorForm.sourceName?.trim() || '手工录入',
      sourceUrl: editorForm.sourceUrl?.trim() || '',
      title: editorForm.title?.trim() || '',
      knowledgeType: editorForm.knowledgeType?.trim() || '规范知识',
      knowledgeSubtype: editorForm.knowledgeSubtype?.trim() || '',
      documentName: editorForm.documentName?.trim() || '',
      documentLevel: editorForm.documentLevel?.trim() || '',
      issuer: editorForm.issuer?.trim() || '',
      businessDomain: editorForm.businessDomain?.trim() || '',
      topic: editorForm.topic?.trim() || '',
      chapterTitle: editorForm.chapterTitle?.trim() || '',
      sectionTitle: editorForm.sectionTitle?.trim() || '',
      articleNumber: editorForm.articleNumber?.trim() || '',
      articleTitle: editorForm.articleTitle?.trim() || '',
      keywords: editorForm.keywords?.trim() || '',
      aliases: editorForm.aliases?.trim() || '',
      summary: editorForm.summary?.trim() || '',
      displayText: editorForm.displayText?.trim() || '',
      effectiveDate: editorForm.effectiveDate || undefined,
      expiryDate: editorForm.expiryDate || undefined,
      currentStatus: editorForm.currentStatus?.trim() || '现行有效',
      status: editorForm.status ?? 1
    }

    if (editorMode.value === 'create') {
      await legalKnowledgeApi.createKnowledge(payload)
      ElMessage.success('法律知识已新增')
    } else {
      await legalKnowledgeApi.updateKnowledge(payload)
      ElMessage.success('法律知识已更新')
    }

    editorVisible.value = false
    await loadKnowledge()
  } finally {
    editorSubmitting.value = false
  }
}

async function removeKnowledge(row: LegalKnowledge) {
  if (!row.id) {
    ElMessage.warning('未找到可删除的知识记录')
    return
  }

  try {
    await ElMessageBox.confirm(
      '删除后会同时移除该条法律知识及其检索分块，并刷新专题索引。确定继续吗？',
      '删除法律知识',
      {
        type: 'warning',
        confirmButtonText: '删除',
        cancelButtonText: '取消'
      }
    )
  } catch {
    return
  }

  loading.value = true
  try {
    const res = await legalKnowledgeApi.deleteKnowledge(row.id, true)
    if (currentDetail.value?.knowledge?.id === row.id) {
      detailVisible.value = false
      currentDetail.value = null
    }
    if (records.value.length <= 1 && pagination.page > 1) {
      pagination.page -= 1
    }
    await loadKnowledge()
    ElMessage.success(res.message || '法律知识已删除')
  } finally {
    loading.value = false
  }
}

function openImportDialog() {
  importVisible.value = true
  importResult.value = null
  resetImportRuntime(rawPreviewRecords.value.length)
  clearImportRecordStates()
  importStage.value = 'idle'
}

async function syncOfficialKnowledge() {
  syncingOfficial.value = true
  try {
    const res = await legalKnowledgeApi.importOfficialKnowledge(importForm.categoryId, false)
    importResult.value = res.data || null
    importStage.value = 'done'
    await loadKnowledge()
    ElMessage.success(res.message || res.data?.indexMessage || '官方知识同步完成')
  } finally {
    syncingOfficial.value = false
  }
}

async function handleUploadChange(_file: UploadFile, files: UploadFiles) {
  uploadFiles.value = files.slice(-1)
  importResult.value = null
  importStage.value = 'idle'
  clearImportRecordStates()
  await parseSelectedFile(uploadFiles.value[0])
}

async function parseSelectedFile(file?: UploadFile) {
  rawPreviewRecords.value = []
  previewParseError.value = ''
  resetImportRuntime()

  if (!(file?.raw instanceof File)) {
    return
  }

  parsingPreview.value = true
  try {
    const text = await file.raw.text()
    const parsed = flattenLegalKnowledgePayload(JSON.parse(text))
    if (!parsed.length) {
      previewParseError.value = 'JSON 中未解析出可导入的知识记录'
      return
    }
    rawPreviewRecords.value = parsed
    resetImportRuntime(parsed.length)
  } catch (error: any) {
    previewParseError.value = error?.message ? `JSON 解析失败：${error.message}` : 'JSON 解析失败，请检查文件格式'
  } finally {
    parsingPreview.value = false
  }
}

function clearImport() {
  uploadFiles.value = []
  importResult.value = null
  rawPreviewRecords.value = []
  previewParseError.value = ''
  resetImportRuntime()
  clearImportRecordStates()
  importStage.value = 'idle'
}

function resetPreviewState() {
  previewLoading.value = false
  previewError.value = ''
  previewDetail.value = null
  previewInfo.sourceFileName = ''
  previewInfo.title = ''
  previewInfo.articleNumber = ''
  previewInfo.summary = ''
}

async function openUploadPreview(row: PreviewKnowledgeRow) {
  resetPreviewState()
  previewDialogVisible.value = true
  previewInfo.sourceFileName = selectedFileName.value
  previewInfo.title = row.title
  previewInfo.articleNumber = row.articleNumber
  previewInfo.summary = row.summary

  previewLoading.value = true
  try {
    const res = await legalKnowledgeApi.previewKnowledgeRecord({
      record: row.record,
      sourceFileName: selectedFileName.value,
      categoryId: importForm.categoryId
    })
    previewDetail.value = res.data
  } catch (error: any) {
    previewError.value = error?.response?.data?.message || error?.message || '法律知识预览失败'
  } finally {
    previewLoading.value = false
  }
}

async function submitImport() {
  if (!knowledgePreviewRows.value.length) {
    ElMessage.warning('请先选择并解析一个 JSON 文件')
    return
  }

  if (previewParseError.value) {
    ElMessage.warning('当前 JSON 解析失败，请修正文件后再导入')
    return
  }

  importing.value = true
  importStage.value = 'processing'
  clearImportRecordStates()
  resetImportRuntime(knowledgePreviewRows.value.length)
  importResult.value = createEmptyImportResult(knowledgePreviewRows.value.length)

  try {
    for (const row of knowledgePreviewRows.value) {
      importRuntime.currentTitle = row.title
      setImportRecordState(row.uid, {
        status: 'processing',
        message: '正在结构化入库'
      })
      try {
        const res = await legalKnowledgeApi.importKnowledgeRecord({
          record: row.record,
          sourceFileName: selectedFileName.value,
          categoryId: importForm.categoryId
        })
        applyImportResult(row.uid, res.data)
      } catch (error: any) {
        applyImportResult(row.uid, {
          sourceName: selectedFileName.value,
          success: false,
          message: error?.response?.data?.message || error?.message || '法律知识导入失败'
        })
      } finally {
        importRuntime.completedRecords += 1
      }
    }

    importRuntime.currentTitle = ''

    if (importForm.rebuildIndex && (importResult.value?.importedCount || 0) > 0) {
      importStage.value = 'reindexing'
      try {
        const reindexRes = await legalKnowledgeApi.rebuildIndex()
        if (importResult.value) {
          importResult.value.reindexed = Boolean(reindexRes.data?.success)
          importResult.value.indexMessage = reindexRes.data?.message || (reindexRes.data?.success ? '专题 RAG 索引已更新' : '专题 RAG 索引刷新失败')
        }
      } catch (error: any) {
        if (importResult.value) {
          importResult.value.reindexed = false
          importResult.value.indexMessage = error?.response?.data?.message || error?.message || '专题 RAG 索引刷新失败'
        }
      }
    } else if (importResult.value) {
      importResult.value.reindexed = false
      importResult.value.indexMessage = importResult.value.importedCount > 0
        ? '法律知识已入库，未执行索引刷新'
        : '没有成功导入的法律知识，未触发索引刷新'
    }

    importStage.value = 'done'
    await loadKnowledge()

    if ((importResult.value?.importedCount || 0) > 0) {
      ElMessage.success(`成功导入 ${importResult.value?.importedCount} 条知识记录`)
    } else if ((importResult.value?.skippedCount || 0) > 0) {
      ElMessage.warning(`本次未新增知识记录，${importResult.value?.skippedCount} 条因重复已跳过`)
    } else {
      ElMessage.error('本次导入未成功')
    }
  } finally {
    importing.value = false
  }
}

async function viewImportedKnowledge(knowledgeId?: number) {
  if (!knowledgeId) {
    ElMessage.warning('未找到可查看的知识 ID')
    return
  }
  const existing = records.value.find((item) => item.id === knowledgeId)
  if (existing) {
    await openDetail(existing)
    return
  }
  const res = await legalKnowledgeApi.getKnowledgeDetail(knowledgeId)
  currentDetail.value = res.data
  detailVisible.value = true
}

async function applyFilters() {
  pagination.page = 1
  await loadKnowledge()
}

async function resetFilters() {
  filters.keyword = ''
  filters.categoryId = undefined
  filters.knowledgeType = ''
  filters.businessDomain = ''
  pagination.page = 1
  await loadKnowledge()
}

async function handleSizeChange() {
  pagination.page = 1
  await loadKnowledge()
}

function createEmptyImportResult(totalRecords = 0): LegalKnowledgeImportResponse {
  return {
    totalRecords,
    importedCount: 0,
    skippedCount: 0,
    failedCount: 0,
    reindexed: false,
    indexMessage: '',
    results: []
  }
}

function resetImportRuntime(totalRecords = 0) {
  importRuntime.totalRecords = totalRecords
  importRuntime.completedRecords = 0
  importRuntime.currentTitle = ''
}

function clearImportRecordStates() {
  Object.keys(importRecordStates).forEach((key) => {
    delete importRecordStates[key]
  })
}

function setImportRecordState(uid: string, state: ImportRecordState) {
  importRecordStates[uid] = state
}

function resolveImportRecordState(uid: string) {
  const state = importRecordStates[uid]
  switch (state?.status) {
    case 'processing':
      return {
        status: 'processing' as const,
        statusLabel: '处理中',
        statusType: 'info' as const,
        message: state.message || '正在结构化入库'
      }
    case 'success':
      return {
        status: 'success' as const,
        statusLabel: '已导入',
        statusType: 'success' as const,
        message: state.message || '知识记录已成功入库'
      }
    case 'skipped':
      return {
        status: 'skipped' as const,
        statusLabel: '已跳过',
        statusType: 'warning' as const,
        message: state.message || '知识记录重复，已跳过'
      }
    case 'failed':
      return {
        status: 'failed' as const,
        statusLabel: '失败',
        statusType: 'danger' as const,
        message: state.message || '知识记录导入失败'
      }
    default:
      return {
        status: 'waiting' as const,
        statusLabel: '待处理',
        statusType: 'info' as const,
        message: '点击查看规范详情预览'
      }
  }
}

function applyImportResult(uid: string, result: Partial<LegalKnowledgeImportResult>) {
  if (!importResult.value) {
    importResult.value = createEmptyImportResult(importRuntime.totalRecords)
  }

  const normalizedResult: LegalKnowledgeImportResult = {
    sourceName: result.sourceName || selectedFileName.value,
    success: Boolean(result.success),
    skipped: Boolean(result.skipped),
    duplicate: Boolean(result.duplicate),
    knowledgeId: result.knowledgeId,
    existingKnowledgeId: result.existingKnowledgeId,
    title: result.title,
    articleNumber: result.articleNumber,
    categoryId: result.categoryId,
    categoryName: result.categoryName,
    charCount: result.charCount,
    chunkCount: result.chunkCount,
    message: result.message
  }

  importResult.value.results.push(normalizedResult)
  importResult.value.totalRecords = importRuntime.totalRecords || importResult.value.totalRecords

  if (normalizedResult.success) {
    importResult.value.importedCount += 1
    setImportRecordState(uid, {
      status: 'success',
      message: normalizedResult.message || '知识记录已成功入库',
      knowledgeId: normalizedResult.knowledgeId
    })
    return
  }

  if (normalizedResult.skipped) {
    importResult.value.skippedCount += 1
    setImportRecordState(uid, {
      status: 'skipped',
      message: normalizedResult.message || '知识记录重复，已跳过',
      knowledgeId: normalizedResult.existingKnowledgeId
    })
    return
  }

  importResult.value.failedCount += 1
  setImportRecordState(uid, {
    status: 'failed',
    message: normalizedResult.message || '知识记录导入失败'
  })
}

function cropText(text?: string, length = 70) {
  if (!text) return '--'
  return text.length > length ? `${text.slice(0, length)}...` : text
}

function firstText(...values: unknown[]) {
  for (const value of values) {
    if (typeof value === 'string' && value.trim()) {
      return value.trim()
    }
  }
  return ''
}
</script>

<style scoped>
.hero,
.toolbar,
.table-panel,
.stat-card,
.import-panel,
.side-card,
.upload-preview,
.import-result,
.import-progress {
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
  font-weight: 600;
  letter-spacing: 0.18em;
  text-transform: uppercase;
  font-size: 13px;
  line-height: 1;
}

.hero h1,
.preview-head h3 {
  margin: 0;
}

.hero h1 {
  color: var(--text-main);
  font-size: 36px;
  font-weight: 700;
  line-height: 1.2;
}

.hero-copy {
  min-width: 0;
}

.hero-text,
.index-message,
.preview-head span,
.preview-meta,
.preview-status-note,
.preview-note,
.progress-meta,
.reindexing-tip {
  color: var(--text-muted);
}

.hero-text {
  margin: 18px 0 0;
  max-width: 1080px;
  line-height: 1.8;
  font-size: 17px;
}

.hero-actions,
.import-submit,
.import-options,
.preview-head,
.progress-meta,
.stage-chip-row,
.reindexing-tip {
  display: flex;
  gap: 12px;
  flex-wrap: wrap;
  align-items: center;
}

.hero-actions {
  justify-content: flex-end;
}

.hero-actions :deep(.el-button) {
  height: 42px;
  margin-left: 0;
  padding: 0 22px;
  font-size: 16px;
}

.preview-head,
.progress-meta {
  justify-content: space-between;
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
  grid-template-columns: minmax(260px, 1.9fr) minmax(180px, 1fr) minmax(150px, 0.9fr) minmax(220px, 1.3fr) 120px 120px;
  gap: 12px;
}

.table-panel {
  padding: 6px 20px 20px;
  overflow: hidden;
}

.table-panel :deep(.el-table td) {
  vertical-align: top;
}

.doc-title-cell {
  display: flex;
  align-items: flex-start;
  gap: 10px;
}

.doc-copy {
  min-width: 0;
}

.doc-title-cell strong,
.doc-title-cell span {
  display: block;
}

.doc-title-cell strong {
  line-height: 1.6;
}

.doc-title-cell span {
  margin-top: 6px;
  color: var(--text-muted);
  line-height: 1.7;
  display: -webkit-box;
  overflow: hidden;
  -webkit-line-clamp: 2;
  -webkit-box-orient: vertical;
}

.doc-dot {
  width: 10px;
  height: 10px;
  border-radius: 50%;
  background: linear-gradient(135deg, var(--brand), #73c0ff);
  margin-top: 8px;
}

.pagination-wrap {
  padding-top: 16px;
  display: flex;
  justify-content: flex-end;
}

.import-layout,
.document-preview {
  display: grid;
  gap: 16px;
}

.import-layout {
  grid-template-columns: 1.5fr 0.9fr;
  gap: 20px;
}

.import-panel,
.import-side {
  display: grid;
  gap: 16px;
}

.import-header {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 16px;
}

.import-header h3 {
  margin: 0;
}

.upload-area {
  width: 100%;
}

.upload-icon {
  font-size: 28px;
  color: var(--brand);
}

.side-card,
.upload-preview,
.import-result,
.import-progress {
  padding: 18px;
  background: #fff;
  border: 1px solid #e4ebf5;
}

.preview-grid {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 12px;
}

.preview-card {
  padding: 14px;
  border-radius: 18px;
  border: 1px solid #e4ebf5;
  background: #fff;
}

.preview-card--interactive {
  cursor: pointer;
  transition: transform 0.18s ease, box-shadow 0.18s ease;
}

.preview-card--interactive:hover {
  transform: translateY(-2px);
  box-shadow: 0 16px 24px rgba(15, 23, 42, 0.08);
}

.preview-card strong {
  display: block;
}

.preview-meta {
  display: flex;
  gap: 10px;
  margin-top: 8px;
  font-size: 12px;
  flex-wrap: wrap;
}

.preview-hint {
  margin-top: 10px;
  display: block;
  color: var(--brand);
}

.preview-status-note,
.preview-note {
  margin: 10px 0 0;
  line-height: 1.7;
  font-size: 13px;
}

.stage-chip {
  padding: 6px 12px;
  border-radius: 999px;
  border: 1px solid #d9e5f5;
  color: #64748b;
  background: #f8fbff;
  font-size: 12px;
}

.stage-chip--active {
  color: #1d4ed8;
  border-color: #bfdbfe;
  background: #eff6ff;
}

.stage-chip--done {
  color: #15803d;
  border-color: #bbf7d0;
  background: #f0fdf4;
}

.document-preview__meta {
  display: grid;
  grid-template-columns: repeat(4, minmax(0, 1fr));
  gap: 12px;
}

.meta-pill {
  background: #f7faff;
  border-radius: 18px;
  padding: 14px 16px;
}

.meta-pill span,
.meta-pill strong {
  display: block;
}

.meta-pill span {
  color: var(--text-muted);
  font-size: 12px;
  margin-bottom: 6px;
}

.import-metrics {
  display: grid;
  gap: 12px;
}

.import-metrics div {
  display: flex;
  justify-content: space-between;
}

.import-list {
  margin: 12px 0 0;
  padding-left: 18px;
  line-height: 1.8;
}

.action-placeholder {
  color: var(--text-muted);
}

@media (max-width: 1600px) {
  .filter-grid {
    grid-template-columns: repeat(4, minmax(0, 1fr));
  }
}

@media (max-width: 1280px) {
  .hero,
  .stats-grid {
    grid-template-columns: 1fr;
  }

  .hero {
    min-height: auto;
    align-items: flex-start;
  }

  .hero-actions {
    width: 100%;
    justify-content: flex-start;
  }

  .filter-grid,
  .document-preview__meta,
  .import-layout,
  .preview-grid {
    grid-template-columns: 1fr;
  }
}

@media (max-width: 768px) {
  .import-header,
  .import-options,
  .import-submit,
  .preview-head,
  .progress-meta {
    flex-direction: column;
    align-items: flex-start;
  }
}
</style>
