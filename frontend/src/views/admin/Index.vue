<template>
  <div class="admin-page">
    <h2>{{ $t('admin.title') }}</h2>
    <el-tabs v-model="activeTab" type="border-card">
      <el-tab-pane :label="$t('admin.tenants')" name="tenants">
        <TenantManagement />
      </el-tab-pane>
      <el-tab-pane :label="$t('admin.users')" name="users">
        <UserManagement />
      </el-tab-pane>
      <el-tab-pane :label="$t('admin.permissions')" name="permissions">
        <PermissionManagement />
      </el-tab-pane>
      <el-tab-pane :label="$t('admin.apiKeys')" name="apiKeys">
        <ApiKeyManagement />
      </el-tab-pane>
      <el-tab-pane :label="$t('review.title')" name="reviews">
        <ReviewManagement />
      </el-tab-pane>
      <el-tab-pane :label="$t('admin.rateLimits')" name="rateLimits">
        <RateLimitPanel />
      </el-tab-pane>
    </el-tabs>
  </div>
</template>

<script setup lang="ts">
import { ref } from 'vue'
import TenantManagement from './TenantManagement.vue'
import UserManagement from './UserManagement.vue'
import PermissionManagement from './PermissionManagement.vue'
import ApiKeyManagement from './ApiKeyManagement.vue'
import ReviewManagement from './ReviewManagement.vue'
import RateLimitPanel from './RateLimitPanel.vue'

const activeTab = ref('tenants')
</script>

<style scoped>
.admin-page {
  padding: 20px;
}
.admin-page h2 {
  margin: 0 0 16px;
}
</style>

