<script setup lang="ts">
import { onMounted, ref } from 'vue'
import { useI18n } from 'vue-i18n'
import { ElMessage } from 'element-plus'
import { adminLogs, type LogView } from '@/api/admin'
import { errorMessage } from '@/api/http'
import { dateTime, money } from '@/utils/format'

const { t } = useI18n()

const rows = ref<LogView[]>([])
const total = ref(0)
const page = ref(0)
const size = ref(20)
const loading = ref(false)

async function load() {
  loading.value = true
  try {
    const result = await adminLogs.list({ page: page.value, size: size.value })
    rows.value = result.content
    total.value = result.totalElements
  } catch (e) {
    ElMessage.error(errorMessage(e))
  } finally {
    loading.value = false
  }
}

onMounted(load)

const actionTag = (action: string) =>
  ((
    {
      CHECKOUT: 'primary',
      CHECKIN: 'success',
      RENEW: 'info',
      MARK_LOST: 'danger',
      FINE_PAID: 'warning',
      FINE_WAIVED: 'warning',
    }
  )[action] ?? 'info') as 'primary' | 'success' | 'info' | 'danger' | 'warning'

function describe(row: LogView): string {
  const d = row.detail ?? {}
  const parts: string[] = []
  if (typeof d.title === 'string') parts.push(`《${d.title}》`)
  if (typeof d.barcode === 'string') parts.push(String(d.barcode))
  if (typeof d.fineCents === 'number' && d.fineCents > 0) parts.push(money(d.fineCents))
  if (typeof d.amountCents === 'number') parts.push(money(d.amountCents))
  if (typeof d.routing === 'string' && d.routing === 'TO_HOLD_SHELF') parts.push('→ 预约架')
  return parts.join(' · ')
}
</script>

<template>
  <el-card shadow="never">
    <el-table v-loading="loading" :data="rows">
      <el-table-column :label="t('logs.time')" width="160">
        <template #default="{ row }">{{ dateTime(row.createdAt) }}</template>
      </el-table-column>
      <el-table-column :label="t('logs.action')" width="110">
        <template #default="{ row }">
          <el-tag size="small" :type="actionTag(row.action)">
            {{ t(`logs.actions.${row.action}`) }}
          </el-tag>
        </template>
      </el-table-column>
      <el-table-column :label="t('logs.operator')" width="90">
        <template #default="{ row }">#{{ row.operatorId ?? '—' }}</template>
      </el-table-column>
      <el-table-column :label="t('logs.reader')" width="90">
        <template #default="{ row }">{{ row.readerId ? `#${row.readerId}` : '—' }}</template>
      </el-table-column>
      <el-table-column :label="t('logs.detail')" min-width="300">
        <template #default="{ row }">{{ describe(row as LogView) }}</template>
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
</template>

<style scoped>
.pager {
  margin-top: 14px;
  justify-content: flex-end;
}
</style>
