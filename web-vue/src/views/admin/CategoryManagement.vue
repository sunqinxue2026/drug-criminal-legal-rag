<template>
  <AdminShell active-path="/admin/categories" breadcrumb="首页 / 专题分类管理">
    <section class="hero panel-card">
      <div>
        <p class="eyebrow">Topic Taxonomy</p>
        <h1>专题分类管理</h1>
        <p class="hero-text">现在分类除了名称和说明，还支持配置图标和主题色，方便在首页、推荐区和案例列表中统一展示。</p>
      </div>
      <div class="hero-actions">
        <el-button round @click="loadCategories">刷新分类</el-button>
        <el-button type="primary" round :icon="Plus" @click="openCreateDialog">新增专题</el-button>
      </div>
    </section>

    <section class="stats-grid">
      <article class="stat-card panel-card">
        <span>专题数量</span>
        <strong>{{ filteredCategories.length }}</strong>
        <small>当前有效分类</small>
      </article>
      <article class="stat-card panel-card">
        <span>图标覆盖率</span>
        <strong>{{ iconCoverage }}</strong>
        <small>已配置图标的专题占比</small>
      </article>
      <article class="stat-card panel-card">
        <span>主题色覆盖率</span>
        <strong>{{ colorCoverage }}</strong>
        <small>已配置主题色的专题占比</small>
      </article>
    </section>

    <section class="toolbar panel-card">
      <div class="filter-grid">
        <el-input v-model="filters.name" placeholder="搜索专题名称" clearable />
        <el-input v-model="filters.keyword" placeholder="搜索说明关键词" clearable />
        <el-button type="primary" @click="applyFilters">查询</el-button>
        <el-button @click="resetFilters">重置</el-button>
      </div>
    </section>

    <section class="panel-card table-panel">
      <el-table :data="pagedCategories" v-loading="loading">
        <el-table-column type="index" label="#" width="64" align="center" />
        <el-table-column label="分类展示" min-width="230">
          <template #default="{ row }">
            <div class="category-cell">
              <div class="category-avatar" :style="getCategoryStyle(row)">
                <img v-if="row.iconUrl" :src="row.iconUrl" alt="" />
                <span v-else>{{ row.name.slice(0, 1) }}</span>
              </div>
              <div>
                <strong>{{ row.name }}</strong>
                <p>{{ row.description || '暂无说明' }}</p>
              </div>
            </div>
          </template>
        </el-table-column>
        <el-table-column label="图标 URL" min-width="220" show-overflow-tooltip>
          <template #default="{ row }">{{ row.iconUrl || '--' }}</template>
        </el-table-column>
        <el-table-column label="主题色" width="140">
          <template #default="{ row }">
            <div class="color-chip">
              <span class="color-dot" :style="{ background: row.themeColor || '#2563eb' }"></span>
              {{ row.themeColor || '#2563eb' }}
            </div>
          </template>
        </el-table-column>
        <el-table-column label="操作" width="280" fixed="right">
          <template #default="{ row }">
            <div class="row-actions">
              <el-button size="small" type="info" plain @click="viewCases(row)">查看案例</el-button>
              <el-button size="small" type="primary" plain :icon="Edit" @click="openEditDialog(row)">编辑</el-button>
              <el-button size="small" type="danger" plain :icon="Delete" @click="removeCategory(row)">删除</el-button>
            </div>
          </template>
        </el-table-column>
      </el-table>

      <div class="pagination-wrap">
        <el-pagination
          v-model:current-page="pagination.page"
          v-model:page-size="pagination.pageSize"
          :page-sizes="[10, 20, 50]"
          :total="filteredCategories.length"
          layout="total, sizes, prev, pager, next"
        />
      </div>
    </section>

    <el-dialog v-model="dialogVisible" :title="dialogMode === 'create' ? '新增专题分类' : '编辑专题分类'" width="620px">
      <el-form label-width="96px">
        <el-form-item label="专题名称">
          <el-input v-model="form.name" />
        </el-form-item>
        <el-form-item label="专题说明">
          <el-input v-model="form.description" type="textarea" :rows="4" />
        </el-form-item>
        <el-form-item label="图标 URL">
          <el-input v-model="form.iconUrl" placeholder="可填写图片地址，用于首页和推荐区展示" />
        </el-form-item>
        <el-form-item label="主题色">
          <div class="theme-row">
            <el-input v-model="form.themeColor" placeholder="如 #2563eb" />
            <input v-model="form.themeColor" type="color" class="color-picker" />
          </div>
        </el-form-item>
        <el-form-item label="展示预览">
          <div class="preview-chip" :style="getCategoryStyle(form)">
            <div class="category-avatar preview-avatar" :style="getCategoryStyle(form)">
              <img v-if="form.iconUrl" :src="form.iconUrl" alt="" />
              <span v-else>{{ form.name?.slice(0, 1) || '分' }}</span>
            </div>
            <div>
              <strong>{{ form.name || '专题分类' }}</strong>
              <p>{{ form.description || '这里会显示分类说明' }}</p>
            </div>
          </div>
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="dialogVisible = false">取消</el-button>
        <el-button type="primary" :loading="submitting" @click="submitCategory">保存</el-button>
      </template>
    </el-dialog>
  </AdminShell>
</template>

<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import { Delete, Edit, Plus } from '@element-plus/icons-vue'
import { categoryApi } from '@/api/category'
import AdminShell from '@/components/admin/AdminShell.vue'
import type { Category } from '@/types'

const router = useRouter()
const loading = ref(false)
const submitting = ref(false)
const dialogVisible = ref(false)
const dialogMode = ref<'create' | 'edit'>('create')

const categories = ref<Category[]>([])
const filteredCategories = ref<Category[]>([])

const filters = reactive({
  name: '',
  keyword: ''
})

const pagination = reactive({
  page: 1,
  pageSize: 10
})

const form = reactive<Category>({
  id: undefined,
  name: '',
  description: '',
  iconUrl: '',
  themeColor: '#2563eb'
})

const pagedCategories = computed(() => {
  const start = (pagination.page - 1) * pagination.pageSize
  return filteredCategories.value.slice(start, start + pagination.pageSize)
})

const iconCoverage = computed(() => calcCoverage(filteredCategories.value, 'iconUrl'))
const colorCoverage = computed(() => calcCoverage(filteredCategories.value, 'themeColor'))

onMounted(loadCategories)

async function loadCategories() {
  loading.value = true
  try {
    const res = await categoryApi.getCategoryList()
    categories.value = Array.isArray(res.data) ? res.data : []
    filteredCategories.value = [...categories.value]
  } finally {
    loading.value = false
  }
}

function applyFilters() {
  const name = filters.name.trim().toLowerCase()
  const keyword = filters.keyword.trim().toLowerCase()
  filteredCategories.value = categories.value.filter((row) => {
    return (!name || row.name.toLowerCase().includes(name))
      && (!keyword || (row.description || '').toLowerCase().includes(keyword))
  })
  pagination.page = 1
}

function resetFilters() {
  filters.name = ''
  filters.keyword = ''
  filteredCategories.value = [...categories.value]
  pagination.page = 1
}

function openCreateDialog() {
  dialogMode.value = 'create'
  Object.assign(form, {
    id: undefined,
    name: '',
    description: '',
    iconUrl: '',
    themeColor: '#2563eb'
  })
  dialogVisible.value = true
}

function openEditDialog(category: Category) {
  dialogMode.value = 'edit'
  Object.assign(form, {
    ...category,
    iconUrl: category.iconUrl || '',
    themeColor: category.themeColor || '#2563eb'
  })
  dialogVisible.value = true
}

async function submitCategory() {
  if (!form.name?.trim()) {
    ElMessage.warning('请输入专题名称')
    return
  }

  submitting.value = true
  try {
    const payload: Category = {
      ...form,
      name: form.name.trim(),
      description: form.description?.trim() || '',
      iconUrl: form.iconUrl?.trim() || '',
      themeColor: form.themeColor?.trim() || '#2563eb'
    }
    if (dialogMode.value === 'create') {
      await categoryApi.createCategory(payload)
      ElMessage.success('专题分类已新增')
    } else {
      await categoryApi.updateCategory(payload)
      ElMessage.success('专题分类已更新')
    }
    dialogVisible.value = false
    await loadCategories()
    applyFilters()
  } finally {
    submitting.value = false
  }
}

async function removeCategory(category: Category) {
  await ElMessageBox.confirm(`确认删除专题“${category.name}”吗？`, '提示', { type: 'warning' })
  await categoryApi.deleteCategory(category.id!)
  ElMessage.success('专题分类已删除')
  await loadCategories()
  applyFilters()
}

function viewCases(category: Category) {
  router.push(`/admin/items?categoryId=${encodeURIComponent(String(category.id || ''))}`)
}

function calcCoverage(list: Category[], field: 'iconUrl' | 'themeColor') {
  if (!list.length) return '0%'
  const covered = list.filter((item) => item[field]).length
  return `${Math.round((covered / list.length) * 100)}%`
}

function getCategoryStyle(category?: Partial<Category>) {
  return {
    '--category-color': category?.themeColor || '#2563eb'
  }
}
</script>

<style scoped>
.hero,
.toolbar,
.table-panel,
.stat-card {
  border-radius: var(--radius-xl);
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
  text-transform: uppercase;
  letter-spacing: 0.18em;
  font-size: 13px;
  font-weight: 600;
  line-height: 1;
}

.hero h1 {
  margin: 0;
  color: var(--text-main);
  font-size: 36px;
  font-weight: 700;
  line-height: 1.2;
}

.hero-text {
  margin: 18px 0 0;
  max-width: 1080px;
  color: var(--text-muted);
  font-size: 17px;
  line-height: 1.8;
}

.hero-actions,
.row-actions {
  display: flex;
  gap: 12px;
}

.hero-actions {
  align-items: center;
  justify-content: flex-end;
  flex-wrap: wrap;
}

.hero-actions :deep(.el-button) {
  height: 42px;
  margin-left: 0;
  padding: 0 22px;
  font-size: 16px;
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
  display: block;
  color: var(--text-muted);
  font-size: 16px;
  line-height: 1.5;
}

.stat-card strong {
  display: block;
  margin: 0;
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
  grid-template-columns: 1.2fr 1.2fr auto auto;
  gap: 10px;
}

.table-panel {
  padding: 6px 16px 16px;
}

.category-cell {
  display: flex;
  align-items: center;
  gap: 14px;
}

.category-cell strong,
.preview-chip strong {
  display: block;
}

.category-cell p,
.preview-chip p {
  margin: 6px 0 0;
  color: var(--text-soft);
  line-height: 1.6;
}

.category-avatar {
  width: 48px;
  height: 48px;
  border-radius: 16px;
  display: grid;
  place-items: center;
  color: var(--category-color, #2563eb);
  background: color-mix(in srgb, var(--category-color, #2563eb) 16%, #ffffff);
  overflow: hidden;
  flex-shrink: 0;
  font-weight: 700;
}

.category-avatar img {
  width: 100%;
  height: 100%;
  object-fit: cover;
}

.color-chip {
  display: inline-flex;
  align-items: center;
  gap: 8px;
}

.color-dot {
  width: 14px;
  height: 14px;
  border-radius: 50%;
}

.pagination-wrap {
  margin-top: 14px;
  display: flex;
  justify-content: flex-end;
}

.theme-row {
  display: grid;
  grid-template-columns: 1fr 52px;
  gap: 10px;
  width: 100%;
}

.color-picker {
  width: 52px;
  height: 40px;
  border: none;
  background: transparent;
  cursor: pointer;
}

.preview-chip {
  width: 100%;
  display: flex;
  align-items: center;
  gap: 14px;
  padding: 14px 16px;
  border-radius: 18px;
  background: color-mix(in srgb, var(--category-color, #2563eb) 10%, #ffffff);
}

.preview-avatar {
  width: 54px;
  height: 54px;
}

@media (max-width: 1280px) {
  .stats-grid,
  .filter-grid {
    grid-template-columns: repeat(2, minmax(0, 1fr));
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

@media (max-width: 900px) {
  .stats-grid,
  .filter-grid {
    grid-template-columns: 1fr;
  }
}
</style>
