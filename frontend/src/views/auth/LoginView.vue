<template>
  <div class="login-container">
    <div class="login-bg-pattern"></div>
    <div class="login-content">
      <div class="login-card">
        <div class="login-header">
          <span class="login-icon">📄</span>
          <h2 class="login-title">DocGen</h2>
          <p class="login-subtitle">{{ $t('auth.login') }}</p>
        </div>

        <el-form
          ref="formRef"
          :model="form"
          :rules="rules"
          class="login-form"
          @submit.prevent="handleLogin"
        >
          <el-form-item prop="username">
            <el-input
              v-model="form.username"
              :placeholder="$t('auth.username')"
              :prefix-icon="User"
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
          <el-form-item>
            <el-button
              type="primary"
              native-type="submit"
              :loading="loading"
              class="login-btn"
              size="large"
            >
              {{ loading ? $t('common.loading') : $t('auth.login') }}
            </el-button>
          </el-form-item>
        </el-form>

        <div class="form-links">
          <router-link to="/register">{{ $t('auth.goRegister') }}</router-link>
          <a href="#" @click.prevent="resetPasswordDialogVisible = true">
            {{ $t('auth.forgotPassword') }}
          </a>
        </div>

        <div class="login-footer">
          <span>© 2025 DocGen</span>
        </div>
      </div>
    </div>

    <ResetPasswordDialog v-model:visible="resetPasswordDialogVisible" />
  </div>
</template>

<script setup lang="ts">
import { reactive, ref } from 'vue'
import { useRouter } from 'vue-router'
import { useI18n } from 'vue-i18n'
import { ElMessage } from 'element-plus'
import type { FormInstance, FormRules } from 'element-plus'
import { User, Lock } from '@element-plus/icons-vue'
import { useUserStore } from '@/stores/user'
import ResetPasswordDialog from './ResetPasswordDialog.vue'

const router = useRouter()
const { t } = useI18n()
const userStore = useUserStore()

const formRef = ref<FormInstance>()
const loading = ref(false)
const resetPasswordDialogVisible = ref(false)

const form = reactive({ username: '', password: '' })

const rules: FormRules = {
  username: [{ required: true, message: () => t('auth.usernameRequired'), trigger: 'blur' }],
  password: [{ required: true, message: () => t('auth.passwordRequired'), trigger: 'blur' }],
}

async function handleLogin() {
  const valid = await formRef.value?.validate().catch(() => false)
  if (!valid) return

  loading.value = true
  try {
    await userStore.login({ username: form.username, password: form.password })
    ElMessage.success(t('auth.loginSuccess'))
    router.push('/')
  } catch {
    // Error already handled by interceptor
  } finally {
    loading.value = false
  }
}
</script>

<style scoped>
.login-container {
  height: 100vh;
  display: flex;
  align-items: center;
  justify-content: center;
  background: linear-gradient(135deg, #DB0011 0%, #8B0000 100%);
  position: relative;
  overflow: hidden;
}

.login-bg-pattern {
  position: absolute;
  top: 0; left: 0; right: 0; bottom: 0;
  background-image:
    radial-gradient(circle at 20% 80%, rgba(255,255,255,0.1) 0%, transparent 50%),
    radial-gradient(circle at 80% 20%, rgba(255,255,255,0.08) 0%, transparent 50%),
    radial-gradient(circle at 40% 40%, rgba(255,255,255,0.05) 0%, transparent 30%);
  pointer-events: none;
}

.login-content {
  position: relative;
  z-index: 1;
}

.login-card {
  width: 420px;
  padding: 40px;
  background: white;
  border-radius: 16px;
  box-shadow: 0 20px 60px rgba(0, 0, 0, 0.3);
}

.login-header {
  text-align: center;
  margin-bottom: 32px;
}

.login-icon {
  font-size: 48px;
  display: block;
  margin-bottom: 16px;
}

.login-title {
  font-size: 24px;
  font-weight: 600;
  color: #303133;
  margin: 0 0 8px 0;
}

.login-subtitle {
  font-size: 14px;
  color: #909399;
  margin: 0;
}

.login-form :deep(.el-input__wrapper) {
  border-radius: 8px;
}

.login-form :deep(.el-form-item) {
  margin-bottom: 20px;
}

.login-btn {
  width: 100%;
  height: 44px;
  font-size: 16px;
  border-radius: 8px;
  background: linear-gradient(135deg, #DB0011 0%, #8B0000 100%);
  border: none;
}

.login-btn:hover {
  background: linear-gradient(135deg, #e6263a 0%, #a00010 100%);
}

.form-links {
  display: flex;
  justify-content: space-between;
  align-items: center;
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

.login-footer {
  text-align: center;
  margin-top: 24px;
  padding-top: 20px;
  border-top: 1px solid #ebeef5;
}

.login-footer span {
  font-size: 12px;
  color: #c0c4cc;
}
</style>
