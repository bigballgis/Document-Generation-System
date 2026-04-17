<template>
  <el-container class="main-layout">
    <!-- Top header with red gradient -->
    <el-header class="app-header">
      <div class="header-left">
        <div class="logo">
          <span class="logo-icon">📄</span>
          <span v-if="!isCollapsed" class="logo-text">DocGen</span>
        </div>
        <el-icon class="collapse-btn" @click="isCollapsed = !isCollapsed">
          <Fold v-if="!isCollapsed" />
          <Expand v-else />
        </el-icon>
      </div>
      <div class="header-right">
        <el-dropdown @command="changeLocale">
          <span class="locale-trigger">{{ currentLocaleName }}</span>
          <template #dropdown>
            <el-dropdown-menu>
              <el-dropdown-item command="en-US">English</el-dropdown-item>
              <el-dropdown-item command="zh-CN">简体中文</el-dropdown-item>
              <el-dropdown-item command="zh-TW">繁體中文</el-dropdown-item>
            </el-dropdown-menu>
          </template>
        </el-dropdown>
        <el-dropdown @command="handleUserCommand">
          <span class="user-trigger">
            <el-avatar :size="28" :icon="UserFilled" />
            <span class="username">{{ displayName }}</span>
          </span>
          <template #dropdown>
            <el-dropdown-menu>
              <el-dropdown-item disabled>
                <el-tag size="small" type="info">{{ userStore.userRole || 'USER' }}</el-tag>
              </el-dropdown-item>
              <el-dropdown-item divided command="logout">
                {{ $t('auth.logout') }}
              </el-dropdown-item>
            </el-dropdown-menu>
          </template>
        </el-dropdown>
      </div>
    </el-header>

    <el-container class="app-body">
      <!-- White sidebar -->
      <el-aside :width="isCollapsed ? '64px' : '240px'" class="app-aside">
        <el-scrollbar>
          <el-menu
            :default-active="activeMenuIndex"
            :collapse="isCollapsed"
            :collapse-transition="false"
            class="app-menu"
            router
          >
            <el-menu-item index="/dashboard">
              <el-icon><Monitor /></el-icon>
              <template #title>{{ $t('nav.dashboard') }}</template>
            </el-menu-item>
            <el-menu-item index="/templates">
              <el-icon><Document /></el-icon>
              <template #title>{{ $t('nav.templateManagement') }}</template>
            </el-menu-item>
            <el-menu-item index="/documents">
              <el-icon><Files /></el-icon>
              <template #title>{{ $t('nav.documents') }}</template>
            </el-menu-item>
            <el-menu-item index="/tasks">
              <el-icon><Clock /></el-icon>
              <template #title>{{ $t('nav.tasks') }}</template>
            </el-menu-item>
            <el-menu-item index="/market">
              <el-icon><Shop /></el-icon>
              <template #title>{{ $t('nav.market') }}</template>
            </el-menu-item>
            <el-menu-item v-if="canAccessAdmin" index="/admin">
              <el-icon><Setting /></el-icon>
              <template #title>{{ $t('nav.admin') }}</template>
            </el-menu-item>
            <el-menu-item v-if="canAccessAudit" index="/audit">
              <el-icon><List /></el-icon>
              <template #title>{{ $t('nav.audit') }}</template>
            </el-menu-item>
          </el-menu>
        </el-scrollbar>
      </el-aside>

      <!-- Main content -->
      <el-main class="app-main">
        <router-view />
      </el-main>
    </el-container>
  </el-container>
</template>

<script setup lang="ts">
import { computed, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { useI18n } from 'vue-i18n'
import {
  Monitor, Document, Shop, Setting, List,
  Fold, Expand, UserFilled, Files, Clock,
} from '@element-plus/icons-vue'
import { useUserStore } from '@/stores/user'

const route = useRoute()
const router = useRouter()
const { locale } = useI18n()
const userStore = useUserStore()

const isCollapsed = ref(false)

const activeMenuIndex = computed(() => {
  const path = route.path
  if (path.startsWith('/templates')) return '/templates'
  return path
})

const localeNames: Record<string, string> = {
  'en-US': 'English',
  'zh-CN': '简体中文',
  'zh-TW': '繁體中文',
}

const currentLocaleName = computed(() => localeNames[locale.value] || 'English')
const displayName = computed(() => userStore.userInfo?.username || 'User')

const adminRoles = ['SUPER_ADMIN', 'TENANT_ADMIN']
const auditRoles = ['SUPER_ADMIN', 'TENANT_ADMIN', 'TEAM_ADMIN']

const canAccessAdmin = computed(() => {
  const role = userStore.userRole
  return role ? adminRoles.includes(role) : false
})

const canAccessAudit = computed(() => {
  const role = userStore.userRole
  return role ? auditRoles.includes(role) : false
})

function changeLocale(lang: string) {
  locale.value = lang
  localStorage.setItem('locale', lang)
}

function handleUserCommand(command: string) {
  if (command === 'logout') {
    userStore.logout()
    router.push('/login')
  }
}
</script>

<style scoped>
.main-layout {
  height: 100vh;
  overflow: hidden;
}

/* ── Top Header ── */
.app-header {
  height: 60px;
  display: flex;
  justify-content: space-between;
  align-items: center;
  background: linear-gradient(135deg, #DB0011 0%, #8B0000 100%);
  color: white;
  padding: 0 20px;
  box-shadow: 0 2px 8px rgba(0, 0, 0, 0.15);
  z-index: 100;
}

.header-left {
  display: flex;
  align-items: center;
  gap: 20px;
}

.logo {
  display: flex;
  align-items: center;
  gap: 10px;
}

.logo-icon {
  font-size: 24px;
}

.logo-text {
  font-size: 18px;
  font-weight: 600;
  letter-spacing: 1px;
  color: white;
}

.collapse-btn {
  font-size: 20px;
  cursor: pointer;
  padding: 8px;
  border-radius: 6px;
  color: white;
  transition: background-color 0.3s;
}

.collapse-btn:hover {
  background-color: rgba(255, 255, 255, 0.15);
}

.header-right {
  display: flex;
  align-items: center;
  gap: 24px;
}

.locale-trigger {
  cursor: pointer;
  font-size: 14px;
  color: rgba(255, 255, 255, 0.9);
}

.locale-trigger:hover {
  color: white;
}

.user-trigger {
  display: flex;
  align-items: center;
  gap: 8px;
  cursor: pointer;
}

.username {
  font-size: 14px;
  color: rgba(255, 255, 255, 0.9);
}

/* ── Body ── */
.app-body {
  height: calc(100vh - 60px);
}

/* ── White Sidebar ── */
.app-aside {
  background: #ffffff;
  border-right: 1px solid #e6e8eb;
  transition: width 0.3s;
  overflow: hidden;
}

.app-menu {
  border-right: none;
  height: 100%;
}

.app-menu :deep(.el-menu-item),
.app-menu :deep(.el-sub-menu__title) {
  height: 50px;
  line-height: 50px;
  margin: 4px 8px;
  border-radius: 8px;
}

.app-menu :deep(.el-menu-item:hover),
.app-menu :deep(.el-sub-menu__title:hover) {
  background-color: rgba(219, 0, 17, 0.08);
}

.app-menu :deep(.el-menu-item.is-active) {
  background-color: rgba(219, 0, 17, 0.12);
  color: #DB0011;
  font-weight: 500;
  position: relative;
}

.app-menu :deep(.el-menu-item.is-active::before) {
  content: '';
  position: absolute;
  left: 0;
  top: 50%;
  transform: translateY(-50%);
  width: 3px;
  height: 24px;
  background-color: #DB0011;
  border-radius: 0 3px 3px 0;
}

.app-menu :deep(.el-menu--collapse .el-menu-item),
.app-menu :deep(.el-menu--collapse .el-sub-menu__title) {
  margin: 4px;
}

/* ── Main Content ── */
.app-main {
  background-color: #f5f7fa;
  padding: 20px;
  overflow-y: auto;
}
</style>
