<template>
  <div class="page-shell doc-page">
    <header class="doc-header glass-panel">
      <div class="page-width header-inner">
        <div>
          <p class="eyebrow">Document Studio</p>
          <h1>咨询文档生成</h1>
          <p class="header-text">把多轮咨询整理成可答辩、可下载、可继续补充的结构化文档。</p>
        </div>
        <div class="header-actions">
          <el-button round @click="router.push('/chat')">返回咨询</el-button>
          <el-button type="primary" round @click="router.push('/home')">回到首页</el-button>
        </div>
      </div>
    </header>

    <main class="page-width doc-layout">
      <section class="panel-card form-panel">
        <div class="section-head">
          <div>
            <p class="eyebrow">Step 1</p>
            <h2>选择生成配置</h2>
          </div>
          <el-tag type="success">支持 Markdown / TXT / HTML</el-tag>
        </div>

        <el-form label-width="96px" class="doc-form">
          <el-form-item label="咨询会话">
            <el-select v-model="form.sessionId" placeholder="请选择会话" filterable style="width: 100%">
              <el-option
                v-for="session in sessions"
                :key="session.id"
                :label="session.title || `会话 ${session.id}`"
                :value="session.id!"
              />
            </el-select>
          </el-form-item>

          <el-form-item label="文档类型">
            <el-radio-group v-model="form.documentType">
              <el-radio-button label="report">法律咨询报告</el-radio-button>
              <el-radio-button label="summary">对话总结</el-radio-button>
            </el-radio-group>
          </el-form-item>

          <el-form-item label="模板强度">
            <el-radio-group v-model="form.template">
              <el-radio-button label="brief">简版</el-radio-button>
              <el-radio-button label="standard">标准版</el-radio-button>
              <el-radio-button label="detailed">详版</el-radio-button>
            </el-radio-group>
          </el-form-item>

          <el-form-item label="导出格式">
            <el-select v-model="form.format" style="width: 100%">
              <el-option label="Markdown" value="markdown" />
              <el-option label="纯文本 TXT" value="text" />
              <el-option label="HTML" value="html" />
            </el-select>
          </el-form-item>

          <el-form-item label="文档标题">
            <el-input v-model="form.title" maxlength="120" placeholder="可留空，系统会自动生成标题" />
          </el-form-item>

          <el-form-item label="补充说明">
            <el-input
              v-model="form.customContent"
              type="textarea"
              :rows="4"
              maxlength="1200"
              show-word-limit
              placeholder="可补充答辩说明、研究备注或老师要求的额外内容"
            />
          </el-form-item>
        </el-form>

        <div class="action-row">
          <el-button :loading="loading" @click="loadSessions">刷新会话</el-button>
          <el-button type="primary" :loading="generating" @click="generateDocument">生成文档</el-button>
        </div>
      </section>

      <section class="panel-card preview-panel">
        <div class="section-head">
          <div>
            <p class="eyebrow">Step 2</p>
            <h2>预览与导出</h2>
          </div>
          <div class="preview-actions">
            <el-button :disabled="!result" @click="copySummary">复制摘要</el-button>
            <el-button type="primary" :disabled="!result" @click="downloadDocument">下载文档</el-button>
          </div>
        </div>

        <div v-if="result" class="result-meta">
          <article class="meta-card">
            <span>生成标题</span>
            <strong>{{ result.title }}</strong>
          </article>
          <article class="meta-card">
            <span>摘要</span>
            <strong>{{ result.summary }}</strong>
          </article>
          <article class="meta-card">
            <span>消息数</span>
            <strong>{{ result.messageCount }}</strong>
          </article>
          <article class="meta-card">
            <span>生成时间</span>
            <strong>{{ result.generatedAt }}</strong>
          </article>
        </div>

        <div class="preview-box">
          <Empty v-if="!result" description="选好会话和模板后，点一次“生成文档”就可以预览。" />
          <MarkdownViewer v-else :content="result.previewMarkdown" />
        </div>
      </section>
    </main>
  </div>
</template>

<script setup lang="ts">
import { onMounted, reactive, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import Empty from '@/components/Empty.vue'
import MarkdownViewer from '@/components/MarkdownViewer.vue'
import { chatApi } from '@/api/chat'
import { documentGenerationApi, type DocumentGenerationParams } from '@/api/documentGeneration'
import { useUserStore } from '@/stores/user'
import type { ChatSession, DocumentGenerationResult } from '@/types'

const route = useRoute()
const router = useRouter()
const userStore = useUserStore()

const loading = ref(false)
const generating = ref(false)
const sessions = ref<ChatSession[]>([])
const result = ref<DocumentGenerationResult | null>(null)

const form = reactive<DocumentGenerationParams>({
  userId: undefined,
  sessionId: 0,
  documentType: 'report',
  template: 'standard',
  format: 'markdown',
  title: '',
  customContent: ''
})

onMounted(async () => {
  userStore.loadFromStorage()
  form.userId = userStore.user?.id
  await loadSessions()
  const routeSessionId = Number(route.query.sessionId || route.query.id || 0)
  if (routeSessionId) {
    form.sessionId = routeSessionId
  } else if (sessions.value.length) {
    form.sessionId = sessions.value[0].id || 0
  }
})

async function loadSessions() {
  if (!userStore.user?.id) {
    return
  }
  loading.value = true
  try {
    const res = await chatApi.getUserSessions(userStore.user.id)
    sessions.value = Array.isArray(res.data) ? res.data : []
  } finally {
    loading.value = false
  }
}

async function generateDocument() {
  if (!form.sessionId) {
    ElMessage.warning('请先选择咨询会话')
    return
  }

  generating.value = true
  try {
    const res = await documentGenerationApi.generate({
      ...form,
      userId: userStore.user?.id,
      title: form.title?.trim() || undefined,
      customContent: form.customContent?.trim() || undefined
    })
    result.value = res.data
    ElMessage.success('文档已生成')
  } finally {
    generating.value = false
  }
}

async function copySummary() {
  if (!result.value?.summary) {
    return
  }
  await navigator.clipboard.writeText(result.value.summary)
  ElMessage.success('摘要已复制')
}

function downloadDocument() {
  if (!result.value) {
    return
  }
  const blob = new Blob([result.value.content], {
    type: result.value.format === 'html' ? 'text/html;charset=utf-8' : 'text/plain;charset=utf-8'
  })
  const link = document.createElement('a')
  link.href = URL.createObjectURL(blob)
  link.download = result.value.suggestedFilename
  link.click()
  URL.revokeObjectURL(link.href)
}
</script>

<style scoped>
.doc-page {
  background: linear-gradient(180deg, #f4f7fc 0%, #eef4fa 100%);
  min-height: 100vh;
}

.doc-header {
  border-bottom: 1px solid rgba(226, 232, 240, 0.9);
}

.header-inner {
  min-height: 140px;
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 20px;
}

.eyebrow {
  margin: 0 0 8px;
  color: var(--brand);
  text-transform: uppercase;
  letter-spacing: 0.16em;
  font-size: 12px;
}

.header-inner h1,
.section-head h2 {
  margin: 0;
}

.header-text {
  margin: 10px 0 0;
  color: var(--text-soft);
  max-width: 620px;
  line-height: 1.7;
}

.header-actions,
.preview-actions,
.action-row {
  display: flex;
  gap: 12px;
  flex-wrap: wrap;
}

.doc-layout {
  padding-top: 24px;
  padding-bottom: 28px;
  display: grid;
  grid-template-columns: minmax(320px, 420px) minmax(0, 1fr);
  gap: 18px;
}

.form-panel,
.preview-panel {
  border-radius: 24px;
  padding: 20px;
}

.section-head {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 14px;
  margin-bottom: 18px;
}

.doc-form {
  margin-top: 6px;
}

.result-meta {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 12px;
  margin-bottom: 16px;
}

.meta-card {
  padding: 14px 16px;
  border-radius: 18px;
  background: #f8fbff;
  border: 1px solid #e2e8f0;
}

.meta-card span {
  display: block;
  font-size: 12px;
  color: var(--text-soft);
}

.meta-card strong {
  display: block;
  margin-top: 8px;
  line-height: 1.6;
}

.preview-box {
  min-height: 560px;
  border-radius: 22px;
  padding: 18px;
  background: linear-gradient(180deg, rgba(248, 251, 255, 0.95), rgba(255, 255, 255, 0.95));
  border: 1px solid rgba(226, 232, 240, 0.9);
  overflow: auto;
}

@media (max-width: 1100px) {
  .doc-layout {
    grid-template-columns: 1fr;
  }

  .header-inner,
  .section-head {
    flex-direction: column;
    align-items: flex-start;
  }

  .result-meta {
    grid-template-columns: 1fr;
  }
}
</style>
