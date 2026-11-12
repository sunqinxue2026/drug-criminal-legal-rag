<template>
  <div v-if="item" class="page-shell detail-page">
    <header class="top-nav glass-panel">
      <div class="page-width top-nav-inner">
        <div class="brand" @click="router.push('/home')">
          <div class="brand-mark">
            <el-icon><ScaleToOriginal /></el-icon>
          </div>
          <div>
            <strong>涉毒刑事法律咨询系统</strong>
            <span>专题案例详情</span>
          </div>
        </div>
        <div class="nav-actions">
          <el-button round @click="router.push('/items')">返回案例列表</el-button>
          <el-button type="primary" round @click="router.push(`/chat?question=${encodeURIComponent(item.title)}`)">基于本案咨询</el-button>
          <el-button round @click="router.push(`/document-generation?sessionId=${recentSessionId}`)" :disabled="!recentSessionId">生成咨询文档</el-button>
        </div>
      </div>
    </header>

    <main class="page-width detail-layout">
      <section class="detail-main">
        <article class="detail-hero panel-card">
          <div class="hero-copy">
            <span class="detail-category">{{ categoryName }}</span>
            <h1>{{ item.title }}</h1>
            <p class="hero-summary">{{ item.summary || '该案例已进入专题知识库，可用于案例检索、相似案例召回和 AI 咨询。' }}</p>
            <div class="detail-meta">
              <span>浏览 {{ item.viewCount || 0 }}</span>
              <span>收藏 {{ item.favoriteCount || 0 }}</span>
              <span>附件 {{ item.assets?.length || 0 }}</span>
            </div>
          </div>
          <img :src="item.assets?.[0]?.fileUrl || coverImage" alt="cover" class="hero-image" />
        </article>

        <article class="detail-card panel-card">
          <h3>案件摘要</h3>
          <p>{{ item.summary || '暂无摘要信息' }}</p>
        </article>

        <article v-if="item.assets?.length" class="detail-card panel-card">
          <div class="section-head">
            <h3>案件附件</h3>
            <span>{{ item.assets.length }} 份材料</span>
          </div>
          <div class="asset-grid">
            <article v-for="asset in item.assets" :key="asset.id" class="asset-card">
              <img v-if="isImage(asset.fileUrl)" :src="asset.fileUrl" :alt="asset.fileName" class="asset-preview" />
              <div v-else class="asset-placeholder">{{ resolveAssetLabel(asset.assetType) }}</div>
              <div class="asset-copy">
                <strong>{{ asset.fileName }}</strong>
                <p>{{ asset.summary || '该材料可作为案例附件或证据说明使用。' }}</p>
                <div class="asset-actions">
                  <span class="asset-type">{{ resolveAssetLabel(asset.assetType) }}</span>
                  <a :href="asset.fileUrl" target="_blank" rel="noreferrer">打开附件</a>
                </div>
              </div>
            </article>
          </div>
        </article>

        <article class="detail-card panel-card">
          <h3>案件正文</h3>
          <div class="detail-content">{{ item.content }}</div>
        </article>
      </section>

      <aside class="detail-side">
        <div class="side-card panel-card">
          <h3>检索标签</h3>
          <div class="tags">
            <span v-for="tag in tags" :key="tag">{{ tag }}</span>
          </div>
        </div>

        <div class="side-card panel-card">
          <h3>RAG 使用建议</h3>
          <ul class="info-list">
            <li>可直接把案例标题带入聊天，验证相似案例召回效果。</li>
            <li>如果附件里有通知书、聊天截图或 PDF，可和正文一起作为证据材料展示。</li>
            <li>结合咨询结果再生成文档，可形成“案例查看 - 咨询 - 文档输出”的完整闭环。</li>
          </ul>
        </div>

        <div class="side-card panel-card">
          <h3>建议操作</h3>
          <el-button type="primary" block @click="router.push('/chat')">发起 AI 咨询</el-button>
          <el-button block :loading="favoriteLoading" @click="handleFavorite">
            {{ isFavorite ? '取消收藏' : '收藏案例' }}
          </el-button>
        </div>
      </aside>
    </main>
  </div>
</template>

<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { ScaleToOriginal } from '@element-plus/icons-vue'
import { categoryApi } from '@/api/category'
import { chatApi } from '@/api/chat'
import { itemApi } from '@/api/item'
import { useItemStore } from '@/stores/item'
import { useUserStore } from '@/stores/user'
import case1 from '@/assets/images/1.jpg'
import case2 from '@/assets/images/2.jpg'
import case3 from '@/assets/images/3.jpg'
import case4 from '@/assets/images/4.jpg'
import case5 from '@/assets/images/5.jpg'
import case6 from '@/assets/images/6.jpg'
import type { Category, Item, ItemAsset } from '@/types'

const route = useRoute()
const router = useRouter()
const userStore = useUserStore()
const itemStore = useItemStore()
const imagePool = [case1, case2, case3, case4, case5, case6]

const item = ref<Item | null>(null)
const categories = ref<Category[]>([])
const isFavorite = ref(false)
const favoriteLoading = ref(false)
const recentSessionId = ref(0)

onMounted(async () => {
  userStore.loadFromStorage()
  try {
    const [itemRes, categoryRes] = await Promise.all([
      itemApi.getItemDetail(Number(route.params.id), userStore.user?.id),
      categoryApi.getCategoryList()
    ])
    item.value = itemRes.data
    categories.value = categoryRes.data || []
    itemStore.setCurrentItem(item.value)
    await itemStore.loadFavorites(userStore.user?.id, true)
    await loadFavoriteState()
    await loadRecentSession()
  } catch (error) {
    ElMessage.error('获取案例详情失败')
    console.error(error)
  }
})

const loadFavoriteState = async () => {
  if (!userStore.user?.id || !item.value?.id) {
    isFavorite.value = false
    return
  }
  isFavorite.value = await itemStore.syncFavoriteStatus(userStore.user.id, item.value.id)
}

const loadRecentSession = async () => {
  if (!userStore.user?.id) return
  const res = await chatApi.getUserSessions(userStore.user.id)
  const sessions = Array.isArray(res.data) ? res.data : []
  recentSessionId.value = sessions[0]?.id || 0
}

const categoryName = computed(() => categories.value.find((entry) => entry.id === item.value?.categoryId)?.name || '未分类')

const tags = computed(() => {
  const source = item.value?.tags || item.value?.summary || ''
  const parsed = source.split(/[，、\s]+/).map((entry) => entry.trim()).filter(Boolean)
  if (parsed.length) return parsed.slice(0, 6)
  return [categoryName.value, '法律案例', 'RAG 检索']
})

const coverImage = computed(() => imagePool[Math.abs(Number(item.value?.id || 0)) % imagePool.length])

const handleFavorite = async () => {
  if (!userStore.user?.id || !item.value?.id) {
    ElMessage.warning('请先登录后再收藏案例')
    return
  }
  favoriteLoading.value = true
  try {
    const next = await itemStore.toggleFavorite(userStore.user.id, item.value)
    isFavorite.value = next
    ElMessage.success(next ? '收藏成功' : '已取消收藏')
  } finally {
    favoriteLoading.value = false
  }
}

const isImage = (value?: string) => /\.(png|jpe?g|gif|webp|bmp)$/i.test(value || '')
const resolveAssetLabel = (type?: string) => {
  const normalized = (type || '').toLowerCase()
  if (normalized.includes('image')) return '图片证据'
  if (normalized.includes('pdf')) return 'PDF 材料'
  if (normalized.includes('doc')) return '文书材料'
  return '附件材料'
}
</script>

<style scoped>
.detail-page {
  background: linear-gradient(180deg, #f4f8fe, #eef3fa);
}

.top-nav {
  position: sticky;
  top: 0;
  z-index: 20;
  border-bottom: 1px solid rgba(229, 236, 244, 0.8);
}

.top-nav-inner {
  min-height: var(--nav-height);
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 18px;
}

.brand {
  display: flex;
  align-items: center;
  gap: 12px;
  cursor: pointer;
}

.brand strong,
.brand span {
  display: block;
}

.brand span {
  color: var(--text-soft);
  font-size: 12px;
}

.brand-mark {
  width: 42px;
  height: 42px;
  border-radius: 14px;
  display: grid;
  place-items: center;
  background: linear-gradient(135deg, #4f8cff, #2fd0c5);
  color: #fff;
}

.nav-actions {
  display: flex;
  gap: 12px;
  flex-wrap: wrap;
  justify-content: flex-end;
}

.detail-layout {
  padding-top: 24px;
  padding-bottom: 32px;
  display: grid;
  grid-template-columns: 1.45fr 0.72fr;
  gap: 18px;
}

.detail-hero,
.detail-card,
.side-card {
  border-radius: var(--radius-xl);
}

.detail-hero {
  padding: 24px;
  display: grid;
  grid-template-columns: 1.2fr 0.8fr;
  gap: 20px;
  align-items: center;
}

.detail-category {
  display: inline-block;
  padding: 6px 14px;
  border-radius: 999px;
  background: #edf6ff;
  color: #2563eb;
  font-size: 13px;
}

.detail-hero h1 {
  margin: 14px 0 12px;
  font-size: var(--headline-xl);
  line-height: 1.22;
}

.hero-summary {
  color: var(--text-soft);
  line-height: 1.72;
  font-size: 13px;
}

.detail-meta {
  display: flex;
  flex-wrap: wrap;
  gap: 16px;
  color: #7b8699;
  margin-top: 16px;
  font-size: 12px;
}

.hero-image {
  width: 100%;
  border-radius: 22px;
  aspect-ratio: 1 / 1;
  object-fit: cover;
}

.detail-card,
.side-card {
  padding: 20px;
}

.detail-card + .detail-card,
.side-card + .side-card {
  margin-top: 16px;
}

.detail-card h3,
.side-card h3 {
  margin-top: 0;
  font-size: 18px;
}

.detail-card p,
.detail-content {
  line-height: 1.82;
  color: #485266;
  white-space: pre-wrap;
  font-size: 13px;
}

.section-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
}

.asset-grid {
  margin-top: 14px;
  display: grid;
  gap: 14px;
}

.asset-card {
  display: grid;
  grid-template-columns: 150px 1fr;
  gap: 16px;
  padding: 14px;
  border-radius: 18px;
  background: #f8fbff;
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
  margin: 8px 0 12px;
}

.asset-actions {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
}

.asset-type {
  padding: 6px 10px;
  border-radius: 999px;
  background: #eff6ff;
  color: #2563eb;
  font-size: 12px;
}

.asset-actions a {
  color: var(--brand);
  text-decoration: none;
}

.tags {
  display: flex;
  flex-wrap: wrap;
  gap: 10px;
}

.tags span {
  padding: 7px 12px;
  border-radius: 999px;
  background: #eef6ff;
  color: #2563eb;
  font-size: 12px;
}

.info-list {
  margin: 0;
  padding-left: 18px;
  color: var(--text-soft);
  line-height: 1.76;
  font-size: 13px;
}

@media (max-width: 1100px) {
  .detail-layout,
  .detail-hero,
  .asset-card {
    grid-template-columns: 1fr;
  }

  .top-nav-inner {
    align-items: flex-start;
    flex-direction: column;
    padding-top: 12px;
    padding-bottom: 12px;
  }

  .nav-actions {
    width: 100%;
    justify-content: flex-start;
  }
}
</style>
