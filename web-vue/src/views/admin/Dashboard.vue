<template>
  <AdminShell active-path="/admin" breadcrumb="首页 / 管理控制台">
    <section class="stats-grid">
      <article v-for="stat in stats" :key="stat.label" class="stat-card panel-card" @click="navigateStat(stat.path)">
        <div class="stat-value" :class="stat.tone">{{ stat.value }}</div>
        <div class="stat-label">{{ stat.label }}</div>
      </article>
    </section>

    <section class="charts-grid">
      <article class="chart-card panel-card">
        <div class="card-head">
          <div class="card-title">
            <el-icon><Clock /></el-icon>
            <span>专题案例分布</span>
          </div>
          <el-button text @click="router.push('/admin/items')">案例管理</el-button>
        </div>
        <div ref="domainChartRef" class="chart-box donut"></div>
      </article>

      <article class="chart-card panel-card">
        <div class="card-head">
          <div class="card-title">
            <el-icon><DataAnalysis /></el-icon>
            <span>系统行为概览</span>
          </div>
          <el-button text @click="router.push('/admin/user-actions')">行为明细</el-button>
        </div>
        <div ref="behaviorChartRef" class="chart-box donut"></div>
      </article>
    </section>

    <section class="charts-grid">
      <article class="trend-card panel-card">
        <div class="card-head">
          <div class="card-title">
            <el-icon><TrendCharts /></el-icon>
            <span>系统趋势分析</span>
          </div>
          <span class="trend-note">最近 7 天真实入库与咨询趋势</span>
        </div>
        <div ref="trendChartRef" class="chart-box trend"></div>
      </article>

      <article class="trend-card panel-card">
        <div class="card-head">
          <div class="card-title">
            <el-icon><Histogram /></el-icon>
            <span>咨询路径漏斗</span>
          </div>
          <span class="trend-note">登录到收藏的关键阶段转化</span>
        </div>
        <div ref="funnelChartRef" class="chart-box trend"></div>
      </article>
    </section>

    <section class="charts-grid">
      <article class="chart-card panel-card">
        <div class="card-head">
          <div class="card-title">
            <el-icon><Search /></el-icon>
            <span>搜索词 Top 8</span>
          </div>
        </div>
        <div ref="keywordChartRef" class="chart-box trend short"></div>
      </article>

      <article class="chart-card panel-card">
        <div class="card-head">
          <div class="card-title">
            <el-icon><PieChart /></el-icon>
            <span>分类偏好分布</span>
          </div>
        </div>
        <div ref="categoryPrefChartRef" class="chart-box trend short"></div>
      </article>
    </section>

    <section class="charts-grid">
      <article class="insight-card panel-card">
        <div class="card-head">
          <div class="card-title">
            <el-icon><Document /></el-icon>
            <span>热门咨询问题</span>
          </div>
        </div>
        <div class="question-list">
          <article v-for="(item, index) in dashboardInsights.hotQuestions" :key="`${item.question}-${index}`" class="question-row">
            <strong>#{{ index + 1 }}</strong>
            <span>{{ item.question }}</span>
            <small>{{ item.count }} 次</small>
          </article>
          <el-empty v-if="!dashboardInsights.hotQuestions.length" description="暂无热门问题数据" />
        </div>
      </article>

      <article class="index-card panel-card">
        <div class="card-head">
          <div class="card-title">
            <el-icon><Search /></el-icon>
            <span>索引与召回管理</span>
          </div>
          <el-button text @click="refreshDashboard">刷新数据</el-button>
        </div>
        <div class="index-overview">
          <article class="index-pill" :class="{ status: !indexStats.totalIndexes }">
            <span>索引状态</span>
            <strong>{{ indexStats.status }}</strong>
          </article>
          <article class="index-pill">
            <span>索引个数</span>
            <strong>{{ indexStats.totalIndexes }}</strong>
          </article>
          <article class="index-pill">
            <span>语料条目数</span>
            <strong>{{ indexStats.totalSources }}</strong>
          </article>
          <article class="index-pill">
            <span>向量总数</span>
            <strong>{{ indexStats.totalVectors }}</strong>
          </article>
          <article class="index-pill">
            <span>向量维度</span>
            <strong>{{ indexStats.dimension }}</strong>
          </article>
        </div>
        <div class="index-actions">
          <el-button type="primary" @click="router.push('/admin/indexes')">索引与召回管理</el-button>
          <el-button @click="router.push('/admin/items')">案例管理</el-button>
        </div>
      </article>
    </section>
  </AdminShell>
</template>

<script setup lang="ts">
import { computed, nextTick, onBeforeUnmount, onMounted, reactive, ref } from 'vue'
import { useRouter } from 'vue-router'
import axios from 'axios'
import { Clock, DataAnalysis, Document, Histogram, PieChart, Search, TrendCharts } from '@element-plus/icons-vue'
import type { ECharts, init, use } from 'echarts/core'
import { categoryApi } from '@/api/category'
import { chatApi, type ChatStats } from '@/api/chat'
import { itemApi } from '@/api/item'
import { userActionApi, type UserActionDashboardInsights } from '@/api/userAction'
import { userApi } from '@/api/user'
import AdminShell from '@/components/admin/AdminShell.vue'
import { ENV } from '@/config/constants'
import type { Category, Item, User } from '@/types'

type EChartsCoreModule = {
  init: typeof init
  use: typeof use
}

type EChartsInstance = ECharts

type VectorIndexSummary = {
  sourceCount?: number
  count?: number
  dimension?: number
}

const router = useRouter()

const users = ref<User[]>([])
const categories = ref<Category[]>([])
const items = ref<Item[]>([])
const chatStats = ref<ChatStats>({
  sessionCount: 0,
  messageCount: 0,
  dates: [],
  sessionTrend: [],
  messageTrend: []
})
const dashboardInsights = reactive<UserActionDashboardInsights>({
  hotQuestions: [],
  searchKeywords: [],
  categoryPreference: [],
  journeyFunnel: []
})
const indexStats = reactive({
  status: '未连接',
  totalIndexes: 0,
  totalSources: 0,
  totalVectors: 0,
  dimension: 0
})

const domainChartRef = ref<HTMLElement>()
const behaviorChartRef = ref<HTMLElement>()
const trendChartRef = ref<HTMLElement>()
const funnelChartRef = ref<HTMLElement>()
const keywordChartRef = ref<HTMLElement>()
const categoryPrefChartRef = ref<HTMLElement>()
const chartInstances: EChartsInstance[] = []
let echartsModule: EChartsCoreModule | null = null

const stats = computed(() => [
  { label: '总用户数', value: String(users.value.length), tone: 'blue', path: '/admin/users' },
  { label: '总案例数', value: String(items.value.length), tone: 'dark', path: '/admin/items' },
  { label: '总专题分类数', value: String(categories.value.length), tone: 'dark', path: '/admin/categories' },
  { label: '运行状态', value: indexStats.totalIndexes ? '正常' : '待建索引', tone: indexStats.totalIndexes ? 'green' : 'orange', path: '/admin/indexes' }
])

const domainData = computed(() => {
  const counts = new Map<number, number>()
  items.value.forEach((item) => {
    if (!item.categoryId) return
    counts.set(item.categoryId, (counts.get(item.categoryId) || 0) + 1)
  })

  return Array.from(counts.entries())
    .map(([categoryId, value]) => ({
      name: categories.value.find((category) => category.id === categoryId)?.name || `分类 ${categoryId}`,
      value
    }))
    .sort((a, b) => b.value - a.value)
    .slice(0, 10)
})

const behaviorData = computed(() => {
  const totalViews = items.value.reduce((sum, item) => sum + Number(item.viewCount || 0), 0)
  const totalFavorites = items.value.reduce((sum, item) => sum + Number(item.favoriteCount || 0), 0)
  return [
    { name: '案例浏览', value: totalViews || 0 },
    { name: '收藏操作', value: totalFavorites || 0 },
    { name: '咨询会话', value: chatStats.value.sessionCount || 0 },
    { name: '咨询消息', value: chatStats.value.messageCount || 0 }
  ].filter((item) => item.value > 0)
})

const trendSeries = computed(() => {
  const itemTrendMap = initRecentTrendMap()
  const userTrendMap = initRecentTrendMap()

  items.value.forEach((item) => {
    const key = normalizeDateKey(item.createTime)
    if (key && key in itemTrendMap) itemTrendMap[key] += 1
  })

  users.value.forEach((user) => {
    const key = normalizeDateKey(user.createTime)
    if (key && key in userTrendMap) userTrendMap[key] += 1
  })

  const dates = Object.keys(itemTrendMap)
  return {
    dates,
    users: dates.map((date) => userTrendMap[date] || 0),
    cases: dates.map((date) => itemTrendMap[date] || 0),
    chats: dates.map((_, index) => chatStats.value.sessionTrend[index] || 0)
  }
})

const ensureECharts = async () => {
  if (!echartsModule) {
    const [core, charts, components, renderers] = await Promise.all([
      import('echarts/core'),
      import('echarts/charts'),
      import('echarts/components'),
      import('echarts/renderers')
    ])

    core.use([
      charts.LineChart,
      charts.PieChart,
      charts.BarChart,
      charts.FunnelChart,
      components.GraphicComponent,
      components.GridComponent,
      components.LegendComponent,
      components.TooltipComponent,
      renderers.CanvasRenderer
    ])

    echartsModule = core
  }
  return echartsModule
}

const refreshDashboard = async () => {
  await Promise.allSettled([
    loadUsers(),
    loadCategories(),
    loadItems(),
    loadChatStats(),
    loadIndexStats(),
    loadInsights()
  ])
  await nextTick()
  await renderCharts()
}

const loadUsers = async () => {
  const res = await userApi.getUserList()
  users.value = Array.isArray(res.data) ? res.data : []
}

const loadCategories = async () => {
  const res = await categoryApi.getCategoryList()
  categories.value = Array.isArray(res.data) ? res.data : []
}

const loadItems = async () => {
  const pageSize = 200
  let pageNum = 1
  let pages = 1
  const records: Item[] = []

  do {
    const res = await itemApi.getItemPage({ pageNum, pageSize })
    records.push(...(res.data?.records || []))
    pages = Number(res.data?.pages || 1)
    pageNum += 1
  } while (pageNum <= pages)

  items.value = records
}

const loadChatStats = async () => {
  const res = await chatApi.getStats()
  chatStats.value = res.data || chatStats.value
}

const loadInsights = async () => {
  const res = await userActionApi.getDashboardInsights()
  Object.assign(dashboardInsights, res.data || {})
}

const loadIndexStats = async () => {
  const res = await axios.get(`${ENV.ALGORITHM_API_BASE_URL}/api/vector/indexes`, { timeout: 10000 })
  const indexes: VectorIndexSummary[] = Array.isArray(res.data?.data) ? res.data.data : []
  indexStats.totalIndexes = indexes.length
  indexStats.totalSources = indexes.reduce((sum, item) => sum + Number(item.sourceCount || 0), 0)
  indexStats.totalVectors = indexes.reduce((sum, item) => sum + Number(item.count || 0), 0)
  indexStats.dimension = indexes[0]?.dimension || 0
  indexStats.status = indexes.length ? '已接入' : '未创建'
}

const renderCharts = async () => {
  const echarts = await ensureECharts()

  chartInstances.forEach((chart) => chart.dispose())
  chartInstances.length = 0

  if (domainChartRef.value) {
    const chart = echarts.init(domainChartRef.value)
    chart.setOption({
      color: ['#35c78a', '#4e7ff1', '#efb348', '#7a6ff0', '#f06a91', '#4cb7d3'],
      tooltip: { trigger: 'item', formatter: '{b}: {c} ({d}%)' },
      legend: { orient: 'vertical', left: 12, top: 'middle', itemWidth: 10, itemHeight: 10, textStyle: { color: '#5f6673', fontSize: 12 } },
      series: [{
        type: 'pie',
        radius: ['42%', '68%'],
        center: ['70%', '50%'],
        itemStyle: { borderColor: '#fff', borderWidth: 4, borderRadius: 10 },
        label: { formatter: '{b}\n{d}%', fontSize: 11, color: '#5c6472' },
        data: domainData.value.length ? domainData.value : [{ name: '暂无数据', value: 1 }]
      }]
    })
    chartInstances.push(chart)
  }

  if (behaviorChartRef.value) {
    const total = behaviorData.value.reduce((sum, item) => sum + item.value, 0)
    const chart = echarts.init(behaviorChartRef.value)
    chart.setOption({
      color: ['#5e8df7', '#38d39f', '#f3b346', '#f06a91'],
      tooltip: { trigger: 'item', formatter: '{b}: {c}' },
      legend: { bottom: 0, itemWidth: 10, itemHeight: 10, textStyle: { color: '#5f6673', fontSize: 12 } },
      graphic: [
        { type: 'text', left: 'center', top: '42%', style: { text: '总计', fill: '#a0a8b8', fontSize: 12, fontWeight: 500 } },
        { type: 'text', left: 'center', top: '48%', style: { text: String(total), fill: '#2f3440', fontSize: 22, fontWeight: 700 } },
        { type: 'text', left: 'center', top: '58%', style: { text: '次操作', fill: '#a0a8b8', fontSize: 12, fontWeight: 500 } }
      ],
      series: [{
        type: 'pie',
        radius: ['50%', '70%'],
        center: ['50%', '46%'],
        label: { show: false },
        itemStyle: { borderColor: '#fff', borderWidth: 4, borderRadius: 10 },
        data: behaviorData.value.length ? behaviorData.value : [{ name: '暂无数据', value: 1 }]
      }]
    })
    chartInstances.push(chart)
  }

  if (trendChartRef.value) {
    const chart = echarts.init(trendChartRef.value)
    chart.setOption({
      color: ['#5f8bf6', '#45d0a2', '#f0be54'],
      tooltip: { trigger: 'axis' },
      legend: { top: 8, data: ['用户数', '案例数', '咨询会话'], itemWidth: 10, itemHeight: 10 },
      grid: { left: 18, right: 18, bottom: 16, top: 54, containLabel: true },
      xAxis: { type: 'category', boundaryGap: false, data: trendSeries.value.dates, axisLabel: { color: '#7c8493' } },
      yAxis: { type: 'value', minInterval: 1, splitLine: { lineStyle: { color: '#edf1f7' } }, axisLabel: { color: '#7c8493' } },
      series: [
        { name: '用户数', type: 'line', smooth: true, symbolSize: 7, data: trendSeries.value.users },
        { name: '案例数', type: 'line', smooth: true, symbolSize: 7, areaStyle: { color: 'rgba(69, 208, 162, 0.18)' }, data: trendSeries.value.cases },
        { name: '咨询会话', type: 'line', smooth: true, symbolSize: 7, areaStyle: { color: 'rgba(240, 190, 84, 0.12)' }, data: trendSeries.value.chats }
      ]
    })
    chartInstances.push(chart)
  }

  if (funnelChartRef.value) {
    const chart = echarts.init(funnelChartRef.value)
    chart.setOption({
      tooltip: { trigger: 'item', formatter: '{b}: {c}' },
      series: [{
        type: 'funnel',
        top: 10,
        bottom: 10,
        left: '10%',
        width: '80%',
        minSize: '20%',
        maxSize: '100%',
        sort: 'descending',
        gap: 8,
        label: { show: true, position: 'inside' },
        itemStyle: { borderColor: '#fff', borderWidth: 2 },
        data: dashboardInsights.journeyFunnel.map((item) => ({ name: item.stage, value: item.count }))
      }]
    })
    chartInstances.push(chart)
  }

  if (keywordChartRef.value) {
    const chart = echarts.init(keywordChartRef.value)
    chart.setOption({
      color: ['#4e7ff1'],
      tooltip: { trigger: 'axis' },
      grid: { left: 18, right: 18, bottom: 16, top: 16, containLabel: true },
      xAxis: { type: 'value', axisLabel: { color: '#7c8493' } },
      yAxis: {
        type: 'category',
        axisLabel: { color: '#7c8493' },
        data: dashboardInsights.searchKeywords.map((item) => item.keyword)
      },
      series: [{
        type: 'bar',
        barWidth: 14,
        data: dashboardInsights.searchKeywords.map((item) => item.count),
        itemStyle: { borderRadius: [0, 8, 8, 0] }
      }]
    })
    chartInstances.push(chart)
  }

  if (categoryPrefChartRef.value) {
    const chart = echarts.init(categoryPrefChartRef.value)
    chart.setOption({
      color: ['#38d39f'],
      tooltip: { trigger: 'axis' },
      grid: { left: 18, right: 18, bottom: 16, top: 16, containLabel: true },
      xAxis: {
        type: 'category',
        axisLabel: { color: '#7c8493', rotate: 20 },
        data: dashboardInsights.categoryPreference.map((item) => item.categoryName)
      },
      yAxis: { type: 'value', minInterval: 1, axisLabel: { color: '#7c8493' } },
      series: [{
        type: 'bar',
        barWidth: 22,
        data: dashboardInsights.categoryPreference.map((item) => ({
          value: item.count,
          itemStyle: { color: item.themeColor || '#38d39f', borderRadius: [8, 8, 0, 0] }
        }))
      }]
    })
    chartInstances.push(chart)
  }
}

const initRecentTrendMap = () => {
  const map: Record<string, number> = {}
  const today = new Date()
  for (let i = 6; i >= 0; i -= 1) {
    const date = new Date(today)
    date.setDate(today.getDate() - i)
    map[`${date.getMonth() + 1}月${date.getDate()}日`] = 0
  }
  return map
}

const normalizeDateKey = (value?: string) => {
  if (!value) return ''
  const date = new Date(value)
  if (Number.isNaN(date.getTime())) return ''
  return `${date.getMonth() + 1}月${date.getDate()}日`
}

const navigateStat = (path: string) => {
  router.push(path)
}

const handleResize = () => {
  chartInstances.forEach((chart) => chart.resize())
}

onMounted(async () => {
  await refreshDashboard()
  window.addEventListener('resize', handleResize)
})

onBeforeUnmount(() => {
  window.removeEventListener('resize', handleResize)
  chartInstances.forEach((chart) => chart.dispose())
  chartInstances.length = 0
})
</script>

<style scoped>
.stats-grid {
  display: grid;
  grid-template-columns: repeat(4, minmax(0, 1fr));
  gap: 16px;
}

.stat-card {
  padding: 22px 14px;
  text-align: center;
  border-radius: 18px;
  cursor: pointer;
}

.stat-value {
  font-size: 38px;
  font-weight: 800;
  line-height: 1;
  color: #242a35;
}

.stat-value.blue {
  color: #1d67f4;
}

.stat-value.green {
  color: #1ebc72;
}

.stat-value.orange {
  color: #d28a14;
}

.stat-label {
  margin-top: 8px;
  font-size: 14px;
  color: #8a93a3;
}

.charts-grid {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 16px;
}

.panel-card {
  border-radius: 20px;
}

.chart-card,
.trend-card,
.index-card,
.insight-card {
  padding: 16px 16px 12px;
}

.card-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
}

.card-title {
  display: flex;
  align-items: center;
  gap: 8px;
  font-size: 16px;
  font-weight: 700;
  color: #2c3440;
}

.card-title :deep(.el-icon) {
  color: #4e7ff1;
}

.chart-box {
  width: 100%;
}

.chart-box.donut {
  height: 320px;
}

.chart-box.trend {
  height: 360px;
}

.chart-box.short {
  height: 300px;
}

.trend-note {
  color: #8a93a3;
  font-size: 12px;
}

.question-list {
  margin-top: 14px;
  display: grid;
  gap: 12px;
}

.question-row {
  display: grid;
  grid-template-columns: 52px 1fr auto;
  gap: 12px;
  padding: 12px 14px;
  border-radius: 16px;
  background: #f8fbff;
  align-items: center;
}

.question-row strong {
  color: #4e7ff1;
}

.question-row span {
  line-height: 1.6;
}

.question-row small {
  color: #8a93a3;
}

.index-overview {
  margin-top: 16px;
  display: grid;
  grid-template-columns: repeat(5, minmax(0, 1fr));
  gap: 12px;
}

.index-pill {
  padding: 16px 14px;
  border-radius: 16px;
  background: #f8fbff;
  display: flex;
  flex-direction: column;
  gap: 8px;
}

.index-pill span {
  color: #8a93a3;
  font-size: 12px;
}

.index-pill strong {
  color: #242a35;
  font-size: 22px;
  line-height: 1.1;
}

.index-pill.status strong {
  color: #d28a14;
}

.index-actions {
  margin-top: 16px;
  display: flex;
  gap: 12px;
}

@media (max-width: 1320px) {
  .stats-grid,
  .index-overview {
    grid-template-columns: repeat(2, minmax(0, 1fr));
  }
}

@media (max-width: 1080px) {
  .charts-grid,
  .stats-grid,
  .index-overview {
    grid-template-columns: 1fr;
  }
}
</style>
