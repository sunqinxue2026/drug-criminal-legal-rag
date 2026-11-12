<template>
  <div class="detail-layout">
    <section class="detail-head panel-card">
      <div class="title-stack">
        <h3>{{ resolvedTitle }}</h3>
        <p>{{ descriptionText }}</p>
      </div>
      <div class="detail-meta-grid">
        <div v-for="item in fieldItems" :key="item.label" class="meta-pill">
          <span>{{ item.label }}</span>
          <strong>{{ item.value }}</strong>
        </div>
      </div>
    </section>

    <section class="summary-section panel-card">
      <div class="section-head">
        <h4>律师阅读版正文</h4>
        <span>{{ detail.knowledge.articleNumber || detail.knowledge.knowledgeType || '--' }}</span>
      </div>
      <div class="raw-text-box">{{ detail.knowledge.displayText || '暂无正文' }}</div>
    </section>

    <section class="summary-section panel-card">
      <div class="section-head">
        <h4>检索文本</h4>
        <span>用于向量化与重排</span>
      </div>
      <div class="raw-text-box">{{ detail.knowledge.retrievalText || '暂无检索文本' }}</div>
    </section>

    <section class="section-card panel-card">
      <div class="section-head">
        <h4>检索切块</h4>
        <span>{{ detail.chunks.length }} 个</span>
      </div>
      <el-collapse>
        <el-collapse-item
          v-for="chunk in detail.chunks"
          :key="chunk.id || chunk.chunkIndex"
          :title="formatLegalKnowledgeChunkTitle(chunk)"
          :name="String(chunk.id || chunk.chunkIndex || 0)"
        >
          <div class="chunk-meta">
            <el-tag size="small" effect="plain">{{ chunk.retrievalRole || 'law_basis' }}</el-tag>
            <span>权重：{{ chunk.sectionWeight ?? '--' }}</span>
            <span>字符数：{{ chunk.charCount ?? '--' }}</span>
          </div>
          <div v-if="chunk.headerPath" class="chunk-path">章节路径：{{ chunk.headerPath }}</div>
          <div class="chunk-body">{{ chunk.chunkText || '--' }}</div>
        </el-collapse-item>
      </el-collapse>
    </section>
  </div>
</template>

<script setup lang="ts">
import { computed } from 'vue'
import type { LegalKnowledgeDetail } from '@/types'
import { buildLegalKnowledgeFieldItems, formatLegalKnowledgeChunkTitle } from '@/utils/legalKnowledge'

const props = withDefaults(defineProps<{
  detail: LegalKnowledgeDetail
  titleFallback?: string
  description?: string
}>(), {
  titleFallback: '',
  description: ''
})

const fieldItems = computed(() => buildLegalKnowledgeFieldItems(props.detail.knowledge))
const resolvedTitle = computed(() =>
  props.detail.knowledge.title || props.detail.knowledge.documentName || props.titleFallback || '未命名规范'
)
const descriptionText = computed(() =>
  props.description || props.detail.knowledge.summary || props.detail.knowledge.articleTitle || props.detail.knowledge.topic || '--'
)
</script>

<style scoped>
.detail-layout,
.summary-section,
.section-card {
  display: flex;
  flex-direction: column;
  gap: 16px;
}

.detail-head,
.summary-section,
.section-card {
  border-radius: 24px;
}

.title-stack h3,
.section-head h4 {
  margin: 0;
}

.title-stack p {
  color: var(--text-muted);
}

.detail-meta-grid {
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

.section-head,
.chunk-meta {
  display: flex;
  gap: 12px;
  flex-wrap: wrap;
}

.section-head {
  justify-content: space-between;
}

.raw-text-box,
.chunk-body {
  white-space: pre-wrap;
  line-height: 1.8;
  color: var(--text-main);
}

.chunk-path {
  color: var(--text-muted);
  font-size: 13px;
}

@media (max-width: 1280px) {
  .detail-meta-grid {
    grid-template-columns: repeat(2, minmax(0, 1fr));
  }
}

@media (max-width: 768px) {
  .detail-meta-grid {
    grid-template-columns: 1fr;
  }
}
</style>
