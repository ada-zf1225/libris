<script setup lang="ts">
import { onMounted, reactive, ref } from 'vue'
import { useI18n } from 'vue-i18n'
import { ElMessage, ElMessageBox, type FormInstance } from 'element-plus'
import { Search, Plus } from '@element-plus/icons-vue'
import { adminReaders, type ReaderView, type ReaderInput } from '@/api/admin'
import { errorMessage } from '@/api/http'

const { t } = useI18n()

const rows = ref<ReaderView[]>([])
const total = ref(0)
const page = ref(0)
const size = ref(20)
const q = ref('')
const loading = ref(false)

async function load() {
  loading.value = true
  try {
    const result = await adminReaders.list({ q: q.value || undefined, page: page.value, size: size.value })
    rows.value = result.content
    total.value = result.totalElements
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
const form = reactive<ReaderInput>({
  username: '',
  displayName: '',
  email: '',
  phone: '',
  readerType: 'STUDENT',
  sex: '男',
  birth: null,
  address: '',
  initialPassword: '',
})

function openCreate() {
  editingId.value = null
  Object.assign(form, {
    username: '',
    displayName: '',
    email: '',
    phone: '',
    readerType: 'STUDENT',
    sex: '男',
    birth: null,
    address: '',
    initialPassword: '',
  })
  dialogVisible.value = true
}

function openEdit(row: ReaderView) {
  editingId.value = row.id
  Object.assign(form, {
    username: row.username,
    displayName: row.displayName,
    email: row.email ?? '',
    phone: row.phone ?? '',
    readerType: row.readerType ?? 'STUDENT',
    sex: row.sex ?? '',
    birth: row.birth,
    address: row.address ?? '',
    initialPassword: '',
  })
  dialogVisible.value = true
}

async function submit() {
  const valid = await formRef.value?.validate().catch(() => false)
  if (!valid) return
  try {
    if (editingId.value == null) {
      await adminReaders.create({ ...form })
    } else {
      await adminReaders.update(editingId.value, { ...form, initialPassword: undefined })
    }
    dialogVisible.value = false
    await load()
  } catch (e) {
    ElMessage.error(errorMessage(e))
  }
}

async function toggleBlock(row: ReaderView) {
  if (row.status === 'ACTIVE') {
    try {
      await ElMessageBox.confirm(
        t('readers.blockConfirm', { name: row.displayName }),
        t('readers.block'),
        { type: 'warning' },
      )
    } catch {
      return
    }
    await adminReaders.block(row.id).catch((e) => ElMessage.error(errorMessage(e)))
  } else {
    await adminReaders.unblock(row.id).catch((e) => ElMessage.error(errorMessage(e)))
  }
  await load()
}
</script>

<template>
  <el-card shadow="never">
    <div class="toolbar">
      <el-input
        v-model="q"
        :placeholder="t('readers.searchPlaceholder')"
        :prefix-icon="Search"
        clearable
        class="search-input"
        @keyup.enter="page = 0; load()"
        @clear="page = 0; load()"
      />
      <div class="spacer" />
      <el-button type="primary" :icon="Plus" @click="openCreate">{{ t('readers.add') }}</el-button>
    </div>

    <el-table v-loading="loading" :data="rows">
      <el-table-column prop="id" label="#" width="80" />
      <el-table-column prop="displayName" :label="t('readers.fields.displayName')" min-width="110" />
      <el-table-column prop="username" :label="t('readers.fields.username')" min-width="120" />
      <el-table-column :label="t('readers.fields.readerType')" width="100">
        <template #default="{ row }">
          <el-tag v-if="row.readerType" size="small" effect="plain">
            {{ t(`readers.types.${row.readerType}`) }}
          </el-tag>
        </template>
      </el-table-column>
      <el-table-column prop="email" :label="t('readers.fields.email')" min-width="180" show-overflow-tooltip />
      <el-table-column prop="phone" :label="t('readers.fields.phone')" width="130" />
      <el-table-column :label="t('table.status')" width="90">
        <template #default="{ row }">
          <el-tag size="small" :type="row.status === 'BLOCKED' ? 'danger' : 'success'">
            {{ row.status === 'BLOCKED' ? t('desk.blocked') : t('desk.active') }}
          </el-tag>
        </template>
      </el-table-column>
      <el-table-column :label="t('table.actions')" width="150" fixed="right">
        <template #default="{ row }">
          <el-button link type="primary" size="small" @click="openEdit(row as ReaderView)">
            {{ t('books.edit') }}
          </el-button>
          <el-button
            link
            :type="row.status === 'ACTIVE' ? 'danger' : 'success'"
            size="small"
            @click="toggleBlock(row as ReaderView)"
          >
            {{ row.status === 'ACTIVE' ? t('readers.block') : t('readers.unblock') }}
          </el-button>
        </template>
      </el-table-column>
    </el-table>

    <el-pagination
      class="pager"
      layout="total, prev, pager, next"
      :total="total"
      :page-size="size"
      :current-page="page + 1"
      @current-change="(p: number) => { page = p - 1; load() }"
    />
  </el-card>

  <el-dialog
    v-model="dialogVisible"
    :title="editingId == null ? t('readers.add') : t('books.edit')"
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
          <el-form-item :label="t('readers.fields.readerType')">
            <el-select v-model="form.readerType">
              <el-option value="TEACHER" :label="t('readers.types.TEACHER')" />
              <el-option value="STUDENT" :label="t('readers.types.STUDENT')" />
            </el-select>
          </el-form-item>
        </el-col>
        <el-col :span="12">
          <el-form-item :label="t('readers.fields.sex')">
            <el-select v-model="form.sex">
              <el-option value="男" label="男" />
              <el-option value="女" label="女" />
            </el-select>
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
          <el-form-item :label="t('readers.fields.phone')">
            <el-input v-model="form.phone" />
          </el-form-item>
        </el-col>
      </el-row>
      <el-row :gutter="12">
        <el-col :span="12">
          <el-form-item :label="t('readers.fields.birth')">
            <el-date-picker v-model="form.birth" type="date" value-format="YYYY-MM-DD" style="width: 100%" />
          </el-form-item>
        </el-col>
        <el-col :span="12">
          <el-form-item :label="t('readers.fields.address')">
            <el-input v-model="form.address" />
          </el-form-item>
        </el-col>
      </el-row>
      <el-form-item
        v-if="editingId == null"
        :label="t('readers.fields.initialPassword')"
        prop="initialPassword"
        :rules="[
          { required: true, message: t('form.required') },
          { min: 8, message: t('form.required') },
        ]"
      >
        <el-input v-model="form.initialPassword" show-password />
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
  gap: 10px;
  margin-bottom: 14px;
}

.search-input {
  width: 260px;
}

.spacer {
  flex: 1;
}

.pager {
  margin-top: 14px;
  justify-content: flex-end;
}
</style>
