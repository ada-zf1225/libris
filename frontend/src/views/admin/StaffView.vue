<script setup lang="ts">
import { onMounted, reactive, ref } from 'vue'
import { useI18n } from 'vue-i18n'
import { ElMessage, ElMessageBox, type FormInstance } from 'element-plus'
import { Plus } from '@element-plus/icons-vue'
import { http, errorMessage } from '@/api/http'
import type { PageResponse } from '@/api/admin'

interface StaffView {
  id: number
  username: string
  displayName: string
  email: string | null
  role: 'SUPER_ADMIN' | 'LIBRARIAN'
  status: 'ACTIVE' | 'BLOCKED' | 'DISABLED'
  mfaEnabled: boolean
  permissions: string[]
}

const ALL_PERMISSIONS = [
  'CIRCULATION',
  'MANAGE_CATALOG',
  'MANAGE_READERS',
  'MANAGE_POLICIES',
  'MANAGE_SUGGESTIONS',
  'VIEW_LOGS',
  'VIEW_STATS',
] as const

const { t } = useI18n()

const rows = ref<StaffView[]>([])
const loading = ref(false)

async function load() {
  loading.value = true
  try {
    const { data } = await http.get<PageResponse<StaffView>>('/api/admin/staff', { params: { size: 50 } })
    rows.value = data.content
  } catch (e) {
    ElMessage.error(errorMessage(e))
  } finally {
    loading.value = false
  }
}

onMounted(load)

const dialogVisible = ref(false)
const editingId = ref<number | null>(null)
const formRef = ref<FormInstance>()
const form = reactive({
  username: '',
  displayName: '',
  email: '',
  initialPassword: '',
  permissions: [] as string[],
})

function openCreate() {
  editingId.value = null
  Object.assign(form, {
    username: '',
    displayName: '',
    email: '',
    initialPassword: '',
    permissions: ['CIRCULATION', 'MANAGE_CATALOG', 'MANAGE_READERS', 'MANAGE_SUGGESTIONS', 'VIEW_STATS'],
  })
  dialogVisible.value = true
}

function openEdit(row: StaffView) {
  if (row.role === 'SUPER_ADMIN') return
  editingId.value = row.id
  Object.assign(form, {
    username: row.username,
    displayName: row.displayName,
    email: row.email ?? '',
    initialPassword: '',
    permissions: [...row.permissions],
  })
  dialogVisible.value = true
}

async function submit() {
  const valid = await formRef.value?.validate().catch(() => false)
  if (!valid) return
  try {
    if (editingId.value == null) {
      await http.post('/api/admin/staff', form)
    } else {
      await http.put(`/api/admin/staff/${editingId.value}`, form)
    }
    dialogVisible.value = false
    await load()
  } catch (e) {
    ElMessage.error(errorMessage(e))
  }
}

async function toggle(row: StaffView) {
  const action = row.status === 'DISABLED' ? 'enable' : 'disable'
  if (action === 'disable') {
    try {
      await ElMessageBox.confirm(row.displayName, t('staff.disableConfirm'), { type: 'warning' })
    } catch {
      return
    }
  }
  await http.post(`/api/admin/staff/${row.id}/${action}`).catch((e) => ElMessage.error(errorMessage(e)))
  await load()
}
</script>

<template>
  <el-card shadow="never">
    <div class="toolbar">
      <h3 class="title">{{ t('nav.admin.staff') }}</h3>
      <el-button type="primary" :icon="Plus" @click="openCreate">{{ t('staff.add') }}</el-button>
    </div>

    <el-table v-loading="loading" :data="rows">
      <el-table-column prop="displayName" :label="t('readers.fields.displayName')" width="130" />
      <el-table-column prop="username" :label="t('readers.fields.username')" width="130" />
      <el-table-column :label="t('staff.role')" width="130">
        <template #default="{ row }">
          <el-tag :type="row.role === 'SUPER_ADMIN' ? 'danger' : 'primary'" size="small" effect="plain">
            {{ t(`staff.roles.${row.role}`) }}
          </el-tag>
        </template>
      </el-table-column>
      <el-table-column :label="t('staff.permissions')" min-width="300">
        <template #default="{ row }">
          <template v-if="row.role === 'SUPER_ADMIN'">
            <el-tag size="small" type="info" effect="plain">{{ t('staff.allPermissions') }}</el-tag>
          </template>
          <template v-else>
            <el-tag v-for="p in row.permissions" :key="p" size="small" effect="plain" class="perm-tag">
              {{ t(`staff.perms.${p}`) }}
            </el-tag>
          </template>
        </template>
      </el-table-column>
      <el-table-column width="80">
        <template #default="{ row }">
          <el-tag v-if="row.mfaEnabled" size="small" type="success" effect="plain">MFA</el-tag>
        </template>
      </el-table-column>
      <el-table-column :label="t('table.status')" width="90">
        <template #default="{ row }">
          <el-tag size="small" :type="row.status === 'ACTIVE' ? 'success' : 'danger'">
            {{ row.status === 'ACTIVE' ? t('desk.active') : t('staff.disabled') }}
          </el-tag>
        </template>
      </el-table-column>
      <el-table-column :label="t('table.actions')" width="140" fixed="right">
        <template #default="{ row }">
          <template v-if="row.role !== 'SUPER_ADMIN'">
            <el-button link type="primary" size="small" @click="openEdit(row as StaffView)">
              {{ t('books.edit') }}
            </el-button>
            <el-button
              link
              :type="row.status === 'DISABLED' ? 'success' : 'danger'"
              size="small"
              @click="toggle(row as StaffView)"
            >
              {{ row.status === 'DISABLED' ? t('staff.enable') : t('staff.disable') }}
            </el-button>
          </template>
        </template>
      </el-table-column>
    </el-table>
  </el-card>

  <el-dialog
    v-model="dialogVisible"
    :title="editingId == null ? t('staff.add') : t('books.edit')"
    width="560"
  >
    <el-form ref="formRef" :model="form" label-width="100px">
      <el-row :gutter="12">
        <el-col :span="12">
          <el-form-item
            :label="t('readers.fields.username')"
            prop="username"
            :rules="{ required: true, message: t('form.required') }"
          >
            <el-input v-model="form.username" :disabled="editingId != null" />
          </el-form-item>
        </el-col>
        <el-col :span="12">
          <el-form-item
            :label="t('readers.fields.displayName')"
            prop="displayName"
            :rules="{ required: true, message: t('form.required') }"
          >
            <el-input v-model="form.displayName" />
          </el-form-item>
        </el-col>
      </el-row>
      <el-row :gutter="12">
        <el-col :span="12">
          <el-form-item :label="t('readers.fields.email')">
            <el-input v-model="form.email" />
          </el-form-item>
        </el-col>
        <el-col :span="12">
          <el-form-item
            :label="t('staff.password')"
            prop="initialPassword"
            :rules="editingId == null ? [{ required: true, message: t('form.required') }, { min: 8, message: t('form.required') }] : []"
          >
            <el-input
              v-model="form.initialPassword"
              show-password
              :placeholder="editingId == null ? '' : t('staff.passwordKeep')"
            />
          </el-form-item>
        </el-col>
      </el-row>
      <el-form-item :label="t('staff.permissions')">
        <el-checkbox-group v-model="form.permissions">
          <el-checkbox v-for="p in ALL_PERMISSIONS" :key="p" :value="p">
            {{ t(`staff.perms.${p}`) }}
          </el-checkbox>
        </el-checkbox-group>
      </el-form-item>
    </el-form>
    <template #footer>
      <el-button @click="dialogVisible = false">{{ t('common.cancel') }}</el-button>
      <el-button type="primary" @click="submit">
        {{ editingId == null ? t('form.create') : t('form.save') }}
      </el-button>
    </template>
  </el-dialog>
</template>

<style scoped>
.toolbar {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 14px;
}

.title {
  margin: 0;
}

.perm-tag {
  margin: 2px 4px 2px 0;
}
</style>
