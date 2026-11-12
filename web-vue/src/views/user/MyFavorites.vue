<template>
  <div class="page-shell favorites-page">
    <header class="top-nav glass-panel">
      <div class="page-width nav-inner">
        <div class="brand" @click="router.push('/home')">
          <div class="brand-mark">案</div>
          <div class="brand-copy">
            <strong>涉毒刑事普法系统</strong>
            <span>我的收藏</span>
          </div>
        </div>

        <nav class="main-nav">
          <button class="nav-link" @click="router.push('/home')">首页</button>
          <button class="nav-link" @click="router.push('/items')">专题案例</button>
          <button class="nav-link" @click="router.push('/chat')">AI律师咨询</button>
          <button class="nav-link active">我的收藏</button>
        </nav>
      </div>
    </header>

    <section class="page-width hero panel-card">
      <div>
        <p class="eyebrow">My Favorites</p>
        <h1>我的收藏</h1>
        <p class="hero-text">这里直接读取当前用户的真实收藏记录，并和首页、案例页、详情页的收藏状态保持一致。</p>
      </div>
      <el-button round @click="loadFavorites">刷新收藏</el-button>
    </section>

    <section class="page-width panel-card table-panel">
      <el-table :data="favorites" v-loading="loading" empty-text="暂无收藏案例">
        <el-table-column prop="title" label="案例标题" min-width="260" show-overflow-tooltip />
        <el-table-column prop="categoryName" label="法律领域" width="160" />
        <el-table-column prop="summary" label="摘要" min-width="320" show-overflow-tooltip />
        <el-table-column prop="createTime" label="收藏时间" width="190" />
        <el-table-column label="操作" width="180" fixed="right">
          <template #default="{ row }">
            <el-button type="primary" size="small" @click="viewItem(row.itemId || row.id)">查看</el-button>
            <el-button type="danger" size="small" @click="unfavorite(row.itemId || row.id)">取消收藏</el-button>
          </template>
        </el-table-column>
      </el-table>
    </section>
  </div>
</template>

<script setup lang="ts">
import { onMounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { favoriteApi } from '@/api/favorite'
import { useItemStore } from '@/stores/item'
import { useUserStore } from '@/stores/user'
import type { FavoriteItem } from '@/types'

const router = useRouter()
const userStore = useUserStore()
const itemStore = useItemStore()
const loading = ref(false)
const favorites = ref<FavoriteItem[]>([])

onMounted(() => {
  loadFavorites()
})

const loadFavorites = async () => {
  if (!userStore.user?.id) {
    favorites.value = []
    return
  }
  loading.value = true
  try {
    const res = await favoriteApi.getList(userStore.user.id)
    favorites.value = Array.isArray(res.data) ? res.data : []
    await itemStore.loadFavorites(userStore.user.id, true)
  } catch (error) {
    console.error(error)
  } finally {
    loading.value = false
  }
}

const viewItem = (id: number) => {
  router.push(`/item/${id}`)
}

const unfavorite = async (itemId: number) => {
  if (!userStore.user?.id) return
  try {
    await favoriteApi.remove(userStore.user.id, itemId)
    ElMessage.success('已取消收藏')
    await itemStore.loadFavorites(userStore.user.id, true)
    await loadFavorites()
  } catch (error) {
    console.error(error)
  }
}
</script>

<style scoped>
.favorites-page { background: linear-gradient(180deg, #f4f8fe, #edf3fb); }
.top-nav { position: sticky; top: 0; z-index: 20; border-bottom: 1px solid rgba(229,236,244,.8); }
.nav-inner { min-height: var(--nav-height); display: grid; grid-template-columns: auto 1fr; align-items: center; gap: 20px; }
.brand { display: flex; align-items: center; gap: 12px; cursor: pointer; }
.brand-mark { width: 40px; height: 40px; border-radius: 14px; display: grid; place-items: center; background: linear-gradient(135deg, var(--brand), var(--brand-2)); color: #fff; font-weight: 800; }
.brand-copy { display: flex; flex-direction: column; gap: 2px; }
.brand-copy strong { font-size: 16px; line-height: 1.3; }
.brand-copy span { font-size: 12px; color: var(--text-soft); }
.main-nav { display: flex; gap: 10px; justify-content: flex-end; flex-wrap: wrap; }
.nav-link { border: none; background: transparent; color: var(--text-muted); padding: 10px 16px; border-radius: 14px; cursor: pointer; font-size: 13px; }
.nav-link.active { background: linear-gradient(135deg,var(--brand),#5397ff); color: #fff; }
.hero, .table-panel { border-radius: var(--radius-xl); }
.hero { margin-top: 24px; margin-bottom: 16px; padding: 24px 26px; display: flex; align-items: center; justify-content: space-between; gap: 18px; }
.eyebrow { margin: 0 0 8px; color: var(--brand); text-transform: uppercase; letter-spacing: .18em; font-size: 12px; }
.hero h1 { margin: 0; font-size: var(--headline-lg); }
.hero-text { margin: 12px 0 0; color: var(--text-soft); line-height: 1.72; font-size: 13px; }
.table-panel { padding: 16px; margin-bottom: 30px; }
@media (max-width: 1080px) { .nav-inner { grid-template-columns: 1fr; padding-top: 12px; padding-bottom: 12px; } .main-nav { justify-content: flex-start; } .hero { flex-direction: column; align-items: flex-start; } }
</style>
