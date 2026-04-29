import { createRouter, createWebHistory } from 'vue-router'
import type { RouteRecordRaw } from 'vue-router'
import type { UserRole } from '@/stores/user'

declare module 'vue-router' {
  interface RouteMeta {
    requiresAuth?: boolean
    roles?: UserRole[]
    title?: string
  }
}

const routes: RouteRecordRaw[] = [
  {
    path: '/login',
    name: 'Login',
    component: () => import('@/views/auth/LoginView.vue'),
    meta: { requiresAuth: false, title: 'Login' },
  },
  {
    path: '/register',
    name: 'Register',
    component: () => import('@/views/auth/RegisterView.vue'),
    meta: { requiresAuth: false, title: 'Register' },
  },
  {
    path: '/',
    component: () => import('@/layouts/MainLayout.vue'),
    meta: { requiresAuth: true },
    children: [
      {
        path: '',
        redirect: '/dashboard',
      },
      {
        path: 'dashboard',
        name: 'Dashboard',
        component: () => import('@/views/dashboard/Index.vue'),
        meta: { title: 'Dashboard' },
      },
      {
        path: 'templates',
        name: 'Templates',
        component: () => import('@/views/templates/Index.vue'),
        meta: { title: 'Templates' },
      },
      {
        path: 'templates/:id/workspace',
        name: 'TemplateWorkspace',
        component: () => import('@/views/template-workspace/Index.vue'),
        meta: { title: 'Template Workspace' },
      },
      {
        path: 'templates/:id/api',
        name: 'TemplateApiManagement',
        component: () => import('@/views/template-api/Index.vue'),
        meta: { title: 'API Management', requiresAuth: true },
      },
      {
        path: 'templates/:id/editor',
        name: 'TemplateEditor',
        component: () => import('@/views/templates/Editor.vue'),
        meta: { title: 'Template Editor' },
      },
      {
        path: 'templates/:id/integrations',
        name: 'TemplateIntegrations',
        component: () => import('@/views/templates/Integrations.vue'),
        meta: { title: 'Template Integrations', requiresAuth: true },
      },
      {
        path: 'templates/:id',
        name: 'TemplateDetail',
        redirect: (to) => `/templates/${to.params.id}/workspace`,
      },
      {
        path: 'generation',
        name: 'Generation',
        component: () => import('@/views/generation/Index.vue'),
        meta: { title: 'Document generation' },
      },
      {
        path: 'market',
        redirect: '/generation',
      },
      {
        path: 'admin',
        name: 'Admin',
        component: () => import('@/views/admin/Index.vue'),
        meta: { title: 'Administration', roles: ['SUPER_ADMIN', 'TENANT_ADMIN'] },
      },
      {
        path: 'audit',
        name: 'Audit',
        component: () => import('@/views/audit/Index.vue'),
        meta: { title: 'Audit Logs', roles: ['SUPER_ADMIN', 'TENANT_ADMIN', 'TEAM_ADMIN'] },
      },
      {
        path: 'documents',
        name: 'Documents',
        component: () => import('@/views/documents/Index.vue'),
        meta: { title: 'Documents', requiresAuth: true },
      },
      {
        path: 'tasks',
        redirect: () => ({ path: '/generation', query: { tab: 'queue' } }),
      },
      {
        path: 'components',
        name: 'Components',
        component: () => import('@/views/components/Index.vue'),
        meta: { title: 'Component Templates' },
      },
      {
        path: 'composite-templates',
        name: 'CompositeTemplates',
        component: () => import('@/views/composite-templates/Index.vue'),
        meta: { title: 'Composite Templates' },
      },
      {
        path: 'composite-templates/:id/editor',
        name: 'AssemblyEditor',
        component: () => import('@/views/composite-templates/AssemblyEditor.vue'),
        meta: { title: 'Assembly Editor' },
      },
      {
        path: 'composite-templates/:id',
        name: 'CompositeTemplateDetail',
        redirect: (to) => `/templates/${to.params.id}/workspace`,
      },
    ],
  },
]

const router = createRouter({
  history: createWebHistory(),
  routes,
})

// Navigation guard
router.beforeEach((to, _from, next) => {
  const token = localStorage.getItem('access_token')

  // Public routes
  if (to.meta.requiresAuth === false) {
    // Redirect to dashboard if already logged in
    if (token && (to.name === 'Login' || to.name === 'Register')) {
      next({ path: '/dashboard' })
      return
    }
    next()
    return
  }

  // Protected routes — require auth
  if (!token) {
    next({ name: 'Login', query: { redirect: to.fullPath } })
    return
  }

  // Role-based access control
  const requiredRoles = to.meta.roles
  if (requiredRoles && requiredRoles.length > 0) {
    let userRole: string | null = null
    try {
      const payload = JSON.parse(atob(token.split('.')[1]))
      userRole = payload.role
    } catch {
      // Invalid token
    }
    if (!userRole || !requiredRoles.includes(userRole as UserRole)) {
      next({ path: '/dashboard' })
      return
    }
  }

  next()
})

export default router
