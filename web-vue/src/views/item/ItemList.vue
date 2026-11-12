<template>
  <div class="page-shell cases-page">
    <header class="top-nav glass-panel">
      <div class="page-width nav-inner">
        <div class="brand" @click="router.push('/home')">
          <div class="brand-mark">
            <el-icon><ScaleToOriginal /></el-icon>
          </div>
          <strong>涉毒刑事普法系统</strong>
        </div>

        <nav class="main-nav">
          <button class="nav-link" @click="router.push('/home')">首页</button>
          <button class="nav-link active">专题案例</button>
          <button class="nav-link" @click="router.push('/chat')">AI律师咨询</button>
        </nav>
      </div>
    </header>

    <section class="page-width hero">
      <div>
        <span class="hero-kicker">专题案例检索中心</span>
        <h1>用专题案例支撑涉毒刑事问答</h1>
        <p>这里展示的是专题案例库内容，会被 AI 律师作为 RAG 检索参考，同时也承担专题案例展示与浏览入口。</p>
      </div>
      <div class="hero-badge">BGE / FAISS / Qwen</div>
    </section>

    <section class="page-width filters panel-card">
      <el-select v-model="searchForm.categoryId" placeholder="筛选专题分类" clearable>
        <el-option
          v-for="category in categories"
          :key="category.id"
          :label="category.name"
          :value="category.id"
        />
      </el-select>
      <el-input v-model="keyword" placeholder="搜索涉毒案例标题或摘要" clearable />
      <el-button type="primary" @click="handleSearch">查询专题案例</el-button>
    </section>

    <section class="page-width cases-grid">
      <article
        v-for="item in items"
        :key="item.id"
        class="case-card panel-card"
        @click="viewDetail(item.id!)"
      >
        <div class="card-cover">
          <img :src="getCaseImage(item.id)" alt="case" />
          <div class="card-badges">
            <span>
              <el-icon><View /></el-icon>
              {{ item.viewCount || 0 }}
            </span>
            <span>
              <el-icon><Star /></el-icon>
              {{ item.favoriteCount || 0 }}
            </span>
          </div>
        </div>
        <div class="card-body">
          <span class="case-category">{{ getCategoryName(item.categoryId) }}</span>
          <h3>{{ item.title }}</h3>
          <p>{{ crop(item.summary || item.content) }}</p>
          <div class="card-meta">
            <span>{{ formatDate(item.createTime) }}</span>
            <div class="card-actions">
              <button
                class="favorite-chip"
                :class="{ active: itemStore.isFavorite(item.id) }"
                @click.stop="toggleFavorite(item)"
              >
                {{ itemStore.isFavorite(item.id) ? '已收藏' : '收藏' }}
              </button>
              <strong>查看详情</strong>
            </div>
          </div>
        </div>
      </article>
    </section>

    <section
      v-if="searchForm.categoryId && !items.length"
      class="page-width knowledge-panel panel-card"
    >
      <div class="knowledge-head">
        <div>
          <span class="hero-kicker">官方指引</span>
          <h2>{{ getSelectedCategoryName() }} 当前暂无专题案例</h2>
          <p>这个分类下目前没有同步到 `item` 案例表，但知识库里已有相关官方指引与普法内容，先展示给你参考。</p>
        </div>
        <span v-if="relatedKnowledgeTotal" class="knowledge-total">共 {{ relatedKnowledgeTotal }} 条</span>
      </div>

      <div v-if="relatedKnowledgeLoading" class="knowledge-empty">
        正在加载同分类官方指引...
      </div>

      <div v-else-if="relatedKnowledge.length" class="knowledge-grid">
        <article
          v-for="knowledge in relatedKnowledge"
          :key="knowledge.id || getKnowledgeTitle(knowledge)"
          class="knowledge-card"
          @click="viewKnowledgeSource(knowledge)"
        >
          <span class="knowledge-type">{{ knowledge.knowledgeType || '官方指引' }}</span>
          <h3>{{ getKnowledgeTitle(knowledge) }}</h3>
          <p>{{ getKnowledgeExcerpt(knowledge) }}</p>
          <div class="knowledge-meta">
            <span>{{ knowledge.sourceName || knowledge.issuer || '官方来源' }}</span>
            <span>{{ formatDate(knowledge.effectiveDate || knowledge.createTime) }}</span>
          </div>
        </article>
      </div>

      <div v-else class="knowledge-empty">
        当前分类暂无专题案例，也还没有可直接展示的官方指引内容。
      </div>
    </section>

    <div class="page-width pagination-wrap">
      <el-pagination
        v-model:current-page="searchForm.pageNum"
        v-model:page-size="searchForm.pageSize"
        :total="total"
        layout="total, prev, pager, next"
        @current-change="loadItems"
      />
    </div>
  </div>
</template>

<script setup lang="ts">
import { onMounted, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { ScaleToOriginal, Star, View } from '@element-plus/icons-vue'
import { categoryApi } from '@/api/category'
import { itemApi } from '@/api/item'
import { legalKnowledgeApi } from '@/api/legalKnowledge'
import { userActionApi } from '@/api/userAction'
import { useItemStore } from '@/stores/item'
import { useUserStore } from '@/stores/user'
import type { Category, Item, LegalKnowledge } from '@/types'
import cover1 from '@/assets/images/1.jpg'
import cover2 from '@/assets/images/2.jpg'
import cover3 from '@/assets/images/3.jpg'
import cover4 from '@/assets/images/4.jpg'
import cover5 from '@/assets/images/5.jpg'
import cover6 from '@/assets/images/6.jpg'

const router = useRouter()
const route = useRoute()
const userStore = useUserStore()
const itemStore = useItemStore()

const imagePool = [cover1, cover2, cover3, cover4, cover5, cover6]
const categories = ref<Category[]>([])
const items = ref<Item[]>([])
const relatedKnowledge = ref<LegalKnowledge[]>([])
const relatedKnowledgeTotal = ref(0)
const relatedKnowledgeLoading = ref(false)
const total = ref(0)
const keyword = ref('')

const normalizeCategoryId = (value: unknown) => {
  if (value === undefined || value === null || value === '') return undefined
  const parsed = Number(value)
  return Number.isFinite(parsed) && parsed > 0 ? parsed : undefined
}

const searchForm = ref({
  pageNum: 1,
  pageSize: 12,
  categoryId: normalizeCategoryId(route.query.categoryId) as number | undefined
})

onMounted(async () => {
  userStore.loadFromStorage()
  const categoryRes = await categoryApi.getCategoryList()
  categories.value = categoryRes.data || []
  await Promise.all([loadItems(), itemStore.loadFavorites(userStore.user?.id, true)])
})

watch(
  () => route.query.categoryId,
  async (value) => {
    const nextCategoryId = normalizeCategoryId(value)
    if (nextCategoryId === searchForm.value.categoryId) return
    searchForm.value.categoryId = nextCategoryId
    searchForm.value.pageNum = 1
    await loadItems()
  }
)

const loadItems = async () => {
  const res = await itemApi.getItemPage({
    ...searchForm.value,
    keyword: keyword.value.trim() || undefined
  })
  items.value = res.data.records || []
  total.value = res.data.total || 0
  await loadRelatedKnowledge()
}

const loadKnowledgePage = async (params: {
  pageNum: number
  pageSize: number
  categoryId: number
  knowledgeType?: string
}) => {
  const res = await legalKnowledgeApi.getKnowledgePage(params)
  return {
    records: res.data.records || [],
    total: Number(res.data.total || 0)
  }
}

const normalizeText = (value?: string) => (value || '').replace(/\s+/g, ' ').trim()

const getReadableScore = (value?: string) => {
  const text = normalizeText(value)
  if (!text) return -1

  const cjkCount = (text.match(/[\u3400-\u9fff]/g) || []).length
  const mojibakeCount = (text.match(/[\u00c0-\u00ff\uFFFD]/g) || []).length
  const penalty = mojibakeCount > cjkCount ? (mojibakeCount - cjkCount) * 4 : 0

  return (cjkCount * 3) + Math.min(text.length, 160) - penalty
}

const getBestKnowledgeText = (knowledge: LegalKnowledge) => {
  const candidates = [
    knowledge.summary,
    knowledge.displayText,
    knowledge.retrievalText,
    knowledge.rawText
  ]
    .map((value) => normalizeText(value))
    .filter(Boolean)
    .sort((left, right) => getReadableScore(right) - getReadableScore(left))

  return candidates[0] || ''
}

const getKnowledgePriority = (knowledge: LegalKnowledge) => {
  const titleScore = Math.max(
    getReadableScore(knowledge.articleTitle),
    getReadableScore(knowledge.title),
    getReadableScore(knowledge.documentName)
  )
  const textScore = getReadableScore(getBestKnowledgeText(knowledge))
  const typeBonus = knowledge.knowledgeType === '官方指引'
    ? 120
    : knowledge.knowledgeType === '官方解读'
      ? 100
      : 0

  return typeBonus + titleScore + textScore
}

const isUsableKnowledge = (knowledge: LegalKnowledge) => {
  const textScore = getReadableScore(getBestKnowledgeText(knowledge))
  const titleScore = Math.max(
    getReadableScore(knowledge.articleTitle),
    getReadableScore(knowledge.title),
    getReadableScore(knowledge.documentName)
  )

  return textScore >= 24 || titleScore >= 12
}

const mergeKnowledgeRecords = (groups: LegalKnowledge[][]) => {
  const bucket = new Map<string, LegalKnowledge>()
  for (const records of groups) {
    for (const record of records) {
      const key = record.id ? `id:${record.id}` : `title:${getKnowledgeTitle(record)}`
      if (!bucket.has(key)) {
        bucket.set(key, record)
      }
    }
  }
  const merged = Array.from(bucket.values())
  const filtered = merged.filter(isUsableKnowledge)
  const ranked = (filtered.length ? filtered : merged).sort(
    (left, right) => getKnowledgePriority(right) - getKnowledgePriority(left)
  )

  return ranked
}

const loadRelatedKnowledge = async () => {
  const categoryId = searchForm.value.categoryId
  relatedKnowledge.value = []
  relatedKnowledgeTotal.value = 0

  if (!categoryId || items.value.length > 0) {
    return
  }

  relatedKnowledgeLoading.value = true
  try {
    const guidePages = await Promise.all([
      loadKnowledgePage({ pageNum: 1, pageSize: 4, categoryId, knowledgeType: '官方指引' }),
      loadKnowledgePage({ pageNum: 1, pageSize: 4, categoryId, knowledgeType: '官方解读' })
    ])

    let merged = mergeKnowledgeRecords(guidePages.map((page) => page.records))
    let mergedTotal = guidePages.reduce((sum, page) => sum + page.total, 0)

    if (merged.length < 3) {
      const fallbackPage = await loadKnowledgePage({ pageNum: 1, pageSize: 12, categoryId })
      merged = mergeKnowledgeRecords([merged, fallbackPage.records])
      mergedTotal = Math.max(mergedTotal, fallbackPage.total)
    }

    relatedKnowledge.value = merged.slice(0, 6)
    relatedKnowledgeTotal.value = mergedTotal || relatedKnowledge.value.length
  } catch (error) {
    console.error('加载同分类官方指引失败:', error)
  } finally {
    relatedKnowledgeLoading.value = false
  }
}

const handleSearch = async () => {
  searchForm.value.pageNum = 1
  await loadItems()

  const queryText = keyword.value.trim()
  const categoryName = categories.value.find((item) => item.id === searchForm.value.categoryId)?.name
  const searchText = queryText || (categoryName ? `分类筛选：${categoryName}` : '')
  if (userStore.user?.id && searchText) {
    try {
      await userActionApi.logSearch(userStore.user.id, searchText, '案例列表搜索')
    } catch (error) {
      console.error('记录案例列表搜索失败:', error)
    }
  }
}

const getCategoryName = (categoryId?: number) => {
  return categories.value.find((item) => item.id === categoryId)?.name || '未分类'
}

const getSelectedCategoryName = () => {
  return getCategoryName(searchForm.value.categoryId)
}

const getCaseImage = (id?: number) => imagePool[Number(id || 0) % imagePool.length]

const crop = (value?: string, limit = 78) => {
  if (!value) return '暂无摘要'
  return value.length > limit ? `${value.slice(0, limit)}...` : value
}

const getKnowledgeTitle = (knowledge: LegalKnowledge) => {
  return knowledge.articleTitle?.trim()
    || knowledge.title?.trim()
    || knowledge.documentName?.trim()
    || '官方指引'
}

const getKnowledgeExcerpt = (knowledge: LegalKnowledge) => {
  return crop(getBestKnowledgeText(knowledge), 110)
}

const formatDate = (value?: string) => {
  if (!value) return '最近更新'
  const date = new Date(value)
  if (Number.isNaN(date.getTime())) return value
  return `${date.getMonth() + 1}月${date.getDate()}日`
}

const viewDetail = (id: number) => router.push(`/item/${id}`)

const viewKnowledgeSource = (knowledge: LegalKnowledge) => {
  if (knowledge.sourceUrl) {
    window.open(knowledge.sourceUrl, '_blank', 'noopener,noreferrer')
    return
  }
  ElMessage.info('该指引暂未提供原始链接')
}

const toggleFavorite = async (item: Item) => {
  if (!userStore.user?.id || !item.id) {
    ElMessage.warning('请先登录后再收藏案例')
    return
  }

  try {
    const next = await itemStore.toggleFavorite(userStore.user.id, item)
    ElMessage.success(next ? '收藏成功' : '已取消收藏')
  } catch (error) {
    ElMessage.error('收藏操作失败')
    console.error(error)
  }
}
</script>

<style scoped>
.top-nav { position: sticky; top: 0; z-index: 20; }
.nav-inner { min-height: var(--nav-height); display: grid; grid-template-columns: auto auto 1fr; align-items: center; gap: 20px; }
.brand { display: flex; align-items: center; gap: 12px; cursor: pointer; }
.brand-mark { width: 42px; height: 42px; border-radius: 14px; display: grid; place-items: center; background: linear-gradient(135deg, var(--brand), var(--brand-2)); color: #fff; }
.main-nav { display: flex; gap: 10px; }
.nav-link { border: none; background: transparent; color: var(--text-muted); border-radius: 14px; padding: 10px 16px; font-size: 13px; }
.nav-link.active { background: linear-gradient(135deg, var(--brand), #5397ff); color: #fff; }
.hero { padding-top: 24px; padding-bottom: 16px; display: flex; align-items: center; justify-content: space-between; gap: 16px; }
.hero-kicker { color: var(--brand); font-weight: 700; }
.hero h1 { margin: 10px 0; font-size: var(--headline-xl); }
.hero p { max-width: 700px; color: var(--text-muted); line-height: 1.75; font-size: 13px; }
.hero-badge { background: linear-gradient(135deg, var(--brand), var(--brand-2)); color: #fff; padding: 14px 18px; border-radius: 16px; font-weight: 700; white-space: nowrap; font-size: 13px; }
.filters { border-radius: var(--radius-xl); padding: 16px; display: grid; grid-template-columns: 220px 1fr auto; gap: 12px; margin-bottom: 18px; }
.cases-grid { display: grid; grid-template-columns: repeat(3, minmax(0, 1fr)); gap: 16px; }
.case-card { border-radius: var(--radius-xl); overflow: hidden; cursor: pointer; }
.card-cover { position: relative; height: 204px; }
.card-cover img { width: 100%; height: 100%; object-fit: cover; }
.card-badges { position: absolute; right: 12px; top: 12px; display: flex; flex-direction: column; gap: 8px; }
.card-badges span { min-width: 48px; padding: 7px 10px; border-radius: 999px; background: rgba(59,115,255,.9); color: #fff; display: inline-flex; justify-content: center; align-items: center; gap: 4px; font-size: 12px; }
.card-body { padding: 16px; }
.case-category { display: inline-block; margin-bottom: 10px; padding: 6px 10px; border-radius: 999px; background: var(--brand-soft); color: var(--brand); font-size: 12px; }
.card-body h3 { margin: 0 0 10px; font-size: 17px; line-height: 1.45; }
.card-body p { margin: 0 0 16px; color: var(--text-muted); line-height: 1.72; min-height: 78px; font-size: 13px; }
.card-meta { display: flex; align-items: center; justify-content: space-between; color: var(--text-soft); font-size: 12px; }
.card-actions { display: flex; align-items: center; gap: 10px; }
.favorite-chip { border: 1px solid #d9e5f6; background: #f8fbff; color: #516175; border-radius: 999px; padding: 6px 12px; cursor: pointer; font-size: 12px; }
.favorite-chip.active { border-color: #fed7aa; background: #fff7ed; color: #c2410c; }
.card-meta strong { color: var(--brand); }
.knowledge-panel { margin-top: 22px; padding: 24px; border-radius: var(--radius-xl); }
.knowledge-head { display: flex; align-items: flex-start; justify-content: space-between; gap: 16px; margin-bottom: 18px; }
.knowledge-head h2 { margin: 10px 0 8px; font-size: 24px; line-height: 1.35; }
.knowledge-head p { margin: 0; color: var(--text-muted); line-height: 1.72; font-size: 13px; max-width: 760px; }
.knowledge-total { display: inline-flex; align-items: center; justify-content: center; min-width: 78px; padding: 8px 14px; border-radius: 999px; background: var(--brand-soft); color: var(--brand); font-size: 12px; font-weight: 700; }
.knowledge-grid { display: grid; grid-template-columns: repeat(3, minmax(0, 1fr)); gap: 16px; }
.knowledge-card { border: 1px solid rgba(214, 225, 242, .92); background: linear-gradient(180deg, #ffffff, #f8fbff); border-radius: 22px; padding: 18px; cursor: pointer; transition: transform .16s ease, box-shadow .16s ease, border-color .16s ease; }
.knowledge-card:hover { transform: translateY(-2px); border-color: rgba(59,115,255,.26); box-shadow: 0 14px 30px rgba(27, 55, 103, .08); }
.knowledge-type { display: inline-flex; margin-bottom: 12px; padding: 6px 10px; border-radius: 999px; background: #eef4ff; color: var(--brand); font-size: 12px; font-weight: 700; }
.knowledge-card h3 { margin: 0 0 10px; font-size: 17px; line-height: 1.5; }
.knowledge-card p { margin: 0 0 16px; color: var(--text-muted); line-height: 1.72; min-height: 88px; font-size: 13px; }
.knowledge-meta { display: flex; align-items: center; justify-content: space-between; gap: 12px; color: var(--text-soft); font-size: 12px; }
.knowledge-empty { min-height: 132px; display: grid; place-items: center; text-align: center; color: var(--text-muted); border-radius: 18px; background: #f8fbff; font-size: 13px; }
.pagination-wrap { display: flex; justify-content: flex-end; padding-top: 22px; padding-bottom: 32px; }
@media (max-width: 1100px) {
  .nav-inner,
  .filters,
  .cases-grid,
  .knowledge-grid {
    grid-template-columns: 1fr;
  }

  .hero,
  .knowledge-head {
    flex-direction: column;
    align-items: flex-start;
  }
}
</style>
