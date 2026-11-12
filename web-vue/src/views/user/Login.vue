<template>
  <div class="login-page">
    <div class="backdrop"></div>

    <section class="hero-copy">
      <div class="brand-row">
        <div class="brand-badge">
          <svg viewBox="0 0 24 24" fill="currentColor" width="28" height="28">
            <path d="M12 2L4 5v6.09c0 5.05 3.41 9.76 8 10.91 4.59-1.15 8-5.86 8-10.91V5l-8-3zm0 3.18 5 1.88v4.03c0 3.98-2.48 7.73-5 8.81-2.52-1.08-5-4.83-5-8.81V7.06l5-1.88z"/>
          </svg>
        </div>
        <div>
          <h1>涉毒刑事法律咨询系统</h1>
          <p>聚焦法律风险分析与普法的专题问答平台</p>
        </div>
      </div>

      <div class="feature-stack">
        <article class="feature-card">
          <span class="feature-icon">01</span>
          <div>
            <strong>专题检索</strong>
            <p>围绕涉毒刑事风险问题，联动法条、案例和规范知识进行检索。</p>
          </div>
        </article>
        <article class="feature-card">
          <span class="feature-icon">02</span>
          <div>
            <strong>专题语料</strong>
            <p>接入司法解释、指导性案例和刑事程序材料，支持检索增强问答。</p>
          </div>
        </article>
        <article class="feature-card">
          <span class="feature-icon">03</span>
          <div>
            <strong>风险识别</strong>
            <p>通过向量检索和案例召回，输出更贴近涉毒刑事场景的参考结果。</p>
          </div>
        </article>
        <article class="feature-card">
          <span class="feature-icon">04</span>
          <div>
            <strong>普法分析</strong>
            <p>结合案例库、检索结果和大模型生成结构化风险提示与普法建议。</p>
          </div>
        </article>
      </div>
    </section>

    <section class="login-panel">
      <div class="login-card">
        <div class="login-head">
          <h2>欢迎登录</h2>
          <p>请输入您的账号信息</p>
        </div>

        <el-form ref="formRef" :model="loginForm" :rules="rules" class="login-form">
          <el-form-item prop="username">
            <el-input v-model="loginForm.username" placeholder="请输入用户名" size="large" class="glass-input">
              <template #prefix>
                <svg viewBox="0 0 24 24" fill="currentColor" width="18" height="18">
                  <path d="M12 12c2.21 0 4-1.79 4-4s-1.79-4-4-4-4 1.79-4 4 1.79 4 4 4zm0 2c-2.67 0-8 1.34-8 4v2h16v-2c0-2.66-5.33-4-8-4z"/>
                </svg>
              </template>
            </el-input>
          </el-form-item>

          <el-form-item prop="password">
            <el-input
              v-model="loginForm.password"
              type="password"
              show-password
              placeholder="请输入密码"
              size="large"
              class="glass-input"
              @keyup.enter="handleLogin"
            >
              <template #prefix>
                <svg viewBox="0 0 24 24" fill="currentColor" width="18" height="18">
                  <path d="M18 8h-1V6c0-2.76-2.24-5-5-5S7 3.24 7 6v2H6c-1.1 0-2 .9-2 2v10c0 1.1.9 2 2 2h12c1.1 0 2-.9 2-2V10c0-1.1-.9-2-2-2zM9 6c0-1.66 1.34-3 3-3s3 1.34 3 3v2H9V6zm9 14H6V10h12v10zm-6-3c1.1 0 2-.9 2-2s-.9-2-2-2-2 .9-2 2 .9 2 2 2z"/>
                </svg>
              </template>
            </el-input>
          </el-form-item>

          <el-button type="primary" size="large" class="login-button" :loading="loading" @click="handleLogin">
            {{ loading ? '登录中...' : '安全登录' }}
          </el-button>

          <button type="button" class="register-link" @click="router.push('/register')">
            + 注册新账号
          </button>
        </el-form>
      </div>
    </section>
  </div>
</template>

<script setup lang="ts">
import { reactive, ref } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import type { FormInstance, FormRules } from 'element-plus'
import { userApi } from '@/api/user'
import { useUserStore } from '@/stores/user'

const router = useRouter()
const userStore = useUserStore()
const formRef = ref<FormInstance>()
const loading = ref(false)

const loginForm = reactive({
  username: '',
  password: ''
})

const rules: FormRules = {
  username: [{ required: true, message: '请输入用户名', trigger: 'blur' }],
  password: [{ required: true, message: '请输入密码', trigger: 'blur' }]
}

const handleLogin = async () => {
  if (!formRef.value) return

  await formRef.value.validate(async (valid) => {
    if (!valid) return
    loading.value = true
    try {
      const res = await userApi.login(loginForm)
      if (res.data?.user && res.data?.token) {
        userStore.setUser(res.data.user)
        userStore.setToken(res.data.token)
        userStore.saveToStorage()
        ElMessage.success('登录成功')
        router.push(res.data.user.role === 'admin' ? '/admin' : '/home')
      }
    } finally {
      loading.value = false
    }
  })
}
</script>

<style scoped>
.login-page {
  position: relative;
  min-height: 100vh;
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 40px;
  overflow: hidden;
  padding: 44px 56px;
  background: url('@/assets/images/login.jpg') center center / cover no-repeat;
}

.backdrop {
  position: absolute;
  inset: 0;
  background:
    linear-gradient(90deg, rgba(18, 29, 48, 0.12) 0%, rgba(18, 29, 48, 0.06) 42%, rgba(18, 29, 48, 0.42) 72%, rgba(18, 29, 48, 0.58) 100%);
}

.hero-copy,
.login-panel {
  position: relative;
  z-index: 1;
}

.hero-copy {
  max-width: 560px;
  color: #fff;
}

.brand-row {
  display: flex;
  align-items: center;
  gap: 16px;
  margin-bottom: 24px;
}

.brand-badge {
  width: 58px;
  height: 58px;
  border-radius: 18px;
  display: grid;
  place-items: center;
  color: #fff;
  background: linear-gradient(180deg, #2f9fff, #2d74f7);
  box-shadow: 0 18px 32px rgba(43, 115, 241, 0.26);
}

.brand-row h1 {
  margin: 0;
  font-size: 40px;
  line-height: 1.12;
  font-weight: 800;
}

.brand-row p {
  margin: 10px 0 0;
  font-size: 18px;
  color: rgba(255, 255, 255, 0.86);
}

.feature-stack {
  display: grid;
  gap: 14px;
  max-width: 420px;
}

.feature-card {
  display: flex;
  align-items: flex-start;
  gap: 14px;
  padding: 16px 18px;
  border-radius: 18px;
  background: rgba(26, 37, 57, 0.38);
  border: 1px solid rgba(255, 255, 255, 0.12);
  backdrop-filter: blur(12px);
  box-shadow: 0 14px 30px rgba(16, 18, 23, 0.18);
}

.feature-icon {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  width: 34px;
  height: 34px;
  border-radius: 12px;
  background: rgba(255, 255, 255, 0.14);
  color: #fff;
  font-size: 12px;
  font-weight: 800;
  letter-spacing: .08em;
}

.feature-card strong,
.feature-card p {
  display: block;
}

.feature-card strong {
  font-size: 19px;
  line-height: 1.2;
}

.feature-card p {
  margin: 6px 0 0;
  font-size: 13px;
  color: rgba(255, 255, 255, 0.86);
  line-height: 1.6;
}

.login-panel {
  display: flex;
  justify-content: flex-end;
  width: min(100%, 420px);
}

.login-card {
  width: 100%;
  padding: 40px 36px 30px;
  border-radius: 28px;
  background: linear-gradient(180deg, rgba(28, 35, 47, 0.74), rgba(21, 26, 36, 0.84));
  border: 1px solid rgba(255, 255, 255, 0.16);
  box-shadow: 0 24px 72px rgba(16, 18, 23, 0.28);
  backdrop-filter: blur(16px);
}

.login-head {
  text-align: center;
  color: #fff;
}

.login-head h2 {
  margin: 0;
  font-size: 36px;
  font-weight: 800;
}

.login-head p {
  margin: 12px 0 0;
  color: rgba(255, 255, 255, 0.72);
  font-size: 14px;
}

.login-form {
  margin-top: 30px;
}

:deep(.glass-input .el-input__wrapper) {
  min-height: 50px;
  border-radius: 12px;
  background: rgba(255, 255, 255, 0.92);
  box-shadow: none;
}

:deep(.glass-input .el-input__inner) {
  color: #2e3440;
}

.login-button {
  width: 100%;
  min-height: 52px;
  margin-top: 10px;
  border-radius: 14px;
  border: 1px solid rgba(255, 255, 255, 0.2);
  background: linear-gradient(180deg, rgba(59, 60, 64, 0.92), rgba(43, 45, 49, 0.96));
  font-size: 17px;
  font-weight: 700;
}

.register-link {
  display: block;
  width: 100%;
  margin-top: 18px;
  border: none;
  background: transparent;
  color: rgba(255, 255, 255, 0.78);
  font-size: 15px;
  cursor: pointer;
}

@media (max-width: 1180px) {
  .login-page {
    padding: 36px 24px;
    flex-direction: column;
    align-items: stretch;
  }

  .hero-copy {
    max-width: none;
  }

  .feature-stack {
    max-width: none;
    grid-template-columns: repeat(2, minmax(0, 1fr));
  }

  .login-panel {
    width: 100%;
    justify-content: center;
  }
}

@media (max-width: 720px) {
  .brand-row {
    align-items: flex-start;
  }

  .brand-row h1 {
    font-size: 32px;
  }

  .brand-row p {
    font-size: 16px;
  }

  .feature-stack {
    grid-template-columns: 1fr;
  }

  .login-card {
    padding: 28px 22px 24px;
    border-radius: 24px;
  }

  .login-head h2 {
    font-size: 30px;
  }
}
</style>
