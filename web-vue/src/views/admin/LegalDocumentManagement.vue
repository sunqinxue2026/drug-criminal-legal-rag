<template>
  <AdminShell active-path="/admin/legal-documents" breadcrumb="首页 / 法律文书管理">
    <section class="hero panel-card">
      <div>
        <p class="eyebrow">Legal Docs</p>
        <h1>法律文书管理</h1>
        <p class="hero-text">
          这里集中管理已导入系统的法律文书，包括法条、司法解释、典型案例发布稿和程序普法材料，所有文书都走同一套解析与入库接口。
        </p>
      </div>
      <div class="hero-actions">
        <el-button round @click="loadDocuments">刷新列表</el-button>
        <el-button type="success" round @click="openCreateDialog">新增文书</el-button>
        <el-button type="primary" round :loading="syncingOfficial" @click="syncOfficialDocuments">同步正式文书</el-button>
        <el-button round @click="openImportDialog">手动导入</el-button>
      </div>
    </section>

    <section class="stats-grid">
      <article class="stat-card panel-card">
        <span>文书总数</span>
        <strong>{{ total }}</strong>
        <small>按当前筛选条件返回</small>
      </article>
      <article class="stat-card panel-card">
        <span>当前页数量</span>
        <strong>{{ documents.length }}</strong>
        <small>本页实际加载记录</small>
      </article>
      <article class="stat-card panel-card">
        <span>结构化完成数</span>
        <strong>{{ structuredCount }}</strong>
        <small>已提取案号、法院/检察院或案由等核心字段</small>
      </article>
    </section>

    <section class="toolbar panel-card">
      <div class="filter-grid">
        <el-input v-model="filters.keyword" placeholder="输入涉毒文书标题、案号、法院/检察院或关键词" clearable @keyup.enter="applyFilters" />
        <el-select v-model="filters.categoryId" placeholder="选择专题分类" clearable>
          <el-option v-for="category in categories" :key="category.id" :label="category.name" :value="category.id" />
        </el-select>
        <el-select v-model="filters.documentType" placeholder="文书类型" clearable>
          <el-option label="法律法规" value="法律法规" />
          <el-option label="司法解释" value="司法解释" />
          <el-option label="典型案例" value="典型案例" />
          <el-option label="普法材料" value="普法材料" />
          <el-option label="判决书" value="判决书" />
          <el-option label="裁定书" value="裁定书" />
          <el-option label="决定书" value="决定书" />
          <el-option label="起诉书" value="起诉书" />
          <el-option label="通知书" value="通知书" />
        </el-select>
        <el-select v-model="filters.caseType" placeholder="案件类型" clearable>
          <el-option label="刑事" value="刑事" />
          <el-option label="综合" value="综合" />
          <el-option label="其他" value="其他" />
        </el-select>
        <el-select v-model="filters.procedureDomain" placeholder="程序领域" clearable>
          <el-option label="程序材料" value="程序材料" />
          <el-option label="证据规则" value="证据规则" />
          <el-option label="普法引导" value="普法引导" />
          <el-option label="专题文书" value="专题文书" />
          <el-option label="普通程序" value="普通程序" />
          <el-option label="审判监督" value="审判监督" />
          <el-option label="执行监督" value="执行监督" />
          <el-option label="司法协助" value="司法协助" />
          <el-option label="审查起诉" value="审查起诉" />
        </el-select>
        <el-button type="primary" @click="applyFilters">搜索</el-button>
        <el-button @click="resetFilters">重置</el-button>
      </div>
    </section>

    <section class="panel-card table-panel">
      <el-table :data="documents" v-loading="loading">
        <el-table-column label="文书标题" min-width="340">
          <template #default="{ row }">
            <div class="doc-title-cell">
              <div class="doc-dot"></div>
              <div>
                <strong>{{ row.title || '未命名文书' }}</strong>
                <span v-if="legalDocumentSecondaryText(row)">{{ legalDocumentSecondaryText(row) }}</span>
              </div>
            </div>
          </template>
        </el-table-column>
        <el-table-column label="来源 / 发布机构" min-width="240">
          <template #default="{ row }">
            <div class="meta-stack">
              <strong>{{ row.sourceName || row.caseNumber || '暂无来源' }}</strong>
              <span>{{ row.courtName || row.sourceType || '暂无机构信息' }}</span>
            </div>
          </template>
        </el-table-column>
        <el-table-column label="文书类型" min-width="140">
          <template #default="{ row }">
            <el-tag effect="plain">{{ row.documentSubtype || row.documentType || '--' }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="专题标签" min-width="220">
          <template #default="{ row }">
            <div class="tag-list">
              <span v-for="tag in buildLegalDocumentTags(row)" :key="tag" class="law-tag">{{ tag }}</span>
            </div>
          </template>
        </el-table-column>
        <el-table-column label="发布日期" width="150">
          <template #default="{ row }">{{ formatDate(row.decisionDate || row.effectiveDate) }}</template>
        </el-table-column>
        <el-table-column label="操作" width="240" fixed="right">
          <template #default="{ row }">
            <el-button size="small" @click="openDetail(row)">详情</el-button>
            <el-button size="small" type="primary" link @click="openEditDialog(row)">编辑</el-button>
            <el-button size="small" type="danger" link @click="removeDocument(row)">删除</el-button>
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
          @current-change="loadDocuments"
          @size-change="handleSizeChange"
        />
      </div>
    </section>

    <el-dialog v-model="detailVisible" title="专题文书详情" width="1180px">
      <LegalDocumentDetailPanel v-if="currentDetail" :detail="currentDetail" />
    </el-dialog>

    <LegalDocumentEditorDialog
      v-model="editorVisible"
      :mode="editorMode"
      :form="editorForm"
      :categories="categories"
      :submitting="editorSubmitting"
      @submit="submitEditor"
    />

    <el-dialog v-model="importVisible" title="批量导入涉毒专题文书" width="980px">
      <div class="import-layout">
        <section class="import-panel">
          <div class="import-header">
            <div>
              <h3>文书导入</h3>
              <p>支持 `.doc`、`.docx`、`.pdf` 等文件，导入后会自动结构化并生成涉毒专题检索切块。</p>
            </div>
            <el-tag type="success" effect="dark">可选自动重建 RAG 索引</el-tag>
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
            :on-preview="handleUploadFilePreview"
          >
            <el-icon class="upload-icon"><UploadFilled /></el-icon>
            <div class="el-upload__text">
              把涉毒专题文书拖到这里，或 <em>点击选择文件</em>
            </div>
          </el-upload>

          <div class="import-submit">
            <el-button @click="clearImportFiles">清空文件</el-button>
            <el-button type="primary" :loading="importing" @click="submitImport">开始导入</el-button>
          </div>

          <section v-if="importing || importStage !== 'idle'" class="import-progress panel-card">
            <div class="preview-head">
              <h3>导入进度</h3>
              <span>{{ importStageLabel }}</span>
            </div>
            <el-progress :percentage="importProgress" :status="importStage === 'done' ? 'success' : undefined" />
            <div class="progress-meta">
              <span>已处理 {{ importRuntime.completedFiles }} / {{ importRuntime.totalFiles || uploadPreviewRows.length }} 个文件</span>
              <span>进度按真实完成文件数计算</span>
            </div>
            <div class="stage-chip-row">
              <span class="stage-chip" :class="{ 'stage-chip--active': importStage === 'processing', 'stage-chip--done': importRuntime.completedFiles > 0 }">
                文件入库
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
              <span>索引刷新没有真实百分比，完成后会自动给出结果。</span>
            </div>
          </section>

          <section v-if="uploadPreviewRows.length" class="upload-preview panel-card">
            <div class="preview-head">
              <h3>导入前预览</h3>
              <span>已选择 {{ uploadPreviewRows.length }} 个文件</span>
            </div>
            <div class="preview-grid">
              <article
                v-for="file in uploadPreviewRows"
                :key="file.uid"
                class="preview-card preview-card--interactive"
                @click="openUploadPreview(file)"
              >
                <strong>{{ file.name }}</strong>
                <div class="preview-meta">
                  <span>{{ file.extLabel }}</span>
                  <span>{{ file.sizeLabel }}</span>
                  <el-tag size="small" effect="plain" :type="file.statusType">{{ file.statusLabel }}</el-tag>
                </div>
                <small class="preview-hint">点击查看专题文书结构化预览</small>
                <p v-if="file.message" class="preview-status-note">{{ file.message }}</p>
              </article>
            </div>
          </section>
        </section>

        <aside class="import-side">
          <div class="side-card">
            <h3>导入说明</h3>
            <ul class="import-list">
              <li>每个专题文书会先抽取纯文本，再做案号、法院/检察院、案由、法条等结构化解析。</li>
              <li>系统会对抽取结果做一轮清洗，尽量去掉页码、重复空白和异常断行。</li>
              <li>系统会优先按章节切块，不足时再按段落和句号附近切分。</li>
              <li>开启重建索引后，会优先使用新文书切块刷新 `legal_cases`。</li>
            </ul>
          </div>

          <div class="side-card" v-if="importResult">
            <h3>导入结果</h3>
            <div class="import-metrics">
              <div><span>文件总数</span><strong>{{ importResult.totalFiles }}</strong></div>
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
        <el-table :data="importResult.results" max-height="280">
          <el-table-column label="文件名" min-width="220" prop="fileName" />
          <el-table-column label="状态" width="100">
            <template #default="{ row }">
              <el-tag :type="row.success ? 'success' : row.skipped ? 'warning' : 'danger'">
                {{ row.success ? '成功' : row.skipped ? '跳过' : '失败' }}
              </el-tag>
            </template>
          </el-table-column>
          <el-table-column label="文书标题" min-width="220" prop="title" />
          <el-table-column label="案号" min-width="160" prop="caseNumber" />
          <el-table-column label="分块数" width="100" prop="chunkCount" />
          <el-table-column label="说明" min-width="280">
            <template #default="{ row }">
              <span>{{ row.message }}</span>
              <span v-if="row.existingTitle" class="duplicate-note"> 已存在：{{ row.existingTitle }}</span>
            </template>
          </el-table-column>
          <el-table-column label="操作" width="120" fixed="right">
            <template #default="{ row }">
              <el-button
                v-if="row.success && row.documentId"
                type="primary"
                link
                @click="viewImportedDocument(row.documentId)"
              >
                查看文书
              </el-button>
              <span v-else class="action-placeholder">--</span>
            </template>
          </el-table-column>
        </el-table>
      </section>
    </el-dialog>

    <el-dialog
      v-model="previewDialogVisible"
      title="专题文书详情预览"
      width="1180px"
      @closed="resetPreviewState"
    >
      <div class="document-preview">
        <div class="document-preview__meta">
          <div class="meta-pill">
            <span>文件名</span>
            <strong>{{ previewInfo.name || '--' }}</strong>
          </div>
          <div class="meta-pill">
            <span>文件类型</span>
            <strong>{{ previewInfo.extLabel || '--' }}</strong>
          </div>
          <div class="meta-pill">
            <span>文件大小</span>
            <strong>{{ previewInfo.sizeLabel || '--' }}</strong>
          </div>
          <div class="meta-pill">
            <span>清洗后字数</span>
            <strong>{{ previewInfo.charCount }}</strong>
          </div>
        </div>
        <div class="preview-note">
          点击预览后看到的是导入前的专题文书结构化详情，导入成功后“详情”页会使用同一套字段布局。
        </div>

        <el-skeleton :rows="10" animated v-if="previewLoading" />
        <el-empty v-else-if="previewError" :description="previewError" />
        <LegalDocumentDetailPanel
          v-else-if="previewDetail"
          :detail="previewDetail"
          :title-fallback="previewInfo.name"
          description="这里展示的是导入前的专题文书结构化详情预览，导入成功后详情页会展示同一套字段结果。"
        />
      </div>
    </el-dialog>
  </AdminShell>
</template>
<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import type { UploadFile, UploadInstance, UploadUserFile } from 'element-plus'
import { Loading, UploadFilled } from '@element-plus/icons-vue'
import { categoryApi } from '@/api/category'
import { legalDocumentApi } from '@/api/legalDocument'
import AdminShell from '@/components/admin/AdminShell.vue'
import LegalDocumentDetailPanel from '@/components/admin/LegalDocumentDetailPanel.vue'
import LegalDocumentEditorDialog from '@/components/admin/LegalDocumentEditorDialog.vue'
import type {
  Category,
  LegalDocument,
  LegalDocumentDetail,
  LegalDocumentImportResult,
  LegalDocumentImportResponse
} from '@/types'
import { buildLegalDocumentTags, cropLegalDocumentText } from '@/utils/legalDocument'

type PreviewUploadRow = {
  uid: string
  name: string
  extLabel: string
  sizeLabel: string
  rawFile: File | null
  status: ImportFileStatus
  statusLabel: string
  statusType: 'info' | 'warning' | 'success' | 'danger'
  message: string
  documentId?: number
}

type ImportFileStatus = 'waiting' | 'processing' | 'success' | 'skipped' | 'failed'
type ImportStage = 'idle' | 'processing' | 'reindexing' | 'done'
type ImportFileState = {
  status: ImportFileStatus
  message: string
  documentId?: number
}

const loading = ref(false)
const importing = ref(false)
const syncingOfficial = ref(false)
const importStage = ref<ImportStage>('idle')
const detailVisible = ref(false)
const editorVisible = ref(false)
const editorSubmitting = ref(false)
const editorMode = ref<'create' | 'edit'>('create')
const importVisible = ref(false)
const previewDialogVisible = ref(false)
const uploadRef = ref<UploadInstance>()

const categories = ref<Category[]>([])
const documents = ref<LegalDocument[]>([])
const total = ref(0)
const currentDetail = ref<LegalDocumentDetail | null>(null)
const previewDetail = ref<LegalDocumentDetail | null>(null)
const uploadFiles = ref<UploadUserFile[]>([])
const importResult = ref<LegalDocumentImportResponse | null>(null)
const editorForm = reactive<LegalDocument>({
  id: undefined,
  sourceType: 'manual_entry',
  sourceName: '手工录入',
  sourceUrl: '',
  title: '',
  caseNumber: '',
  courtName: '',
  documentType: '',
  documentSubtype: '',
  caseType: '',
  categoryId: undefined,
  causeNormalized: '',
  businessDomain: '',
  procedureDomain: '',
  decisionResult: '',
  decisionDate: '',
  effectiveDate: '',
  keywords: '',
  aliases: '',
  summary: '',
  factsSummary: '',
  issuesSummary: '',
  reasoningSummary: '',
  resultSummary: '',
  legalBasisText: '',
  caseDetailText: '',
  displayText: '',
  status: 1
})
const previewLoading = ref(false)
const previewError = ref('')
const previewInfo = reactive({
  name: '',
  extLabel: '',
  sizeLabel: '',
  charCount: 0
})

const pagination = reactive({
  page: 1,
  pageSize: 10
})

const filters = reactive({
  keyword: '',
  categoryId: undefined as number | undefined,
  documentType: '',
  caseType: '',
  procedureDomain: ''
})

const importForm = reactive({
  categoryId: undefined as number | undefined,
  rebuildIndex: true
})
const importRuntime = reactive({
  totalFiles: 0,
  completedFiles: 0,
  currentFileName: ''
})
const importFileStates = reactive<Record<string, ImportFileState>>({})

const structuredCount = computed(() =>
  documents.value.filter((item) => item.caseNumber || item.courtName || item.causeNormalized).length
)

const uploadPreviewRows = computed<PreviewUploadRow[]>(() =>
  uploadFiles.value.map((file, index) => ({
    ...resolveImportFileState(String(file.uid || `${file.name}-${index}`)),
    uid: String(file.uid || `${file.name}-${index}`),
    name: file.name,
    extLabel: getFileExtensionLabel(file.name),
    sizeLabel: formatFileSize(file.size || 0),
    rawFile: file.raw instanceof File ? file.raw : null,
    documentId: importFileStates[String(file.uid || `${file.name}-${index}`)]?.documentId
  }))
)

const importProgress = computed(() =>
  importRuntime.totalFiles ? Math.round((importRuntime.completedFiles / importRuntime.totalFiles) * 100) : 0
)

const importStageLabel = computed(() => {
  switch (importStage.value) {
    case 'processing':
      return importRuntime.currentFileName
        ? `已完成 ${importRuntime.completedFiles}/${importRuntime.totalFiles}，正在处理《${importRuntime.currentFileName}》`
        : `已完成 ${importRuntime.completedFiles}/${importRuntime.totalFiles} 个文件`
    case 'reindexing':
      return '文件处理完成，正在刷新专题 RAG 索引'
    case 'done':
      return `导入完成，共处理 ${importRuntime.completedFiles}/${importRuntime.totalFiles} 个文件`
    default:
      return '等待开始'
  }
})

const importStatusMessage = computed(() => {
  if (importStage.value === 'processing') {
    return importRuntime.currentFileName
      ? `正在处理《${importRuntime.currentFileName}》，结果会逐条写入下方回执。`
      : '正在处理文书，结果会逐条写入下方回执。'
  }
  if (importStage.value === 'reindexing') {
    return '所有文件已处理完成，正在刷新专题 RAG 索引。索引刷新不显示伪百分比，完成后会直接返回结果。'
  }
  return importResult.value?.indexMessage || ''
})

onMounted(async () => {
  await Promise.all([loadCategories(), loadDocuments()])
})

async function loadCategories() {
  const res = await categoryApi.getCategoryList()
  categories.value = Array.isArray(res.data) ? res.data : []
}

async function loadDocuments() {
  loading.value = true
  try {
    const res = await legalDocumentApi.getDocumentPage({
      pageNum: pagination.page,
      pageSize: pagination.pageSize,
      categoryId: filters.categoryId,
      keyword: filters.keyword.trim() || undefined,
      documentType: filters.documentType || undefined,
      caseType: filters.caseType || undefined,
      procedureDomain: filters.procedureDomain || undefined
    })
    documents.value = res.data?.records || []
    total.value = Number(res.data?.total || 0)
  } finally {
    loading.value = false
  }
}

function legalDocumentSecondaryText(row: LegalDocument) {
  const text = cropLegalDocumentText(row.summary || row.resultSummary || row.factsSummary, 56)
  return text && text !== (row.title || '') ? text : ''
}

async function applyFilters() {
  pagination.page = 1
  await loadDocuments()
}

function resetFilters() {
  filters.keyword = ''
  filters.categoryId = undefined
  filters.documentType = ''
  filters.caseType = ''
  filters.procedureDomain = ''
  pagination.page = 1
  void loadDocuments()
}

function handleSizeChange(size: number) {
  pagination.pageSize = size
  pagination.page = 1
  void loadDocuments()
}

async function openDetail(row: LegalDocument) {
  if (!row.id) {
    ElMessage.warning('未找到文书 ID')
    return
  }
  const res = await legalDocumentApi.getDocumentDetail(row.id)
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
    caseNumber: '',
    courtName: '',
    documentType: '',
    documentSubtype: '',
    caseType: '',
    categoryId: undefined,
    causeNormalized: '',
    businessDomain: '',
    procedureDomain: '',
    decisionResult: '',
    decisionDate: '',
    effectiveDate: '',
    keywords: '',
    aliases: '',
    summary: '',
    factsSummary: '',
    issuesSummary: '',
    reasoningSummary: '',
    resultSummary: '',
    legalBasisText: '',
    caseDetailText: '',
    displayText: '',
    status: 1
  })
}

function openCreateDialog() {
  editorMode.value = 'create'
  resetEditorForm()
  editorVisible.value = true
}

async function openEditDialog(row: LegalDocument) {
  if (!row.id) {
    ElMessage.warning('未找到可编辑的文书记录')
    return
  }
  const res = await legalDocumentApi.getDocumentDetail(row.id)
  const document = res.data?.document
  if (!document) {
    ElMessage.warning('未找到文书详情')
    return
  }

  editorMode.value = 'edit'
  Object.assign(editorForm, {
    id: document.id,
    sourceType: document.sourceType || 'manual_entry',
    sourceName: document.sourceName || '手工录入',
    sourceUrl: document.sourceUrl || '',
    title: document.title || '',
    caseNumber: document.caseNumber || '',
    courtName: document.courtName || '',
    documentType: document.documentType || '',
    documentSubtype: document.documentSubtype || '',
    caseType: document.caseType || '',
    categoryId: document.categoryId,
    causeNormalized: document.causeNormalized || document.causeRaw || '',
    businessDomain: document.businessDomain || '',
    procedureDomain: document.procedureDomain || '',
    decisionResult: document.decisionResult || '',
    decisionDate: document.decisionDate || '',
    effectiveDate: document.effectiveDate || '',
    keywords: document.keywords || '',
    aliases: document.aliases || '',
    summary: document.summary || '',
    factsSummary: document.factsSummary || '',
    issuesSummary: document.issuesSummary || '',
    reasoningSummary: document.reasoningSummary || '',
    resultSummary: document.resultSummary || '',
    legalBasisText: document.legalBasisText || '',
    caseDetailText: document.caseDetailText || '',
    displayText: document.displayText || document.cleanText || '',
    status: document.status ?? 1
  })
  editorVisible.value = true
}

async function submitEditor() {
  if (!editorForm.title?.trim()) {
    ElMessage.warning('请输入文书标题')
    return
  }
  if (!editorForm.displayText?.trim() && !editorForm.caseDetailText?.trim() && !editorForm.summary?.trim()) {
    ElMessage.warning('请至少填写正文、案件详情或摘要')
    return
  }

  editorSubmitting.value = true
  try {
    const payload: LegalDocument = {
      ...editorForm,
      sourceType: editorForm.sourceType?.trim() || 'manual_entry',
      sourceName: editorForm.sourceName?.trim() || '手工录入',
      sourceUrl: editorForm.sourceUrl?.trim() || '',
      title: editorForm.title?.trim() || '',
      caseNumber: editorForm.caseNumber?.trim() || '',
      courtName: editorForm.courtName?.trim() || '',
      documentType: editorForm.documentType?.trim() || '',
      documentSubtype: editorForm.documentSubtype?.trim() || '',
      caseType: editorForm.caseType?.trim() || '',
      causeNormalized: editorForm.causeNormalized?.trim() || '',
      businessDomain: editorForm.businessDomain?.trim() || '',
      procedureDomain: editorForm.procedureDomain?.trim() || '',
      decisionResult: editorForm.decisionResult?.trim() || '',
      decisionDate: editorForm.decisionDate || undefined,
      effectiveDate: editorForm.effectiveDate || undefined,
      keywords: editorForm.keywords?.trim() || '',
      aliases: editorForm.aliases?.trim() || '',
      summary: editorForm.summary?.trim() || '',
      factsSummary: editorForm.factsSummary?.trim() || '',
      issuesSummary: editorForm.issuesSummary?.trim() || '',
      reasoningSummary: editorForm.reasoningSummary?.trim() || '',
      resultSummary: editorForm.resultSummary?.trim() || '',
      legalBasisText: editorForm.legalBasisText?.trim() || '',
      caseDetailText: editorForm.caseDetailText?.trim() || '',
      displayText: editorForm.displayText?.trim() || '',
      status: editorForm.status ?? 1
    }

    if (editorMode.value === 'create') {
      await legalDocumentApi.createDocument(payload)
      ElMessage.success('法律文书已新增')
    } else {
      await legalDocumentApi.updateDocument(payload)
      ElMessage.success('法律文书已更新')
    }

    editorVisible.value = false
    await loadDocuments()
  } finally {
    editorSubmitting.value = false
  }
}

async function removeDocument(row: LegalDocument) {
  if (!row.id) {
    ElMessage.warning('未找到可删除的文书记录')
    return
  }

  try {
    await ElMessageBox.confirm(
      '删除后会同时移除该份文书及其结构化分块，并刷新专题索引。确定继续吗？',
      '删除文书语料',
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
    const res = await legalDocumentApi.deleteDocument(row.id, true)
    if (currentDetail.value?.document?.id === row.id) {
      detailVisible.value = false
      currentDetail.value = null
    }
    if (documents.value.length <= 1 && pagination.page > 1) {
      pagination.page -= 1
    }
    await loadDocuments()
    ElMessage.success(res.message || '文书语料已删除')
  } finally {
    loading.value = false
  }
}

function openImportDialog() {
  importVisible.value = true
  importResult.value = null
  resetImportRuntime(uploadPreviewRows.value.length)
  clearImportFileStates()
  importStage.value = 'idle'
}

async function syncOfficialDocuments() {
  syncingOfficial.value = true
  try {
    const res = await legalDocumentApi.importOfficialDocuments(importForm.categoryId, false)
    importResult.value = res.data || null
    importStage.value = 'done'
    await loadDocuments()
    ElMessage.success(res.message || res.data?.indexMessage || '官方文书同步完成')
  } finally {
    syncingOfficial.value = false
  }
}

function clearImportFiles() {
  uploadFiles.value = []
  uploadRef.value?.clearFiles()
  resetImportRuntime()
  clearImportFileStates()
  importStage.value = 'idle'
}

function resetPreviewState() {
  previewLoading.value = false
  previewError.value = ''
  previewDetail.value = null
  previewInfo.name = ''
  previewInfo.extLabel = ''
  previewInfo.sizeLabel = ''
  previewInfo.charCount = 0
}

async function openUploadPreview(file: PreviewUploadRow) {
  resetPreviewState()
  previewDialogVisible.value = true
  previewInfo.name = file.name
  previewInfo.extLabel = file.extLabel
  previewInfo.sizeLabel = file.sizeLabel

  if (!file.rawFile) {
    previewError.value = '无法读取当前文件实体，请重新选择后再试。'
    return
  }

  previewLoading.value = true
  try {
    const res = await legalDocumentApi.previewDocument(file.rawFile, importForm.categoryId)
    previewDetail.value = res.data
    previewInfo.charCount = res.data?.document?.cleanText?.length || 0
    if (!previewDetail.value?.document?.cleanText) {
      previewError.value = '未能从文档中提取到可预览内容'
    }
  } catch (error: any) {
    previewError.value = error?.response?.data?.message || error?.message || '文档预览失败'
  } finally {
    previewLoading.value = false
  }
}

function handleUploadFilePreview(file: UploadFile) {
  const uid = String(file.uid || file.name)
  void openUploadPreview({
    ...resolveImportFileState(uid),
    uid,
    name: file.name,
    extLabel: getFileExtensionLabel(file.name),
    sizeLabel: formatFileSize(file.size || 0),
    rawFile: file.raw instanceof File ? file.raw : null,
    documentId: importFileStates[uid]?.documentId
  })
}

async function submitImport() {
  const files = uploadPreviewRows.value.filter((file): file is PreviewUploadRow & { rawFile: File } => file.rawFile instanceof File)

  if (!files.length) {
    ElMessage.warning('请先选择要导入的法律文书')
    return
  }

  importing.value = true
  importStage.value = 'processing'
  resetImportRuntime(files.length)
  clearImportFileStates()
  importResult.value = createEmptyImportResult(files.length)
  try {
    for (const file of files) {
      importRuntime.currentFileName = file.name
      setImportFileState(file.uid, {
        status: 'processing',
        message: '正在抽取正文并结构化入库'
      })
      try {
        const res = await legalDocumentApi.importSingleDocument(file.rawFile, importForm.categoryId, false)
        const singleResult = res.data.results?.[0] || {
          fileName: file.name,
          success: false,
          message: '未返回导入结果'
        }
        applyImportResult(file.uid, singleResult)
      } catch (error: any) {
        const message = error?.response?.data?.message || error?.message || '文书导入失败'
        applyImportResult(file.uid, {
          fileName: file.name,
          success: false,
          message
        })
      } finally {
        importRuntime.completedFiles += 1
      }
    }

    importRuntime.currentFileName = ''

    if (importForm.rebuildIndex && (importResult.value?.importedCount || 0) > 0) {
      importStage.value = 'reindexing'
      try {
        const reindexRes = await legalDocumentApi.rebuildIndex()
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
        ? '文书已入库，未执行索引更新'
        : '没有成功导入的文书，未触发索引更新'
    }

    importStage.value = 'done'
    await loadDocuments()

    if ((importResult.value?.importedCount || 0) > 0) {
      ElMessage.success(`成功导入 ${importResult.value?.importedCount} 份文书`)
    } else if ((importResult.value?.skippedCount || 0) > 0) {
      ElMessage.warning(`本次未新增文书，${importResult.value?.skippedCount} 个文件因重复已跳过`)
    } else {
      ElMessage.error('本次导入未成功')
    }
  } finally {
    importing.value = false
  }
}

async function viewImportedDocument(documentId?: number) {
  if (!documentId) {
    ElMessage.warning('未找到可查看的文书 ID')
    return
  }
  const target = documents.value.find((item) => item.id === documentId)
  await openDetail(target || { id: documentId })
}

function createEmptyImportResult(totalFiles = 0): LegalDocumentImportResponse {
  return {
    totalFiles,
    importedCount: 0,
    skippedCount: 0,
    failedCount: 0,
    reindexed: false,
    indexMessage: '',
    results: []
  }
}

function resetImportRuntime(totalFiles = 0) {
  importRuntime.totalFiles = totalFiles
  importRuntime.completedFiles = 0
  importRuntime.currentFileName = ''
}

function clearImportFileStates() {
  Object.keys(importFileStates).forEach((key) => {
    delete importFileStates[key]
  })
}

function setImportFileState(uid: string, state: ImportFileState) {
  importFileStates[uid] = state
}

function resolveImportFileState(uid: string) {
  const state = importFileStates[uid]
  switch (state?.status) {
    case 'processing':
      return {
        status: 'processing' as const,
        statusLabel: '处理中',
        statusType: 'info' as const,
        message: state.message || '正在抽取正文并结构化入库'
      }
    case 'success':
      return {
        status: 'success' as const,
        statusLabel: '已导入',
        statusType: 'success' as const,
        message: state.message || '文书已成功入库'
      }
    case 'skipped':
      return {
        status: 'skipped' as const,
        statusLabel: '已跳过',
        statusType: 'warning' as const,
        message: state.message || '文书重复，已跳过'
      }
    case 'failed':
      return {
        status: 'failed' as const,
        statusLabel: '失败',
        statusType: 'danger' as const,
        message: state.message || '文书导入失败'
      }
    default:
      return {
        status: 'waiting' as const,
        statusLabel: '待处理',
        statusType: 'info' as const,
        message: '点击查看结构化详情预览'
      }
  }
}

function applyImportResult(uid: string, result: LegalDocumentImportResult) {
  if (!importResult.value) {
    importResult.value = createEmptyImportResult(importRuntime.totalFiles)
  }

  importResult.value.results.push(result)
  importResult.value.totalFiles = importRuntime.totalFiles || importResult.value.totalFiles

  if (result.success) {
    importResult.value.importedCount += 1
    setImportFileState(uid, {
      status: 'success',
      message: result.message || '文书已成功入库',
      documentId: result.documentId
    })
    return
  }

  if (result.skipped) {
    importResult.value.skippedCount += 1
    setImportFileState(uid, {
      status: 'skipped',
      message: result.existingTitle ? `重复文书，已存在：${result.existingTitle}` : (result.message || '文书重复，已跳过'),
      documentId: result.existingDocumentId
    })
    return
  }

  importResult.value.failedCount += 1
  setImportFileState(uid, {
    status: 'failed',
    message: result.message || '文书导入失败'
  })
}

function formatDate(value?: string) {
  if (!value) return '--'
  const date = new Date(value)
  if (Number.isNaN(date.getTime())) return value
  const year = date.getFullYear()
  const month = `${date.getMonth() + 1}`.padStart(2, '0')
  const day = `${date.getDate()}`.padStart(2, '0')
  return `${year}-${month}-${day}`
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
</script>
<style scoped>
.hero,
.hero-actions,
.preview-head,
.import-header,
.import-options,
.import-submit,
.section-head {
  display: flex;
  justify-content: space-between;
  align-items: center;
  gap: 16px;
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
  letter-spacing: 0.18em;
  text-transform: uppercase;
  font-size: 13px;
  font-weight: 600;
  line-height: 1;
}

.hero h1,
.import-header h3,
.preview-head h3,
.section-head h4,
.title-stack h3,
.summary-card h5 {
  margin: 0;
}

.hero h1 {
  color: var(--text-main);
  font-size: 36px;
  font-weight: 700;
  line-height: 1.2;
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

.hero-text,
.index-message,
.preview-head span,
.preview-meta,
.mini-card small,
.meta-stack span,
.title-stack p,
.section-helper,
.table-muted,
.tag-band__label,
.meta-pill span {
  color: var(--text-muted);
}

.hero-text {
  margin: 18px 0 0;
  max-width: 1080px;
  font-size: 17px;
  line-height: 1.8;
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
  grid-template-columns: 1.3fr repeat(4, minmax(0, 1fr)) auto auto;
  gap: 12px;
}

.table-panel {
  padding: 6px 20px 20px;
}

.doc-title-cell {
  display: flex;
  align-items: flex-start;
  gap: 12px;
}

.doc-dot {
  width: 12px;
  height: 12px;
  border-radius: 999px;
  margin-top: 6px;
  background: linear-gradient(135deg, #0f766e, #2dd4bf);
  flex-shrink: 0;
}

.doc-title-cell strong,
.doc-title-cell span,
.meta-stack strong,
.meta-stack span {
  display: block;
}

.doc-title-cell span {
  margin-top: 6px;
  color: var(--text-soft);
  font-size: 13px;
}

.meta-stack {
  display: grid;
  gap: 4px;
}

.tag-list {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
}

.law-tag {
  background: #ecf7ec;
  color: #25803b;
  padding: 4px 10px;
  border-radius: 999px;
  font-size: 12px;
}

.pagination-wrap {
  margin-top: 20px;
  display: flex;
  justify-content: flex-end;
}

.detail-layout,
.document-preview,
.import-panel,
.import-side,
.preview-split,
.chunk-preview-grid {
  display: grid;
  gap: 16px;
}

.detail-head,
.summary-section,
.section-card,
.side-card,
.upload-preview,
.import-result,
.import-progress,
.preview-section {
  padding: 18px;
}

.title-stack,
.tag-band,
.reference-list {
  display: grid;
  gap: 10px;
}

.detail-meta-grid,
.document-preview__meta,
.summary-grid,
.party-grid,
.preview-grid {
  display: grid;
  gap: 12px;
}

.detail-meta-grid,
.document-preview__meta {
  grid-template-columns: repeat(auto-fit, minmax(180px, 1fr));
}

.summary-grid {
  grid-template-columns: repeat(auto-fit, minmax(240px, 1fr));
}

.party-grid {
  grid-template-columns: repeat(auto-fit, minmax(220px, 1fr));
}

.party-grid--single {
  grid-template-columns: 1fr;
}

.meta-pill,
.mini-card,
.summary-card,
.reference-item,
.chunk-preview-card {
  padding: 14px;
  border-radius: 16px;
  background: #f8fbff;
  border: 1px solid #e6eef8;
}

.meta-pill span {
  display: block;
  font-size: 12px;
  margin-bottom: 6px;
}

.summary-card p,
.mini-card p,
.reference-item p,
.chunk-preview-card p,
.chunk-body {
  margin: 8px 0 0;
  line-height: 1.85;
  white-space: pre-wrap;
}

.section-card,
.upload-preview,
.import-result,
.import-progress,
.preview-section {
  background: #fff;
  border: 1px solid #e4ebf5;
}

.section-helper {
  margin: 6px 0 16px;
  line-height: 1.8;
}

.chunk-meta,
.preview-meta,
.chunk-preview-meta,
.reference-head {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: 10px;
}

.chunk-meta {
  margin-bottom: 12px;
  font-size: 13px;
  color: var(--text-soft);
}

.chunk-path {
  margin-bottom: 12px;
  font-size: 13px;
  color: var(--text-soft);
}

.raw-text-box {
  min-height: 220px;
  max-height: 420px;
  overflow: auto;
  padding: 16px;
  border-radius: 18px;
  background: #f8fbff;
  line-height: 1.85;
  white-space: pre-wrap;
}

.import-layout {
  display: grid;
  grid-template-columns: 1.5fr 0.9fr;
  gap: 20px;
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

.preview-hint {
  margin-top: 10px;
  display: block;
  color: var(--brand);
}

.preview-status-note,
.preview-note,
.progress-meta,
.reindexing-tip {
  color: var(--text-soft);
  font-size: 13px;
}

.preview-status-note,
.preview-note {
  margin: 10px 0 0;
  line-height: 1.7;
}

.progress-meta,
.stage-chip-row,
.reindexing-tip {
  margin-top: 12px;
}

.progress-meta,
.stage-chip-row,
.reindexing-tip {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: 10px;
}

.progress-meta {
  justify-content: space-between;
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

.reindexing-tip {
  padding-top: 4px;
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

.duplicate-note {
  color: #b45309;
}

.action-placeholder {
  color: var(--text-soft);
}

@media (max-width: 1200px) {
  .stats-grid,
  .filter-grid,
  .import-layout,
  .preview-split {
    grid-template-columns: 1fr;
  }

  .hero-actions,
  .import-header,
  .import-options,
  .import-submit,
  .preview-head,
  .section-head {
    flex-direction: column;
    align-items: flex-start;
  }

  .hero {
    min-height: auto;
    grid-template-columns: 1fr;
    align-items: flex-start;
  }

  .hero-actions {
    justify-content: flex-start;
  }
}
</style>
