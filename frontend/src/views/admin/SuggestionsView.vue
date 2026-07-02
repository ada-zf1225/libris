<script setup lang="ts">
import { onMounted, ref } from 'vue'
import { useI18n } from 'vue-i18n'
import { ElMessage, ElMessageBox } from 'element-plus'
import { http } from '@/api/http'
import type { PageResponse } from '@/api/admin'
import { errorMessage } from '@/api/http'
import { dateTime } from '@/utils/format'

interface AdminSuggestion {
  id: number
  readerId: number
  readerName: string
  title: string
  author: string | null
  isbn: string | null
  reason: string | null
  status: 'PENDING' | 'APPROVED' | 'REJECTED'
  reply: string | null
  createdAt: string
}

const { t } = useI18n()

const rows = ref<AdminSuggestion[]>([])
const total = ref(0)
const page = ref(0)
const status = ref<string>('PENDING')
const loading = ref(false)

async function load() {
  loading.value = true
  try {
    const { data } = await http.get<PageResponse<AdminSuggestion>>('/api/admin/suggestions', {
      params: { status: status.value || undefined, page: page.value, size: 20 },
    })
    rows.value = data.content
    total.value = data.totalElements
  } catch (e) {
    ElMessage.error(errorMessage(e))
  } finally {
    loading.value = false
  }
}

onMounted(load)

async function handle(row: AdminSuggestion, approve: boolean) {
  let reply = ''
  try {
    const result = await ElMessageBox.prompt(row.title, approve ? '✓' : '✗', {
      inputPlaceholder: 'reply (optional)',
      inputValue: '',
    })
    reply = result.value ?? ''
  } catch {
    return
  }
  try {
    await http.post(`/api/admin/suggestions/${row.id}/${approve ? 'approve' : 'reject'}`, { reply })
    await load()
  } catch (e) {
    ElMessage.error(errorMessage(e))
  }
}
</script>

<template>
  <el-card shadow="never">
    <div class="toolbar">
      <el-radio-group v-model="status" @change="page = 0; load()">
        <el-radio-button value="PENDING">PENDING</el-radio-button>
        <el-radio-button value="APPROVED">APPROVED</el-radio-button>
        <el-radio-button value="REJECTED">REJECTED</el-radio-button>
        <el-radio-button value="">ALL</el-radio-button>
      </el-radio-group>
    </div>

    <el-table v-loading="loading" :data="rows">
      <el-table-column prop="title" :label="t('myLib.suggestForm.bookTitle')" min-width="180" />
      <el-table-column prop="author" :label="t('myLib.suggestForm.author')" width="130" />
      <el-table-column prop="isbn" label="ISBN" width="130" />
      <el-table-column prop="readerName" :label="t('logs.reader')" width="100" />
      <el-table-column prop="reason" :label="t('myLib.suggestForm.reason')" min-width="160" show-overflow-tooltip />
      <el-table-column width="140">
        <template #default="{ row }">{{ dateTime(row.createdAt) }}</template>
      </el-table-column>
      <el-table-column :label="t('table.status')" width="110">
        <template #default="{ row }">
          <el-tag
            size="small"
            :type="row.status === 'APPROVED' ? 'success' : row.status === 'REJECTED' ? 'danger' : 'info'"
          >
            {{ row.status }}
          </el-tag>
        </template>
      </el-table-column>
      <el-table-column :label="t('table.actions')" width="120" fixed="right">
        <template #default="{ row }">
          <template v-if="row.status === 'PENDING'">
            <el-button link type="success" size="small" @click="handle(row as AdminSuggestion, true)">✓</el-button>
            <el-button link type="danger" size="small" @click="handle(row as AdminSuggestion, false)">✗</el-button>
          </template>
        </template>
      </el-table-column>
    </el-table>

    <el-pagination
      class="pager"
      layout="total, prev, pager, next"
      :total="total"
      :page-size="20"
      :current-page="page + 1"
      @current-change="(p: number) => { page = p - 1; load() }"
    />
  </el-card>
</template>

<style scoped>
.toolbar {
  margin-bottom: 14px;
}

.pager {
  margin-top: 14px;
  justify-content: flex-end;
}
</style>
