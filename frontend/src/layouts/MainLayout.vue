<template>
  <el-container class="main-layout">
    <el-aside :width="isCollapsed ? '64px' : '220px'">
      <div class="logo">
        <span v-if="!isCollapsed">DocGen</span>
        <span v-else>D</span>
      </div>
      <el-menu
        :default-active="route.path"
        :collapse="isCollapsed"
        router
        background-color="#304156"
        text-color="#bfcbd9"
        active-text-color="#409eff"
      >
        <el-menu-item index="/dashboard">
          <el-icon><Monitor /></el-icon>
          <template #title>{{ $t('nav.dashboard') }}</template>
        </el-menu-item>
        <el-menu-item index="/templates">
          <el-icon><Document /></el-icon>
          <template #title>{{ $t('nav.templates') }}</template>
        </el-menu-item>
        <el-menu-item index="/documents">
          <el-icon><Files /></el-icon>
          <template #title>{{ $t('nav.documents') }}</template>
        </el-menu-item>
        <el-menu-item index="/tasks">
          <el-icon><Clock /></el-icon>
          <template #title>{{ $t('nav.tasks') }}</template>
        </el-menu-item>
        <el-menu-item index="/data-sources">
          <el-icon><Connection /></el-icon>
          <template #title>{{ $t('nav.dataSources') }}</template>
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
        <el-sub-menu index="template-components">
          <template #title>
            <el-icon><Grid /></el-icon>
            <span>{{ $t('nav.templateComponents') }}</span>
          </template>
          <el-menu-item index="/segments">{{ $t('nav.segments') }}</el-menu-item>
          <el-menu-item index="/components">{{ $t('nav.components') }}</el-menu-item>
          <el-menu-item index="/composite-templates">{{ $t('nav.compositeTemplates') }}</el-menu-item>
        </el-sub-menu>
      </el-menu>
    </el-aside>
    <el-container>
      <el-header>
        <div class="header-left">
          <el-icon class="collapse-btn" @click="isCollapsed = !isCollapsed">
            <Fold v-if="!isCollapsed" />
            <Expand v-else />
          </el-icon>
          <el-breadcrumb separator="/">
            <el-breadcrumb-item
              v-for="item in breadcrumbs"
              :key="item.path"
              :to="item.path"
            >
              {{ item.title }}
            </el-breadcrumb-item>
          </el-breadcrumb>
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
      <el-main>
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
  Monitor, Document, Connection, Shop, Setting, List,
  Fold, Expand, UserFilled, Files, Clock, Grid,
} from '@element-plus/icons-vue'
import { useUserStore } from '@/stores/user'

const route = useRoute()
const router = useRouter()
const { locale, t } = useI18n()
const userStore = useUserStore()

const isCollapsed = ref(false)

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

const navTitleMap: Record<string, string> = {
  '/dashboard': 'nav.dashboard',
  '/templates': 'nav.templates',
  '/data-sources': 'nav.dataSources',
  '/market': 'nav.market',
  '/admin': 'nav.admin',
  '/audit': 'nav.audit',
  '/documents': 'nav.documents',
  '/tasks': 'nav.tasks',
  '/segments': 'nav.segments',
  '/components': 'nav.components',
  '/composite-templates': 'nav.compositeTemplates',
}

const breadcrumbs = computed(() => {
  const items: Array<{ path: string; title: string }> = []
  const path = route.path
  // Exact match first, then prefix match for detail/editor sub-routes
  const titleKey = navTitleMap[path]
    ?? Object.entries(navTitleMap).find(([prefix]) => path.startsWith(prefix + '/'))?.[1]
  if (titleKey) {
    const basePath = navTitleMap[path] ? path : Object.keys(navTitleMap).find((prefix) => path.startsWith(prefix + '/'))!
    items.push({ path: basePath, title: t(titleKey) })
  }
  // Always append the current page title for sub-routes (detail, editor, etc.)
  if (!navTitleMap[path] && route.meta.title) {
    items.push({ path, title: route.meta.title as string })
  }
  return items
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
}
.logo {
  height: 60px;
  line-height: 60px;
  text-align: center;
  font-size: 20px;
  font-weight: bold;
  color: #fff;
  background-color: #2b2f3a;
  overflow: hidden;
  white-space: nowrap;
}
.el-aside {
  background-color: #304156;
  transition: width 0.3s;
  overflow-x: hidden;
}
.el-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  border-bottom: 1px solid #e6e6e6;
  padding: 0 16px;
}
.header-left {
  display: flex;
  align-items: center;
  gap: 12px;
}
.collapse-btn {
  cursor: pointer;
  font-size: 20px;
  color: #606266;
}
.header-right {
  display: flex;
  align-items: center;
  gap: 16px;
}
.locale-trigger {
  cursor: pointer;
  font-size: 14px;
  color: #606266;
}
.user-trigger {
  display: flex;
  align-items: center;
  gap: 8px;
  cursor: pointer;
}
.username {
  font-size: 14px;
  color: #303133;
}
</style>
