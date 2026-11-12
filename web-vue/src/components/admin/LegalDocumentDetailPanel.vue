<template>
  <div class="detail-layout">
    <section class="detail-head panel-card">
      <div class="title-stack">
        <h3>{{ resolvedTitle }}</h3>
        <p>{{ resolvedDescription }}</p>
      </div>

      <div v-if="fieldItems.length" class="detail-meta-grid">
        <article
          v-for="item in fieldItems"
          :key="item.label"
          class="meta-chip"
          :class="{ 'meta-chip--wide': item.wide }"
        >
          <span>{{ item.label }}</span>
          <strong>{{ item.value }}</strong>
        </article>
      </div>

      <div v-if="tags.length" class="tag-band">
        <span class="tag-band__label">核心标签</span>
        <div class="tag-list">
          <span v-for="tag in tags" :key="tag" class="law-tag">{{ tag }}</span>
        </div>
      </div>
    </section>

    <section v-if="caseDetailText" class="summary-section panel-card">
      <div class="section-head">
        <div>
          <h4>案件详情</h4>
          <p>按结构字段整理后的阅读摘要</p>
        </div>
      </div>
      <div class="text-panel">{{ caseDetailText }}</div>
    </section>

    <section v-if="summaryBlocks.length" class="summary-section panel-card">
      <div class="section-head">
        <div>
          <h4>核心提取结果</h4>
          <p>只保留有意义且不重复的字段</p>
        </div>
      </div>
      <div class="summary-list">
        <article
          v-for="block in summaryBlocks"
          :key="block.label"
          class="summary-row"
          :class="{ 'summary-row--wide': block.value.length >= 100 }"
        >
          <h5>{{ block.label }}</h5>
          <p>{{ block.value }}</p>
        </article>
      </div>
    </section>

    <section class="section-card panel-card">
      <div class="section-head">
        <div>
          <h4>当事人信息</h4>
          <p>从正文中识别出的当事人和代理信息</p>
        </div>
        <span>{{ detail.parties.length }} 条</span>
      </div>

      <div v-if="detail.parties.length" class="party-grid">
        <article
          v-for="party in detail.parties"
          :key="party.id || `${party.partySeq}-${party.partyName}`"
          class="mini-card"
        >
          <strong>{{ party.partyRoleNormalized || party.partyRoleRaw || '当事人' }}</strong>
          <p>{{ party.partyName || '--' }}</p>
          <small>{{ [party.partyType, party.representativeText].filter(Boolean).join(' · ') || '--' }}</small>
        </article>
      </div>
      <el-empty v-else description="暂无当事人信息" />
    </section>

    <section class="section-card panel-card">
      <div class="section-head">
        <div>
          <h4>引用信息</h4>
          <p>文书中识别到的案号、法条和编号</p>
        </div>
        <span>{{ detail.references.length }} 条</span>
      </div>

      <el-table v-if="detail.references.length" :data="detail.references" size="small" max-height="260">
        <el-table-column label="类型" width="130">
          <template #default="{ row }">
            <el-tag size="small" effect="plain">{{ mapLegalDocumentReferenceType(row.refType) }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="refName" label="名称" min-width="160" />
        <el-table-column prop="refValue" label="内容" min-width="280" />
        <el-table-column label="标记" width="110">
          <template #default="{ row }">
            <el-tag v-if="row.isPrimary" type="success" size="small">主要引用</el-tag>
            <span v-else class="table-muted">普通引用</span>
          </template>
        </el-table-column>
      </el-table>
      <el-empty v-else description="暂无引用信息" />
    </section>

    <section class="section-card panel-card">
      <div class="section-head">
        <div>
          <h4>检索切块</h4>
          <p>供 RAG 召回使用的结构化片段</p>
        </div>
        <span>{{ detail.chunks.length }} 段</span>
      </div>

      <p v-if="detail.chunks.length" class="section-helper">
        系统会优先按照“审理经过”“法院认为”“裁判结果”等章节切分，再在单章节内部按语义长度拆成片段，避免正文被随机打散。
      </p>

      <el-collapse v-if="detail.chunks.length">
        <el-collapse-item
          v-for="chunk in detail.chunks"
          :key="chunk.id || `${chunk.sectionOrder}-${chunk.chunkIndex}`"
          :title="formatLegalDocumentChunkTitle(chunk)"
          :name="String(chunk.id || `${chunk.sectionOrder}-${chunk.chunkIndex}`)"
        >
          <div class="chunk-meta">
            <el-tag size="small" effect="plain">{{ mapLegalDocumentSectionLabel(chunk.sectionType) }}</el-tag>
            <span>检索角色：{{ mapLegalDocumentRetrievalRole(chunk.retrievalRole) }}</span>
            <span>检索权重：{{ formatLegalDocumentSectionWeight(chunk.sectionWeight) }}</span>
            <span>字符数：{{ chunk.charCount ?? '--' }}</span>
          </div>
          <div v-if="chunk.headerPath && chunk.headerPath !== chunk.sectionTitle" class="chunk-path">
            章节路径：{{ chunk.headerPath }}
          </div>
          <div class="chunk-body">{{ chunk.chunkText || '--' }}</div>
        </el-collapse-item>
      </el-collapse>
      <el-empty v-else description="暂无检索切块" />
    </section>

    <section class="section-card panel-card">
      <div class="section-head">
        <div>
          <h4>正文</h4>
          <p>已做去重、空白折叠和标题清洗</p>
        </div>
        <span>{{ formatLegalDocumentLength(bodyText) }}</span>
      </div>
      <div class="text-panel text-panel--body">{{ bodyText || '暂无正文' }}</div>
    </section>
  </div>
</template>

<script setup lang="ts">
import { computed } from 'vue'
import type { LegalDocumentDetail } from '@/types'
import {
  buildLegalDocumentFieldItems,
  buildLegalDocumentTags,
  cleanLegalDocumentBodyText,
  cropLegalDocumentText,
  formatLegalDocumentChunkTitle,
  formatLegalDocumentLength,
  formatLegalDocumentSectionWeight,
  mapLegalDocumentReferenceType,
  mapLegalDocumentRetrievalRole,
  mapLegalDocumentSectionLabel,
  pickLegalDocumentSummaryBlocks
} from '@/utils/legalDocument'

const props = withDefaults(defineProps<{
  detail: LegalDocumentDetail
  titleFallback?: string
  description?: string
}>(), {
  titleFallback: '',
  description: ''
})

const documentRecord = computed(() => props.detail.document)
const tags = computed(() => buildLegalDocumentTags(documentRecord.value))
const fieldItems = computed(() => buildLegalDocumentFieldItems(documentRecord.value))
const summaryBlocks = computed(() => pickLegalDocumentSummaryBlocks(documentRecord.value))
const caseDetailText = computed(() => cleanLegalDocumentBodyText(documentRecord.value.caseDetailText))
const bodyText = computed(() => cleanLegalDocumentBodyText(
  documentRecord.value.displayText,
  documentRecord.value.cleanText,
  documentRecord.value.rawText
))
const resolvedTitle = computed(() => documentRecord.value.title || props.titleFallback || '未命名文书')
const resolvedDescription = computed(() =>
  props.description
  || cropLegalDocumentText(
    caseDetailText.value
    || documentRecord.value.summary
    || documentRecord.value.disputeSummary
    || documentRecord.value.resultSummary
    || documentRecord.value.reasoningSummary,
    110
  )
)
</script>

<style scoped>
.detail-layout {
  display: grid;
  gap: 16px;
}

.detail-head,
.summary-section,
.section-card {
  padding: 20px;
  background: #fff;
  border: 1px solid #e4ebf5;
}

.title-stack,
.tag-band {
  display: grid;
  gap: 10px;
}

.title-stack h3,
.section-head h4,
.summary-row h5 {
  margin: 0;
}

.title-stack p,
.section-head p,
.section-helper,
.table-muted,
.tag-band__label,
.meta-chip span,
.chunk-meta,
.chunk-path,
.mini-card small {
  color: var(--text-soft);
}

.title-stack p,
.section-head p,
.section-helper,
.chunk-body,
.text-panel,
.summary-row p,
.mini-card p {
  line-height: 1.82;
}

.detail-meta-grid,
.summary-list,
.party-grid {
  display: grid;
  gap: 12px;
}

.detail-meta-grid {
  grid-template-columns: repeat(12, minmax(0, 1fr));
}

.summary-list {
  grid-template-columns: repeat(2, minmax(0, 1fr));
}

.party-grid {
  grid-template-columns: repeat(auto-fit, minmax(220px, 1fr));
}

.meta-chip,
.mini-card,
.summary-row {
  padding: 14px 16px;
  border-radius: 16px;
  background: #f8fbff;
  border: 1px solid #e6eef8;
}

.meta-chip {
  grid-column: span 3;
}

.meta-chip--wide {
  grid-column: span 6;
}

.meta-chip span {
  display: block;
  font-size: 12px;
  margin-bottom: 6px;
}

.meta-chip strong,
.summary-row p,
.mini-card p,
.chunk-body,
.text-panel {
  word-break: break-word;
}

.meta-chip strong {
  display: block;
  font-size: 15px;
  line-height: 1.7;
}

.summary-row {
  display: grid;
  gap: 8px;
  align-content: start;
}

.summary-row--wide {
  grid-column: 1 / -1;
}

.summary-row p,
.mini-card p,
.chunk-body {
  margin: 0;
  white-space: pre-wrap;
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

.section-head {
  display: flex;
  justify-content: space-between;
  align-items: flex-start;
  gap: 16px;
  margin-bottom: 14px;
}

.section-head p {
  margin: 6px 0 0;
  font-size: 13px;
}

.section-helper {
  margin: 0 0 14px;
}

.chunk-meta {
  margin-bottom: 12px;
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: 10px;
  font-size: 13px;
}

.chunk-path {
  margin-bottom: 12px;
  font-size: 13px;
}

.text-panel {
  overflow: auto;
  padding: 16px 18px;
  border-radius: 18px;
  background: #f8fbff;
  white-space: pre-wrap;
  border: 1px solid #eef3fb;
}

.text-panel--body {
  min-height: 280px;
  max-height: 520px;
}

@media (max-width: 1200px) {
  .meta-chip,
  .meta-chip--wide {
    grid-column: span 6;
  }

  .summary-list {
    grid-template-columns: 1fr;
  }
}

@media (max-width: 760px) {
  .detail-meta-grid {
    grid-template-columns: repeat(2, minmax(0, 1fr));
  }

  .meta-chip,
  .meta-chip--wide {
    grid-column: span 1;
  }

  .section-head {
    flex-direction: column;
  }
}
</style>
