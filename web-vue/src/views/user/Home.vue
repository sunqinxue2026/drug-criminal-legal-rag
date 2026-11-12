<template>
  <div class="page-shell home-page">
    <header class="top-nav glass-panel">
      <div class="page-width nav-inner">
        <div class="brand" @click="router.push('/home')">
          <div class="brand-mark">
            <el-icon><ScaleToOriginal /></el-icon>
          </div>
          <div class="brand-copy">
            <strong>涉毒刑事法律咨询系统</strong>
            <span>风险分析 + 普法问答 + 专题检索</span>
          </div>
        </div>

        <nav class="main-nav">
          <button class="nav-link active">首页</button>
          <button class="nav-link" @click="router.push('/items')">专题案例</button>
          <button class="nav-link" @click="router.push('/chat')">涉毒咨询</button>
          <button class="nav-link" @click="router.push('/document-generation')">咨询文档</button>
        </nav>

        <div class="nav-actions">
          <span class="status-badge" :class="{ warning: !servicesReady }">
            <span class="dot"></span>
            {{ serviceText }}
          </span>
          <button class="pill-link" @click="router.push('/favorites')">案例收藏</button>
          <button class="pill-link" @click="router.push('/document-generation')">文档生成</button>
          <el-dropdown @command="handleCommand">
            <div class="user-chip">
              <el-avatar :size="38">{{ displayUserName.slice(0, 1) }}</el-avatar>
              <div>
                <strong>{{ displayUserName }}</strong>
                <span>{{ userStore.isAdmin ? '管理员' : '用户' }}</span>
              </div>
            </div>
            <template #dropdown>
              <el-dropdown-menu>
                <el-dropdown-item command="profile">个人中心</el-dropdown-item>
                <el-dropdown-item command="chat">涉毒咨询</el-dropdown-item>
                <el-dropdown-item command="document">咨询文档</el-dropdown-item>
                <el-dropdown-item command="admin" v-if="userStore.isAdmin">管理控制台</el-dropdown-item>
                <el-dropdown-item command="logout" divided>退出登录</el-dropdown-item>
              </el-dropdown-menu>
            </template>
          </el-dropdown>
        </div>
      </div>
    </header>

    <div class="category-strip">
      <div class="page-width category-row">
        <button class="category-item" :class="{ active: !activeCategoryId }" @click="goToCategory()">
          全部专题
        </button>
        <button
          v-for="category in quickCategories"
          :key="category.id"
          class="category-item"
          :class="{ active: activeCategoryId === category.id }"
          :style="getCategoryStyle(category)"
          @click="goToCategory(category.id)"
        >
          <img v-if="category.iconUrl" :src="category.iconUrl" alt="" class="category-icon" />
          {{ category.name }}
        </button>
      </div>
    </div>

    <section class="page-width hero-grid">
      <div class="hero-carousel panel-card">
        <div class="carousel-frame">
          <img :src="heroImages[currentHeroIndex]" alt="hero" />
          <div class="carousel-copy">
            <span class="hero-kicker">专题法律 RAG 咨询演示</span>
            <h1>把法律知识、官方文书、案例素材和咨询流程串成一条完整链路</h1>
            <p>
              现在首页已经把咨询入口、案例浏览、个性化推荐和文档生成连起来了，既适合演示产品流程，也适合答辩时说明系统闭环。
            </p>
            <div class="hero-actions">
              <button class="hero-button primary" @click="router.push('/chat')">开始涉毒咨询</button>
              <button class="hero-button" @click="router.push('/items')">浏览专题案例</button>
              <button class="hero-button" @click="router.push('/document-generation')">生成咨询文档</button>
            </div>
          </div>
        </div>
        <div class="carousel-track">
          <button
            v-for="(image, index) in heroImages"
            :key="image"
            class="thumb"
            :class="{ active: currentHeroIndex === index }"
            @click="currentHeroIndex = index"
          >
            <img :src="image" alt="thumb" />
          </button>
        </div>
      </div>

      <aside class="profile-card panel-card">
        <div class="profile-head">
          <el-avatar :size="52">{{ displayUserName.slice(0, 1) }}</el-avatar>
          <div>
            <strong>{{ profileName }}</strong>
            <span>{{ userStore.isAdmin ? '管理员账户' : '普通用户' }}</span>
          </div>
        </div>

        <div class="profile-stats">
          <button class="stat-entry" @click="router.push('/items')">
            <strong>{{ homeStats.caseCount }}</strong>
            <span>案例总数</span>
          </button>
          <button class="stat-entry" @click="router.push('/items')">
            <strong>{{ homeStats.categoryCount }}</strong>
            <span>专题分类</span>
          </button>
          <button class="stat-entry" @click="router.push('/chat')">
            <strong>{{ homeStats.sessionCount }}</strong>
            <span>咨询会话</span>
          </button>
        </div>

        <div class="profile-stats single">
          <button class="stat-entry" @click="router.push('/favorites')">
            <strong>{{ itemStore.favoriteCount }}</strong>
            <span>我的收藏</span>
          </button>
        </div>

        <div class="profile-section">
          <div class="section-title">
            <span>高频专题</span>
            <button @click="router.push('/items')">更多</button>
          </div>
          <div class="hot-grid">
            <button
              v-for="category in quickCategories"
              :key="category.id"
              :style="getCategoryStyle(category)"
              @click="goToCategory(category.id)"
            >
              <img v-if="category.iconUrl" :src="category.iconUrl" alt="" class="hot-grid-icon" />
              {{ category.name }}
            </button>
          </div>
        </div>
      </aside>
    </section>

    <section v-if="recommendedItems.length" class="page-width recommendation-section">
      <div class="section-header">
        <div>
          <h2>个性化案例推荐</h2>
          <p>根据你的搜索、浏览、收藏和咨询习惯，优先推荐更贴近当前关注点的案例。</p>
        </div>
        <button class="more-link" @click="loadRecommendations">刷新推荐</button>
      </div>

      <div class="recommendation-meta">
        <div v-if="interestCategories.length" class="recommendation-group">
          <span>偏好专题</span>
          <div class="tag-row">
            <span
              v-for="category in interestCategories"
              :key="category.categoryId"
              class="interest-pill"
              :style="getCategoryStyle(category)"
            >
              <img v-if="category.iconUrl" :src="category.iconUrl" alt="" class="interest-icon" />
              {{ category.name }}
            </span>
          </div>
        </div>
        <div v-if="interestKeywords.length" class="recommendation-group">
          <span>高频关键词</span>
          <div class="tag-row">
            <span v-for="keyword in interestKeywords" :key="keyword" class="interest-keyword">{{ keyword }}</span>
          </div>
        </div>
      </div>

      <div class="case-grid recommendation-grid">
        <article
          v-for="item in recommendedItems"
          :key="`recommend-${item.id}`"
          class="case-card panel-card"
          @click="viewDetail(item.id!)"
        >
          <div class="card-cover">
            <img :src="item.assets?.[0]?.fileUrl || getCaseImage(item.id)" alt="cover" />
            <div class="recommend-overlay">{{ item.recommendReason || '基于偏好推荐' }}</div>
          </div>
          <div class="card-body">
            <span class="category-pill">{{ getCategoryName(item.categoryId) }}</span>
            <h3>{{ item.title }}</h3>
            <p>{{ crop(item.summary || item.content) }}</p>
            <div class="card-footer">
              <span>匹配分 {{ item.recommendScore || 0 }}</span>
              <button @click.stop="viewDetail(item.id!)">查看详情</button>
            </div>
          </div>
        </article>
      </div>
    </section>

    <section class="page-width cases-section">
      <div class="section-header">
        <div>
          <h2>热门专题案例</h2>
          <p>用于演示系统当前案例库中的结构化案例与专题语料，适合作为 RAG 召回与引用样本。</p>
        </div>
        <button class="more-link" @click="router.push('/items')">更多</button>
      </div>

      <div class="case-grid">
        <article v-for="item in featuredItems" :key="item.id" class="case-card panel-card" @click="viewDetail(item.id!)">
          <div class="card-cover">
            <img :src="item.assets?.[0]?.fileUrl || getCaseImage(item.id)" alt="cover" />
            <div class="card-overlay">
              <span class="metric">
                <el-icon><View /></el-icon>
                {{ item.viewCount || 0 }}
              </span>
              <span class="metric">
                <el-icon><Star /></el-icon>
                {{ item.favoriteCount || 0 }}
              </span>
            </div>
          </div>
          <div class="card-body">
            <span class="category-pill">{{ getCategoryName(item.categoryId) }}</span>
            <h3>{{ item.title }}</h3>
            <p>{{ crop(item.summary || item.content) }}</p>
            <div class="card-footer">
              <span>{{ formatDate(item.createTime) }}</span>
              <div class="card-footer-actions">
                <button class="favorite-chip" :class="{ active: itemStore.isFavorite(item.id) }" @click.stop="toggleFavorite(item)">
                  {{ itemStore.isFavorite(item.id) ? '已收藏' : '收藏' }}
                </button>
                <button @click.stop="viewDetail(item.id!)">查看详情</button>
              </div>
            </div>
          </div>
        </article>
      </div>
    </section>
  </div>
</template>

<script setup lang="ts">
import { computed, onBeforeUnmount, onMounted, reactive, ref } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessageBox } from 'element-plus'
import axios from 'axios'
import { ScaleToOriginal, Star, View } from '@element-plus/icons-vue'
import { categoryApi } from '@/api/category'
import { itemApi } from '@/api/item'
import { recommendApi } from '@/api/recommend'
import { chatApi } from '@/api/chat'
import { ENV } from '@/config/constants'
import { useItemStore } from '@/stores/item'
import { useUserStore } from '@/stores/user'
import type { Category, Item, RecommendationInterestCategory } from '@/types'
import heroCourtroom from '@/assets/images/hero-courtroom.jpg'
import heroConsultation from '@/assets/images/hero-consultation.jpg'
import heroDocuments from '@/assets/images/hero-documents.jpg'
import heroLawLibrary from '@/assets/images/hero-law-library.jpg'
import cover1 from '@/assets/images/1.jpg'
import cover2 from '@/assets/images/2.jpg'
import cover3 from '@/assets/images/3.jpg'
import cover4 from '@/assets/images/4.jpg'
import cover5 from '@/assets/images/5.jpg'
import cover6 from '@/assets/images/6.jpg'

const router = useRouter()
const userStore = useUserStore()
const itemStore = useItemStore()

const categories = ref<Category[]>([])
const items = ref<Item[]>([])
const recommendedItems = ref<Item[]>([])
const interestKeywords = ref<string[]>([])
const interestCategories = ref<RecommendationInterestCategory[]>([])
const itemsTotal = ref(0)
const sessionsCount = ref(0)
const currentHeroIndex = ref(0)
const activeCategoryId = ref<number>()
const serviceState = reactive({
  spring: false,
  flask: false,
  llmReady: false
})

const heroImages = [heroCourtroom, heroConsultation, heroDocuments, heroLawLibrary]
const cardImages = [cover1, cover2, cover3, cover4, cover5, cover6]
let heroTimer: number | undefined

onMounted(async () => {
  userStore.loadFromStorage()
  await Promise.allSettled([
    loadCategories(),
    loadItems(),
    loadRecommendations(),
    itemStore.loadFavorites(userStore.user?.id, true),
    loadSessionCount(),
    loadServiceState()
  ])

  heroTimer = window.setInterval(() => {
    currentHeroIndex.value = (currentHeroIndex.value + 1) % heroImages.length
  }, 3600)
})

onBeforeUnmount(() => {
  if (heroTimer) {
    window.clearInterval(heroTimer)
  }
})

const displayUserName = computed(() => userStore.user?.username || '访客')
const profileName = computed(() => userStore.user?.realName || displayUserName.value)
const quickCategories = computed(() => categories.value.slice(0, 6))
const featuredItems = computed(() => items.value.slice(0, 6))
const homeStats = computed(() => ({
  caseCount: itemsTotal.value,
  categoryCount: categories.value.length,
  sessionCount: sessionsCount.value
}))
const servicesReady = computed(() => serviceState.spring && serviceState.flask)
const serviceText = computed(() => {
  if (!servicesReady.value) return '部分服务待检查'
  return serviceState.llmReady ? '检索与模型服务正常' : '检索正常，模型待检查'
})

const loadCategories = async () => {
  const res = await categoryApi.getCategoryList()
  categories.value = Array.isArray(res.data) ? res.data : []
}

const loadItems = async () => {
  const res = await itemApi.getItemPage({ pageNum: 1, pageSize: 12 })
  items.value = res.data?.records || []
  itemsTotal.value = Number(res.data?.total || 0)
}

const loadRecommendations = async () => {
  if (!userStore.user?.id) {
    recommendedItems.value = []
    interestKeywords.value = []
    interestCategories.value = []
    return
  }
  const res = await recommendApi.getCases(userStore.user.id, 6)
  recommendedItems.value = res.data?.items || []
  interestKeywords.value = (res.data?.interestKeywords || []).map((item) => item.keyword)
  interestCategories.value = res.data?.interestCategories || []
}

const loadSessionCount = async () => {
  if (!userStore.user?.id) {
    sessionsCount.value = 0
    return
  }
  const res = await chatApi.getUserSessions(userStore.user.id)
  sessionsCount.value = Array.isArray(res.data) ? res.data.length : 0
}

const loadServiceState = async () => {
  const [springHealth, flaskHealth, modelInfo] = await Promise.allSettled([
    axios.get(`${ENV.API_BASE_URL}/health`, { timeout: 6000 }),
    axios.get(`${ENV.ALGORITHM_API_BASE_URL}/health`, { timeout: 6000 }),
    axios.get(`${ENV.ALGORITHM_API_BASE_URL}/api/llm/models`, { timeout: 6000 })
  ])
  serviceState.spring = springHealth.status === 'fulfilled' && springHealth.value.data?.code === 200
  serviceState.flask = flaskHealth.status === 'fulfilled' && flaskHealth.value.data?.code === 200
  serviceState.llmReady = modelInfo.status === 'fulfilled' && Boolean(modelInfo.value.data?.data?.has_api_key)
}

const handleCommand = async (command: string) => {
  if (command === 'profile') return void router.push('/profile')
  if (command === 'chat') return void router.push('/chat')
  if (command === 'document') return void router.push('/document-generation')
  if (command === 'admin') return void router.push('/admin')
  if (command === 'logout') {
    await ElMessageBox.confirm('确认退出登录吗？', '提示', { type: 'warning' })
    userStore.logout()
    router.push('/login')
  }
}

const getCategoryName = (categoryId?: number) => categories.value.find((item) => item.id === categoryId)?.name || '未分类'
const getCategoryStyle = (category?: Category | RecommendationInterestCategory) => ({
  '--category-color': category?.themeColor || '#2563eb'
})
const getCaseImage = (id?: number) => cardImages[Number(id || 0) % cardImages.length]
const crop = (value?: string) => !value ? '暂无摘要' : (value.length > 48 ? `${value.slice(0, 48)}...` : value)
const formatDate = (value?: string) => {
  if (!value) return '最近入库'
  const date = new Date(value)
  if (Number.isNaN(date.getTime())) return value
  return `${date.getMonth() + 1}月${date.getDate()}日`
}
const viewDetail = (id: number) => router.push(`/item/${id}`)

const toggleFavorite = async (item: Item) => {
  if (!userStore.user?.id || !item.id) return
  await itemStore.toggleFavorite(userStore.user.id, item)
}

const goToCategory = (categoryId?: number) => {
  activeCategoryId.value = categoryId
  router.push({ path: '/items', query: categoryId ? { categoryId: String(categoryId) } : {} })
}
</script>

<style scoped>
.top-nav {
  position: sticky;
  top: 0;
  z-index: 20;
  border-bottom: 1px solid rgba(229, 236, 244, 0.8);
}

.nav-inner {
  min-height: var(--nav-height);
  display: grid;
  grid-template-columns: auto auto 1fr;
  align-items: center;
  gap: 20px;
}

.brand {
  display: flex;
  align-items: center;
  gap: 14px;
  cursor: pointer;
}

.brand-mark {
  width: 42px;
  height: 42px;
  border-radius: 14px;
  display: grid;
  place-items: center;
  color: #fff;
  background: linear-gradient(135deg, var(--brand), var(--brand-2));
  box-shadow: 0 14px 24px rgba(62, 123, 250, 0.24);
}

.brand-copy {
  display: flex;
  flex-direction: column;
  gap: 2px;
}

.brand-copy strong {
  font-size: 16px;
  line-height: 1.3;
}

.brand-copy span,
.user-chip span {
  font-size: 12px;
  color: var(--text-soft);
}

.main-nav {
  display: flex;
  gap: 10px;
}

.nav-link {
  border: none;
  background: transparent;
  color: var(--text-muted);
  padding: 10px 16px;
  border-radius: 14px;
  cursor: pointer;
  font-size: 13px;
}

.nav-link.active {
  background: linear-gradient(135deg, var(--brand), #5397ff);
  color: #fff;
  box-shadow: 0 12px 24px rgba(62, 123, 250, 0.22);
}

.nav-actions {
  margin-left: auto;
  display: flex;
  align-items: center;
  gap: 12px;
  flex-wrap: wrap;
  justify-content: flex-end;
}

.pill-link {
  border: 1px solid var(--line-soft);
  background: rgba(255, 255, 255, 0.8);
  color: var(--text-muted);
  border-radius: 999px;
  height: 36px;
  padding: 0 13px;
  display: inline-flex;
  align-items: center;
  gap: 8px;
  cursor: pointer;
  font-size: 13px;
}

.status-badge {
  border: 1px solid rgba(59, 193, 121, 0.18);
  background: rgba(235, 255, 245, 0.82);
  color: #17945c;
  border-radius: 999px;
  height: 36px;
  padding: 0 13px;
  display: inline-flex;
  align-items: center;
  gap: 8px;
}

.status-badge.warning {
  border-color: rgba(240, 173, 78, 0.24);
  background: rgba(255, 248, 233, 0.92);
  color: #b97708;
}

.status-badge .dot {
  width: 8px;
  height: 8px;
  border-radius: 50%;
  background: currentColor;
}

.user-chip {
  display: flex;
  align-items: center;
  gap: 10px;
  cursor: pointer;
}

.user-chip strong {
  display: block;
  font-size: 14px;
}

.category-strip {
  background: rgba(255, 255, 255, 0.8);
  border-bottom: 1px solid rgba(229, 236, 244, 0.8);
}

.category-row {
  display: flex;
  gap: 10px;
  padding-top: 12px;
  padding-bottom: 12px;
  overflow-x: auto;
}

.category-item {
  border: none;
  background: var(--bg-soft);
  color: var(--text-muted);
  border-radius: 999px;
  padding: 9px 14px;
  white-space: nowrap;
  cursor: pointer;
  font-size: 13px;
  display: inline-flex;
  align-items: center;
  gap: 8px;
}

.category-item.active {
  background: var(--category-color, #121d30);
  color: #fff;
}

.category-icon,
.hot-grid-icon,
.interest-icon {
  width: 18px;
  height: 18px;
  border-radius: 50%;
  object-fit: cover;
}

.hero-grid {
  padding-top: 24px;
  padding-bottom: 22px;
  display: grid;
  grid-template-columns: minmax(0, 1.72fr) minmax(300px, 0.82fr);
  gap: 16px;
}

.hero-carousel,
.profile-card,
.case-card {
  border-radius: var(--radius-xl);
  overflow: hidden;
}

.hero-carousel {
  padding: 16px;
}

.carousel-frame {
  position: relative;
  height: 336px;
  border-radius: var(--radius-lg);
  overflow: hidden;
}

.carousel-frame::before {
  content: "";
  position: absolute;
  inset: 0;
  z-index: 1;
  pointer-events: none;
  background:
    linear-gradient(90deg, rgba(5, 12, 24, 0.82) 0%, rgba(9, 18, 34, 0.68) 40%, rgba(9, 18, 34, 0.22) 70%, rgba(9, 18, 34, 0.08) 100%),
    linear-gradient(0deg, rgba(5, 12, 24, 0.42) 0%, rgba(5, 12, 24, 0.08) 52%);
}

.carousel-frame img,
.thumb img,
.card-cover img {
  width: 100%;
  height: 100%;
  object-fit: cover;
  display: block;
}

.carousel-copy {
  position: absolute;
  z-index: 2;
  left: 24px;
  bottom: 24px;
  max-width: 460px;
  color: #fff;
  text-shadow: 0 2px 12px rgba(0, 0, 0, 0.32);
}

.hero-kicker {
  display: inline-block;
  padding: 7px 12px;
  border-radius: 999px;
  background: rgba(13, 25, 48, 0.42);
  font-size: 12px;
}

.carousel-copy h1 {
  margin: 12px 0 10px;
  font-size: var(--headline-xl);
  line-height: 1.2;
}

.carousel-copy p {
  margin: 0;
  color: rgba(255, 255, 255, 0.88);
  line-height: 1.68;
  font-size: 13px;
}

.hero-actions {
  margin-top: 16px;
  display: flex;
  gap: 10px;
  flex-wrap: wrap;
}

.hero-button {
  border: 1px solid rgba(255, 255, 255, 0.34);
  background: rgba(255, 255, 255, 0.12);
  color: #fff;
  border-radius: 999px;
  padding: 9px 16px;
  cursor: pointer;
  font-size: 13px;
}

.hero-button.primary {
  background: linear-gradient(135deg, var(--brand), #5a9dff);
  border-color: transparent;
}

.carousel-track {
  margin-top: 12px;
  display: grid;
  grid-template-columns: repeat(4, 1fr);
  gap: 10px;
}

.thumb {
  height: 78px;
  border: 2px solid transparent;
  border-radius: 16px;
  overflow: hidden;
  padding: 0;
  background: transparent;
  cursor: pointer;
}

.thumb.active {
  border-color: var(--brand);
}

.profile-card {
  padding: 22px;
  display: flex;
  flex-direction: column;
  gap: 20px;
}

.profile-head {
  display: flex;
  align-items: center;
  gap: 14px;
}

.profile-head strong {
  display: block;
  font-size: 18px;
}

.profile-head span {
  color: var(--text-soft);
  font-size: 13px;
}

.profile-stats {
  display: grid;
  grid-template-columns: repeat(3, 1fr);
  gap: 12px;
  text-align: center;
}

.profile-stats.single {
  grid-template-columns: 1fr;
}

.stat-entry {
  border: none;
  background: linear-gradient(180deg, #f8fbff, #f1f5fb);
  border-radius: 18px;
  padding: 16px 10px;
  cursor: pointer;
}

.profile-stats strong {
  display: block;
  font-size: 24px;
  margin-bottom: 6px;
}

.profile-stats span {
  color: var(--text-soft);
  font-size: 12px;
}

.profile-section {
  display: grid;
  gap: 14px;
}

.section-title,
.section-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 16px;
}

.section-title button,
.more-link,
.card-footer button {
  border: none;
  background: transparent;
  color: var(--brand);
  cursor: pointer;
}

.hot-grid {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 10px;
}

.hot-grid button {
  border: 1px solid var(--line-soft);
  background: #f8fbff;
  padding: 11px;
  border-radius: 14px;
  text-align: left;
  color: var(--text-main);
  cursor: pointer;
  font-size: 13px;
  display: inline-flex;
  align-items: center;
  gap: 8px;
}

.recommendation-section,
.cases-section {
  padding-bottom: 24px;
}

.section-header h2 {
  margin: 0 0 6px;
  font-size: var(--headline-lg);
}

.section-header p {
  margin: 0;
  color: var(--text-muted);
  font-size: 13px;
  line-height: 1.7;
}

.recommendation-meta {
  display: grid;
  gap: 12px;
  margin: 16px 0;
}

.recommendation-group {
  display: grid;
  gap: 8px;
}

.tag-row {
  display: flex;
  gap: 10px;
  flex-wrap: wrap;
}

.interest-pill {
  padding: 7px 12px;
  border-radius: 999px;
  background: color-mix(in srgb, var(--category-color, #2563eb) 14%, #ffffff);
  color: var(--category-color, #2563eb);
  display: inline-flex;
  align-items: center;
  gap: 8px;
}

.interest-keyword {
  padding: 7px 12px;
  border-radius: 999px;
  background: #edf4ff;
  color: #2563eb;
  font-size: 12px;
}

.case-grid {
  display: grid;
  grid-template-columns: repeat(3, minmax(0, 1fr));
  gap: 16px;
}

.case-card {
  cursor: pointer;
}

.card-cover {
  position: relative;
  height: 196px;
}

.card-overlay {
  position: absolute;
  right: 12px;
  top: 12px;
  display: flex;
  flex-direction: column;
  gap: 8px;
}

.metric {
  min-width: 48px;
  border-radius: 999px;
  padding: 7px 10px;
  background: rgba(59, 115, 255, 0.88);
  color: #fff;
  display: inline-flex;
  align-items: center;
  justify-content: center;
  gap: 4px;
  font-size: 12px;
}

.recommend-overlay {
  position: absolute;
  left: 12px;
  right: 12px;
  bottom: 12px;
  padding: 10px 12px;
  border-radius: 14px;
  background: rgba(15, 23, 42, 0.72);
  color: #fff;
  font-size: 12px;
  line-height: 1.5;
}

.card-body {
  padding: 16px;
}

.category-pill {
  display: inline-block;
  margin-bottom: 10px;
  padding: 6px 10px;
  border-radius: 999px;
  background: var(--brand-soft);
  color: var(--brand);
  font-size: 12px;
}

.card-body h3 {
  margin: 0 0 10px;
  font-size: 17px;
  line-height: 1.45;
}

.card-body p {
  margin: 0 0 16px;
  color: var(--text-muted);
  line-height: 1.72;
  min-height: 54px;
  font-size: 13px;
}

.card-footer {
  display: flex;
  align-items: center;
  justify-content: space-between;
  color: var(--text-soft);
  font-size: 12px;
}

.card-footer-actions {
  display: flex;
  align-items: center;
  gap: 10px;
}

.favorite-chip {
  border: 1px solid #d9e5f6;
  background: #f8fbff;
  color: #516175;
  border-radius: 999px;
  padding: 6px 12px;
  cursor: pointer;
  font-size: 12px;
}

.favorite-chip.active {
  border-color: #fed7aa;
  background: #fff7ed;
  color: #c2410c;
}

@media (max-width: 1200px) {
  .nav-inner {
    grid-template-columns: 1fr;
    height: auto;
    padding-top: 14px;
    padding-bottom: 14px;
  }

  .main-nav,
  .nav-actions {
    margin-left: 0;
  }

  .hero-grid,
  .case-grid {
    grid-template-columns: 1fr;
  }
}
</style>
