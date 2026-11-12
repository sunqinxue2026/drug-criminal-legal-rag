<template>
  <div class="detail-view">
    <section class="detail-head">
      <div class="official-badge">
        <span class="official-badge__label">Case Detail</span>
        <strong>专题案例</strong>
      </div>
      <div class="detail-meta">
        <h3>{{ item.title || '未命名案例' }}</h3>
        <p><strong>所属专题：</strong>{{ categoryName || '未分类' }}</p>
        <p><strong>来源说明：</strong>{{ sourceName || '未标注来源' }}</p>
        <p><strong>标签：</strong>{{ tags.length ? tags.join(' / ') : '暂无标签' }}</p>
        <p><strong>案例摘要：</strong>{{ item.summary || '暂无摘要' }}</p>
      </div>
    </section>

    <section v-if="editable" class="detail-block">
      <div class="asset-toolbar">
        <div>
          <h4>案例附件</h4>
          <p>支持绑定图片、PDF、DOC 等材料，方便在前台案例详情页和咨询场景中展示。</p>
        </div>
        <div class="asset-actions">
          <input ref="fileInputRef" type="file" class="hidden-input" accept="image/*,.pdf,.doc,.docx,.txt" @change="handleSelect" />
          <el-button type="primary" :loading="uploading" @click="fileInputRef?.click()">上传附件</el-button>
        </div>
      </div>

      <div v-if="item.assets?.length" class="asset-grid">
        <article v-for="asset in item.assets" :key="asset.id" class="asset-card">
          <img v-if="isImage(asset.fileUrl)" :src="asset.fileUrl" :alt="asset.fileName" class="asset-preview" />
          <div v-else class="asset-placeholder">{{ resolveAssetType(asset.assetType) }}</div>
          <div class="asset-copy">
            <strong>{{ asset.fileName }}</strong>
            <p>{{ asset.summary || '暂无材料说明' }}</p>
            <div class="asset-foot">
              <a :href="asset.fileUrl" target="_blank" rel="noreferrer">打开附件</a>
              <el-button text type="danger" @click="$emit('remove-asset', asset)">移除</el-button>
            </div>
          </div>
        </article>
      </div>
      <el-empty v-else description="当前案例还没有绑定附件" />
    </section>

    <section class="detail-block">
      <h4>{{ contentTitle }}</h4>
      <div class="detail-body">{{ item.content || '暂无正文' }}</div>
    </section>
  </div>
</template>

<script setup lang="ts">
import { ref } from 'vue'
import type { Item, ItemAsset } from '@/types'

withDefaults(defineProps<{
  item: Partial<Item>
  categoryName?: string
  tags?: string[]
  contentTitle?: string
  sourceName?: string
  editable?: boolean
  uploading?: boolean
}>(), {
  categoryName: '未分类',
  tags: () => [],
  contentTitle: '案例正文',
  sourceName: '未标注来源',
  editable: false,
  uploading: false
})

const emit = defineEmits<{
  (event: 'upload-asset', file: File): void
  (event: 'remove-asset', asset: ItemAsset): void
}>()

const fileInputRef = ref<HTMLInputElement>()

function handleSelect(event: Event) {
  const input = event.target as HTMLInputElement
  const file = input.files?.[0]
  if (!file) {
    return
  }
  emit('upload-asset', file)
  input.value = ''
}

function isImage(value?: string) {
  return /\.(png|jpe?g|gif|webp|bmp)$/i.test(value || '')
}

function resolveAssetType(type?: string) {
  const normalized = (type || '').toLowerCase()
  if (normalized.includes('image')) return '图片'
  if (normalized.includes('pdf')) return 'PDF'
  if (normalized.includes('doc')) return '文书'
  return '附件'
}
</script>

<style scoped>
.detail-view {
  display: grid;
  gap: 20px;
}

.detail-head {
  display: grid;
  grid-template-columns: 220px 1fr;
  gap: 20px;
  align-items: start;
}

.official-badge {
  min-height: 180px;
  border-radius: 24px;
  padding: 24px;
  display: flex;
  flex-direction: column;
  justify-content: flex-end;
  gap: 8px;
  color: #0f172a;
  background:
    radial-gradient(circle at top left, rgba(37, 99, 235, 0.18), transparent 52%),
    linear-gradient(160deg, #f8fbff 0%, #eef6ff 55%, #e0f2fe 100%);
  border: 1px solid #d9e8fb;
}

.official-badge__label {
  display: inline-flex;
  align-items: center;
  width: fit-content;
  padding: 5px 12px;
  border-radius: 999px;
  font-size: 12px;
  letter-spacing: 0.08em;
  text-transform: uppercase;
  color: #1d4ed8;
  background: rgba(255, 255, 255, 0.72);
}

.official-badge strong {
  font-size: 28px;
  line-height: 1.2;
}

.detail-meta {
  display: grid;
  gap: 10px;
}

.detail-meta h3,
.detail-block h4 {
  margin: 0;
}

.detail-meta p {
  margin: 0;
  line-height: 1.8;
  color: var(--text-soft);
}

.detail-block {
  padding: 22px 24px;
  border-radius: 24px;
  background: #f8fbff;
  border: 1px solid #e4ebf5;
}

.detail-body {
  margin-top: 12px;
  line-height: 1.9;
  white-space: pre-wrap;
  color: #0f172a;
}

.asset-toolbar {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 16px;
}

.asset-toolbar p {
  margin: 8px 0 0;
  color: var(--text-soft);
}

.hidden-input {
  display: none;
}

.asset-grid {
  margin-top: 16px;
  display: grid;
  gap: 14px;
}

.asset-card {
  display: grid;
  grid-template-columns: 160px 1fr;
  gap: 14px;
  padding: 14px;
  border-radius: 18px;
  background: #fff;
}

.asset-preview,
.asset-placeholder {
  width: 100%;
  aspect-ratio: 4 / 3;
  border-radius: 16px;
}

.asset-preview {
  object-fit: cover;
}

.asset-placeholder {
  display: grid;
  place-items: center;
  background: linear-gradient(135deg, #eef4ff, #dbeafe);
  color: #1d4ed8;
  font-weight: 700;
}

.asset-copy strong {
  display: block;
}

.asset-copy p {
  margin: 8px 0 14px;
  color: var(--text-soft);
}

.asset-foot {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
}

.asset-foot a {
  color: var(--brand);
  text-decoration: none;
}

@media (max-width: 960px) {
  .detail-head,
  .asset-card {
    grid-template-columns: 1fr;
  }

  .asset-toolbar {
    flex-direction: column;
  }
}
</style>
