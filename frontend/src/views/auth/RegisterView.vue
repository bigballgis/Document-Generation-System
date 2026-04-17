<template>
  <div class="register-container">
    <div class="register-bg-pattern"></div>
    <div class="register-content">
      <div class="register-card">
        <div class="register-header">
          <span class="register-icon">📄</span>
          <h2 class="register-title">DocGen</h2>
          <p class="register-subtitle">{{ $t('auth.register') }}</p>
        </div>

        <el-form
          ref="formRef"
          :model="form"
          :rules="rules"
          class="register-form"
          @keyup.enter="handleRegister"
        >
          <el-form-item prop="username">
            <el-input
              v-model="form.username"
              :placeholder="$t('auth.username')"
              :prefix-icon="User"
              size="large"
            />
          </el-form-item>
          <el-form-item prop="email">
            <el-input
              v-model="form.email"
              :placeholder="$t('auth.email')"
              :prefix-icon="Message"
              size="large"
            />
          </el-form-item>
          <el-form-item prop="password">
            <el-input
              v-model="form.password"
              type="password"
              :placeholder="$t('auth.password')"
              :prefix-icon="Lock"
              show-password
              size="large"
            />
          </el-form-item>
          <el-form-item prop="confirmPassword">
            <el-input
              v-model="form.confirmPassword"
              type="password"
              :placeholder="$t('auth.confirmPassword')"
              :prefix-icon="Lock"
              show-password
              size="large"
            />
          </el-form-item>
          <el-form-item>
            <el-button
              type="primary"
              native-type="submit"
              :loading="loading"
              class="register-btn"
              size="large"
              @click="handleRegister"
            >
              {{ $t('auth.register') }}
            </el-button>
          </el-form-item>
        </el-form>

        <div class="form-links">
          <router-link to="/login">{{ $t('auth.goLogin') }}</router-link>
        </div>

        <div class="register-footer">
          <span>© 2025 DocGen</span>
        </div>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { reactive, ref } from 'vue'
import { useRouter } from 'vue-router'
import { useI18n } from 'vue-i18n'
import { ElMessage } from 'element-plus'
import type { FormInstance, FormRules } from 'element-plus'
import { User, Lock, Message } from '@element-plus/icons-vue'
import { useUserStore } from '@/stores/user'

const router = useRouter()
const { t } = useI18n()
const userStore = useUserStore()

const formRef = ref<FormInstance>()
const loading = ref(false)

const form = reactive({
  username: '',
  email: '',
  password: '',
  confirmPassword: '',
})

const validatePassword = (_rule: any, value: string, callback: any) => {
  if (!value) { callback(new Error(t('auth.passwordRequired'))); return }
  if (value.length < 8) { callback(new Error(t('auth.passwordMinLength'))); return }
  const hasUpper = /[A-Z]/.test(value)
  const hasLower = /[a-z]/.test(value)
  const hasDigit = /\d/.test(value)
  const hasSpecial = /[!@#$%^&*()_+\-=[\]{};':"\\|,.<>/?]/.test(value)
  if (!hasUpper || !hasLower || !hasDigit || !hasSpecial) {
    callback(new Error(t('auth.passwordStrength'))); return
  }
  callback()
}

const validateConfirmPassword = (_rule: any, value: string, callback: any) => {
  if (value !== form.password) { callback(new Error(t('auth.passwordMismatch'))); return }
  callback()
}

const rules: FormRules = {
  username: [{ required: true, message: () => t('auth.usernameRequired'), trigger: 'blur' }],
  email: [
    { required: true, message: () => t('auth.emailRequired'), trigger: 'blur' },
    { type: 'email', message: () => t('auth.emailInvalid'), trigger: 'blur' },
  ],
  password: [{ required: true, validator: validatePassword, trigger: 'blur' }],
  confirmPassword: [{ required: true, validator: validateConfirmPassword, trigger: 'blur' }],
}

async function handleRegister() {
  const valid = await formRef.value?.validate().catch(() => false)
  if (!valid) return
  loading.value = true
  try {
    await userStore.register({
      username: form.username,
      email: form.email,
      password: form.password,
    })
    ElMessage.success(t('auth.registerSuccess'))
    router.push('/')
  } catch {
    // Error already handled by interceptor
  } finally {
    loading.value = false
  }
}
</script>

<style scoped>
.register-container {
  height: 100vh;
  display: flex;
  align-items: center;
  justify-content: center;
  background: linear-gradient(135deg, #DB0011 0%, #8B0000 100%);
  position: relative;
  overflow: hidden;
}

.register-bg-pattern {
  position: absolute;
  top: 0; left: 0; right: 0; bottom: 0;
  background-image:
    radial-gradient(circle at 20% 80%, rgba(255,255,255,0.1) 0%, transparent 50%),
    radial-gradient(circle at 80% 20%, rgba(255,255,255,0.08) 0%, transparent 50%),
    radial-gradient(circle at 40% 40%, rgba(255,255,255,0.05) 0%, transparent 30%);
  pointer-events: none;
}

.register-content {
  position: relative;
  z-index: 1;
}

.register-card {
  width: 420px;
  padding: 40px;
  background: white;
  border-radius: 16px;
  box-shadow: 0 20px 60px rgba(0, 0, 0, 0.3);
}

.register-header {
  text-align: center;
  margin-bottom: 32px;
}

.register-icon {
  font-size: 48px;
  display: block;
  margin-bottom: 16px;
}

.register-title {
  font-size: 24px;
  font-weight: 600;
  color: #303133;
  margin: 0 0 8px 0;
}

.register-subtitle {
  font-size: 14px;
  color: #909399;
  margin: 0;
}

.register-form :deep(.el-input__wrapper) {
  border-radius: 8px;
}

.register-form :deep(.el-form-item) {
  margin-bottom: 20px;
}

.register-btn {
  width: 100%;
  height: 44px;
  font-size: 16px;
  border-radius: 8px;
  background: linear-gradient(135deg, #DB0011 0%, #8B0000 100%);
  border: none;
}

.register-btn:hover {
  background: linear-gradient(135deg, #e6263a 0%, #a00010 100%);
}

.form-links {
  text-align: center;
  margin-top: 4px;
}

.form-links a {
  color: #DB0011;
  text-decoration: none;
  font-size: 14px;
}

.form-links a:hover {
  color: #ff4d4f;
}

.register-footer {
  text-align: center;
  margin-top: 24px;
  padding-top: 20px;
  border-top: 1px solid #ebeef5;
}

.register-footer span {
  font-size: 12px;
  color: #c0c4cc;
}
</style>
