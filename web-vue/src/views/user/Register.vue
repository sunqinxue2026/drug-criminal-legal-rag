<template>
  <div class="register-container">
    <div class="register-box">
      <p class="register-kicker">Create Account</p>
      <h2 class="register-title">用户注册</h2>
      <p class="register-subtitle">创建账号后即可进入案例检索、AI律师咨询和收藏同步流程。</p>
      <el-form :model="registerForm" :rules="rules" ref="formRef" class="register-form">
        <el-form-item prop="username">
          <el-input v-model="registerForm.username" placeholder="用户名" prefix-icon="User" size="large" />
        </el-form-item>
        <el-form-item prop="password">
          <el-input v-model="registerForm.password" type="password" placeholder="密码" prefix-icon="Lock" size="large" />
        </el-form-item>
        <el-form-item prop="realName">
          <el-input v-model="registerForm.realName" placeholder="真实姓名" prefix-icon="User" size="large" />
        </el-form-item>
        <el-form-item prop="phone">
          <el-input v-model="registerForm.phone" placeholder="手机号" prefix-icon="Phone" size="large" />
        </el-form-item>
        <el-form-item prop="email">
          <el-input v-model="registerForm.email" placeholder="邮箱" prefix-icon="Message" size="large" />
        </el-form-item>
        <el-form-item>
          <el-button type="primary" size="large" style="width: 100%" :loading="loading" @click="handleRegister">
            注册
          </el-button>
        </el-form-item>
        <div class="register-footer">
          <span>已有账号？</span>
          <router-link to="/login">立即登录</router-link>
        </div>
      </el-form>
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref, reactive } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { userApi } from '@/api/user'
import type { FormInstance, FormRules } from 'element-plus'

const router = useRouter()
const formRef = ref<FormInstance>()
const loading = ref(false)

const registerForm = reactive({
  username: '',
  password: '',
  realName: '',
  phone: '',
  email: ''
})

const rules: FormRules = {
  username: [{ required: true, message: '请输入用户名', trigger: 'blur' }],
  password: [{ required: true, message: '请输入密码', trigger: 'blur' }],
  realName: [{ required: true, message: '请输入真实姓名', trigger: 'blur' }],
  phone: [{ required: true, message: '请输入手机号', trigger: 'blur' }],
  email: [{ required: true, message: '请输入邮箱', trigger: 'blur' }]
}

const handleRegister = async () => {
  if (!formRef.value) return
  
  await formRef.value.validate(async (valid) => {
    if (valid) {
      loading.value = true
      try {
        await userApi.register(registerForm)
        ElMessage.success('注册成功，请登录')
        router.push('/login')
      } catch (error) {
        console.error('注册失败:', error)
      } finally {
        loading.value = false
      }
    }
  })
}
</script>

<style scoped>
.register-container {
  min-height: 100vh;
  display: flex;
  align-items: center;
  justify-content: center;
  padding: 24px;
  background:
    radial-gradient(circle at top left, rgba(62, 123, 250, 0.18), transparent 28%),
    radial-gradient(circle at bottom right, rgba(53, 201, 187, 0.14), transparent 24%),
    linear-gradient(180deg, #eef4fb, #f5f8fc);
}

.register-box {
  width: min(100%, 440px);
  padding: 34px 32px 28px;
  background: rgba(255, 255, 255, 0.94);
  border-radius: 28px;
  border: 1px solid rgba(229, 236, 244, 0.95);
  box-shadow: 0 20px 44px rgba(17, 27, 46, 0.08);
  backdrop-filter: blur(16px);
}

.register-kicker {
  margin: 0 0 8px;
  color: var(--brand);
  text-transform: uppercase;
  letter-spacing: .18em;
  font-size: 12px;
}

.register-title {
  margin: 0;
  color: var(--text-main);
  font-size: 30px;
  line-height: 1.2;
}

.register-subtitle {
  margin: 10px 0 22px;
  color: var(--text-soft);
  font-size: 13px;
  line-height: 1.7;
}

.register-form {
  margin-top: 0;
}

.register-footer {
  text-align: center;
  margin-top: 18px;
  color: var(--text-muted);
  font-size: 13px;
}

.register-footer a {
  color: var(--brand);
  text-decoration: none;
  margin-left: 5px;
}
</style>
