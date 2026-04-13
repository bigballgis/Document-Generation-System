<template>
  <el-dialog
    :model-value="visible"
    :title="isEdit ? $t('dataSource.edit') : $t('dataSource.create')"
    width="720px"
    destroy-on-close
    @update:model-value="$emit('update:visible', $event)"
  >
    <el-form
      ref="formRef"
      :model="form"
      :rules="rules"
      label-width="140px"
      label-position="right"
    >
      <!-- Basic Info -->
      <el-form-item :label="$t('dataSource.name')" prop="name">
        <el-input v-model="form.name" />
      </el-form-item>

      <el-form-item :label="$t('dataSource.type')" prop="type">
        <el-select v-model="form.type" :disabled="isEdit" style="width: 100%" @change="onTypeChange">
          <el-option value="HTTP_API" :label="$t('dataSource.typeHttpApi')" />
          <el-option value="DATABASE" :label="$t('dataSource.typeDatabase')" />
          <el-option value="INTERNAL_SYSTEM" :label="$t('dataSource.typeInternal')" />
        </el-select>
      </el-form-item>

      <el-form-item :label="$t('dataSource.priority')" prop="priority">
        <el-input-number v-model="form.priority" :min="0" :max="100" />
      </el-form-item>

      <el-divider />

      <!-- HTTP API Config -->
      <template v-if="form.type === 'HTTP_API'">
        <el-form-item :label="$t('dataSource.url')" prop="httpConfig.url">
          <el-input v-model="form.httpConfig.url" placeholder="https://api.example.com/data" />
        </el-form-item>
        <el-form-item :label="$t('dataSource.method')">
          <el-select v-model="form.httpConfig.method" style="width: 100%">
            <el-option value="GET" label="GET" />
            <el-option value="POST" label="POST" />
            <el-option value="PUT" label="PUT" />
            <el-option value="DELETE" label="DELETE" />
          </el-select>
        </el-form-item>
        <el-form-item :label="$t('dataSource.authType')">
          <el-select v-model="form.httpConfig.authType" style="width: 100%">
            <el-option value="NONE" :label="$t('dataSource.authNone')" />
            <el-option value="API_KEY" :label="$t('dataSource.authApiKey')" />
            <el-option value="OAUTH" :label="$t('dataSource.authOAuth')" />
            <el-option value="BASIC" :label="$t('dataSource.authBasic')" />
          </el-select>
        </el-form-item>
        <!-- Auth config fields based on authType -->
        <template v-if="form.httpConfig.authType === 'BASIC'">
          <el-form-item :label="$t('dataSource.dbUsername')">
            <el-input v-model="form.httpConfig.authConfig.username" />
          </el-form-item>
          <el-form-item :label="$t('dataSource.dbPassword')">
            <el-input
              v-model="form.httpConfig.authConfig.password"
              type="password"
              show-password
              :placeholder="isEdit ? $t('dataSource.sensitiveHidden') : ''"
            />
          </el-form-item>
        </template>
        <template v-if="form.httpConfig.authType === 'API_KEY'">
          <el-form-item label="API Key">
            <el-input
              v-model="form.httpConfig.authConfig.apiKey"
              type="password"
              show-password
              :placeholder="isEdit ? $t('dataSource.sensitiveHidden') : ''"
            />
          </el-form-item>
        </template>
        <template v-if="form.httpConfig.authType === 'OAUTH'">
          <el-form-item label="Token">
            <el-input
              v-model="form.httpConfig.authConfig.token"
              type="password"
              show-password
              :placeholder="isEdit ? $t('dataSource.sensitiveHidden') : ''"
            />
          </el-form-item>
        </template>
        <el-form-item :label="$t('dataSource.timeout')">
          <el-input-number v-model="form.httpConfig.timeout" :min="1000" :max="60000" :step="1000" />
        </el-form-item>
        <el-form-item :label="$t('dataSource.retryEnabled')">
          <el-switch v-model="form.httpConfig.retryEnabled" />
        </el-form-item>
        <template v-if="form.httpConfig.retryEnabled">
          <el-form-item :label="$t('dataSource.retryCount')">
            <el-input-number v-model="form.httpConfig.retryCount" :min="1" :max="5" />
          </el-form-item>
          <el-form-item :label="$t('dataSource.retryInterval')">
            <el-input-number v-model="form.httpConfig.retryInterval" :min="100" :max="10000" :step="100" />
          </el-form-item>
          <el-form-item :label="$t('dataSource.retryBackoff')">
            <el-switch v-model="form.httpConfig.retryBackoff" />
          </el-form-item>
        </template>

        <!-- Headers key-value editor -->
        <el-form-item :label="$t('dataSource.headers')">
          <KeyValueEditor v-model="form.httpConfig.headers" />
        </el-form-item>

        <!-- Response Transform Rules -->
        <el-divider>{{ $t('dataSource.responseTransform') }}</el-divider>
        <TransformRulesEditor v-model="form.httpConfig.responseTransformRules" />
      </template>

      <!-- Database Config -->
      <template v-if="form.type === 'DATABASE'">
        <el-form-item :label="$t('dataSource.dbType')" prop="dbConfig.dbType">
          <el-select v-model="form.dbConfig.dbType" style="width: 100%">
            <el-option value="POSTGRESQL" label="PostgreSQL" />
            <el-option value="MYSQL" label="MySQL" />
            <el-option value="SQLSERVER" label="SQL Server" />
            <el-option value="ORACLE" label="Oracle" />
          </el-select>
        </el-form-item>
        <el-form-item :label="$t('dataSource.dbHost')" prop="dbConfig.host">
          <el-input v-model="form.dbConfig.host" placeholder="localhost" />
        </el-form-item>
        <el-form-item :label="$t('dataSource.dbPort')" prop="dbConfig.port">
          <el-input-number v-model="form.dbConfig.port" :min="1" :max="65535" />
        </el-form-item>
        <el-form-item :label="$t('dataSource.dbName')" prop="dbConfig.dbName">
          <el-input v-model="form.dbConfig.dbName" />
        </el-form-item>
        <el-form-item :label="$t('dataSource.dbUsername')">
          <el-input v-model="form.dbConfig.username" />
        </el-form-item>
        <el-form-item :label="$t('dataSource.dbPassword')">
          <el-input
            v-model="form.dbConfig.password"
            type="password"
            show-password
            :placeholder="isEdit ? $t('dataSource.sensitiveHidden') : ''"
          />
        </el-form-item>
        <el-form-item :label="$t('dataSource.sqlQuery')">
          <el-input
            v-model="form.dbConfig.sqlQuery"
            type="textarea"
            :rows="4"
            placeholder="SELECT * FROM users WHERE id = :userId"
          />
        </el-form-item>
      </template>

      <!-- Internal System Config -->
      <template v-if="form.type === 'INTERNAL_SYSTEM'">
        <el-form-item :label="$t('dataSource.serviceName')" prop="internalConfig.serviceName">
          <el-input v-model="form.internalConfig.serviceName" placeholder="user-service" />
        </el-form-item>
        <el-form-item :label="$t('dataSource.serviceUrl')" prop="internalConfig.serviceUrl">
          <el-input v-model="form.internalConfig.serviceUrl" placeholder="http://user-service:8080/api/users" />
        </el-form-item>
        <el-form-item :label="$t('dataSource.authType')">
          <el-select v-model="form.internalConfig.authType" style="width: 100%">
            <el-option value="NONE" :label="$t('dataSource.authNone')" />
            <el-option value="API_KEY" :label="$t('dataSource.authApiKey')" />
            <el-option value="OAUTH" :label="$t('dataSource.authOAuth')" />
            <el-option value="BASIC" :label="$t('dataSource.authBasic')" />
          </el-select>
        </el-form-item>
        <template v-if="form.internalConfig.authType === 'API_KEY'">
          <el-form-item label="API Key">
            <el-input
              v-model="form.internalConfig.authConfig.apiKey"
              type="password"
              show-password
              :placeholder="isEdit ? $t('dataSource.sensitiveHidden') : ''"
            />
          </el-form-item>
        </template>
        <el-form-item :label="$t('dataSource.timeout')">
          <el-input-number v-model="form.internalConfig.timeout" :min="1000" :max="60000" :step="1000" />
        </el-form-item>
        <el-form-item :label="$t('dataSource.retryEnabled')">
          <el-switch v-model="form.internalConfig.retryEnabled" />
        </el-form-item>
        <template v-if="form.internalConfig.retryEnabled">
          <el-form-item :label="$t('dataSource.retryCount')">
            <el-input-number v-model="form.internalConfig.retryCount" :min="1" :max="5" />
          </el-form-item>
          <el-form-item :label="$t('dataSource.retryInterval')">
            <el-input-number v-model="form.internalConfig.retryInterval" :min="100" :max="10000" :step="100" />
          </el-form-item>
        </template>
      </template>

      <el-divider />

      <!-- Cache Settings -->
      <el-form-item :label="$t('dataSource.cacheEnabled')">
        <el-switch v-model="form.cacheEnabled" />
      </el-form-item>
      <el-form-item v-if="form.cacheEnabled" :label="$t('dataSource.cacheTtl')">
        <el-input-number v-model="form.cacheTtl" :min="1" :max="86400" />
      </el-form-item>
    </el-form>

    <template #footer>
      <el-button @click="$emit('update:visible', false)">{{ $t('common.cancel') }}</el-button>
      <el-button type="primary" :loading="saving" @click="handleSave">{{ $t('common.save') }}</el-button>
    </template>
  </el-dialog>
</template>

<script setup lang="ts">
import { ref, reactive, computed, watch } from 'vue'
import { useI18n } from 'vue-i18n'
import { ElMessage } from 'element-plus'
import type { FormInstance, FormRules } from 'element-plus'
import {
  createDataSource,
  updateDataSource,
  type DataSourceDTO,
  type DataSourceType,
  type HttpApiConfig,
  type DatabaseConfig,
  type InternalSystemConfig,
} from '@/api/data-sources'
import KeyValueEditor from './KeyValueEditor.vue'
import TransformRulesEditor from './TransformRulesEditor.vue'

const props = defineProps<{
  visible: boolean
  dataSource: DataSourceDTO | null
  templateId: number
}>()

const emit = defineEmits<{
  'update:visible': [val: boolean]
  saved: []
}>()

const { t } = useI18n()
const formRef = ref<FormInstance>()
const saving = ref(false)

const isEdit = computed(() => !!props.dataSource)

function defaultHttpConfig(): HttpApiConfig {
  return {
    url: '',
    method: 'GET',
    headers: {},
    params: {},
    authType: 'NONE',
    authConfig: {},
    timeout: 5000,
    retryEnabled: false,
    retryCount: 3,
    retryInterval: 1000,
    retryBackoff: true,
    responseTransformRules: [],
  }
}

function defaultDbConfig(): DatabaseConfig {
  return {
    dbType: 'POSTGRESQL',
    host: '',
    port: 5432,
    dbName: '',
    username: '',
    password: '',
    sqlQuery: '',
  }
}

function defaultInternalConfig(): InternalSystemConfig {
  return {
    serviceName: '',
    serviceUrl: '',
    authType: 'NONE',
    authConfig: {},
    timeout: 5000,
    retryEnabled: false,
    retryCount: 3,
    retryInterval: 1000,
  }
}

const form = reactive({
  name: '',
  type: 'HTTP_API' as DataSourceType,
  priority: 0,
  cacheEnabled: false,
  cacheTtl: 300,
  httpConfig: defaultHttpConfig(),
  dbConfig: defaultDbConfig(),
  internalConfig: defaultInternalConfig(),
})

const rules: FormRules = {
  name: [{ required: true, message: () => t('validation.required', { field: t('dataSource.name') }), trigger: 'blur' }],
  type: [{ required: true, message: () => t('validation.required', { field: t('dataSource.type') }), trigger: 'change' }],
}

/** Mask sensitive values: show first 4 and last 4 chars with asterisks in between, or all asterisks if short */
function maskSensitive(val: string | undefined): string {
  if (!val) return ''
  if (val.length >= 8) return val.substring(0, 4) + '****' + val.substring(val.length - 4)
  return '********'
}

function parseConfigForEdit(ds: DataSourceDTO) {
  try {
    const config = JSON.parse(ds.configJson)
    if (ds.type === 'HTTP_API') {
      form.httpConfig = { ...defaultHttpConfig(), ...config }
      // Mask sensitive auth fields for display
      if (form.httpConfig.authConfig?.password) {
        form.httpConfig.authConfig.password = maskSensitive(form.httpConfig.authConfig.password)
      }
      if (form.httpConfig.authConfig?.apiKey) {
        form.httpConfig.authConfig.apiKey = maskSensitive(form.httpConfig.authConfig.apiKey)
      }
      if (form.httpConfig.authConfig?.token) {
        form.httpConfig.authConfig.token = maskSensitive(form.httpConfig.authConfig.token)
      }
    } else if (ds.type === 'DATABASE') {
      form.dbConfig = { ...defaultDbConfig(), ...config }
      form.dbConfig.password = maskSensitive(form.dbConfig.password)
    } else if (ds.type === 'INTERNAL_SYSTEM') {
      form.internalConfig = { ...defaultInternalConfig(), ...config }
      if (form.internalConfig.authConfig?.apiKey) {
        form.internalConfig.authConfig.apiKey = maskSensitive(form.internalConfig.authConfig.apiKey)
      }
    }
  } catch {
    // If configJson is not valid JSON, leave defaults
  }
}

watch(
  () => props.visible,
  (val) => {
    if (val) {
      if (props.dataSource) {
        form.name = props.dataSource.name
        form.type = props.dataSource.type
        form.priority = props.dataSource.priority
        form.cacheEnabled = props.dataSource.cacheEnabled
        form.cacheTtl = props.dataSource.cacheTtl ?? 300
        parseConfigForEdit(props.dataSource)
      } else {
        // Reset form for create
        form.name = ''
        form.type = 'HTTP_API'
        form.priority = 0
        form.cacheEnabled = false
        form.cacheTtl = 300
        form.httpConfig = defaultHttpConfig()
        form.dbConfig = defaultDbConfig()
        form.internalConfig = defaultInternalConfig()
      }
    }
  },
)

function onTypeChange() {
  form.httpConfig = defaultHttpConfig()
  form.dbConfig = defaultDbConfig()
  form.internalConfig = defaultInternalConfig()
}

function buildConfigJson(): string {
  if (form.type === 'HTTP_API') return JSON.stringify(form.httpConfig)
  if (form.type === 'DATABASE') return JSON.stringify(form.dbConfig)
  if (form.type === 'INTERNAL_SYSTEM') return JSON.stringify(form.internalConfig)
  return '{}'
}

async function handleSave() {
  const valid = await formRef.value?.validate().catch(() => false)
  if (!valid) return

  saving.value = true
  try {
    const payload = {
      name: form.name,
      type: form.type,
      configJson: buildConfigJson(),
      cacheEnabled: form.cacheEnabled,
      cacheTtl: form.cacheEnabled ? form.cacheTtl : undefined,
      priority: form.priority,
    }

    if (isEdit.value && props.dataSource) {
      await updateDataSource(props.dataSource.id, payload)
      ElMessage.success(t('message.updateSuccess'))
    } else {
      await createDataSource(props.templateId, payload)
      ElMessage.success(t('message.createSuccess'))
    }
    emit('update:visible', false)
    emit('saved')
  } catch {
    // error handled by interceptor
  } finally {
    saving.value = false
  }
}
</script>
