<template>
  <div class="page-shell profile-page">
    <header class="top-nav glass-panel">
      <div class="page-width nav-inner">
        <div class="brand" @click="router.push('/home')">
          <div class="brand-mark">我</div>
          <div class="brand-copy">
            <strong>涉毒刑事普法系统</strong>
            <span>个人中心</span>
          </div>
        </div>

        <nav class="main-nav">
          <button class="nav-link" @click="router.push('/home')">首页</button>
          <button class="nav-link" @click="router.push('/items')">专题案例</button>
          <button class="nav-link" @click="router.push('/chat')">AI律师咨询</button>
          <button class="nav-link active">个人中心</button>
        </nav>
      </div>
    </header>

    <section class="page-width profile-layout">
      <aside class="profile-hero panel-card">
        <div class="avatar-badge">{{ userInfo.username?.slice(0, 1) || 'U' }}</div>
        <div class="hero-copy">
          <p class="eyebrow">Profile</p>
          <h1>个人中心</h1>
          <p>这里维护账号的基础资料，和聊天页、收藏页、后台头像展示保持一致。</p>
        </div>
      </aside>

      <section class="panel-card form-panel">
        <h2>个人信息</h2>
        <el-form :model="userInfo" label-width="92px">
          <el-form-item label="用户名">
            <el-input v-model="userInfo.username" disabled />
          </el-form-item>
          <el-form-item label="真实姓名">
            <el-input v-model="userInfo.realName" />
          </el-form-item>
          <el-form-item label="手机号">
            <el-input v-model="userInfo.phone" />
          </el-form-item>
          <el-form-item label="邮箱">
            <el-input v-model="userInfo.email" />
          </el-form-item>
          <el-form-item>
            <el-button type="primary" @click="handleUpdate">保存</el-button>
          </el-form-item>
        </el-form>
      </section>
    </section>
  </div>
</template>

<script setup lang="ts">
import { ref, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { userApi } from '@/api/user'
import { useUserStore } from '@/stores/user'

const router = useRouter()
const userStore = useUserStore()
const userInfo = ref({
  id: 0,
  username: '',
  realName: '',
  phone: '',
  email: ''
})

onMounted(async () => {
  try {
    if (userStore.user?.id) {
      const res = await userApi.getUserInfo(userStore.user.id)
      userInfo.value = res.data
    }
  } catch (error) {
    console.error('获取用户信息失败:', error)
  }
})

const handleUpdate = async () => {
  try {
    await userApi.updateUser(userInfo.value)
    userStore.setUser(userInfo.value)
    userStore.saveToStorage()
    ElMessage.success('更新成功')
  } catch (error) {
    ElMessage.error('更新失败')
    console.error('更新失败:', error)
  }
}
</script>

<style scoped>
.profile-page { background: linear-gradient(180deg, #f4f8fe, #edf3fb); }
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
.profile-layout { padding-top: 24px; padding-bottom: 32px; display: grid; grid-template-columns: 300px minmax(0, 1fr); gap: 18px; }
.profile-hero,.form-panel { border-radius: var(--radius-xl); }
.profile-hero { padding: 22px; display: flex; flex-direction: column; gap: 18px; align-items: flex-start; }
.avatar-badge { width: 64px; height: 64px; border-radius: 20px; display: grid; place-items: center; background: linear-gradient(135deg, var(--brand), var(--brand-2)); color: #fff; font-size: 26px; font-weight: 800; }
.eyebrow { margin: 0 0 8px; color: var(--brand); text-transform: uppercase; letter-spacing: .18em; font-size: 12px; }
.hero-copy h1 { margin: 0; font-size: var(--headline-lg); }
.hero-copy p:last-child { margin: 12px 0 0; color: var(--text-soft); line-height: 1.72; font-size: 13px; }
.form-panel { padding: 22px 24px; }
.form-panel h2 { margin: 0 0 18px; font-size: 20px; }
@media (max-width: 1080px) { .nav-inner, .profile-layout { grid-template-columns: 1fr; } .nav-inner { padding-top: 12px; padding-bottom: 12px; } .main-nav { justify-content: flex-start; } }
</style>
