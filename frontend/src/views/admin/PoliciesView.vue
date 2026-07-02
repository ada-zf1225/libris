<script setup lang="ts">
import { onMounted, ref } from 'vue'
import { useI18n } from 'vue-i18n'
import { ElMessage } from 'element-plus'
import { adminPolicies, type PolicyView } from '@/api/admin'
import { errorMessage } from '@/api/http'

const { t } = useI18n()

const rows = ref<PolicyView[]>([])
const loading = ref(false)

onMounted(async () => {
  loading.value = true
  try {
    rows.value = await adminPolicies.list()
  } catch (e) {
    ElMessage.error(errorMessage(e))
  } finally {
    loading.value = false
  }
})

async function save(row: PolicyView) {
  try {
    await adminPolicies.update(row.id, {
      loanDays: row.loanDays,
      maxLoans: row.maxLoans,
      maxRenewals: row.maxRenewals,
      dailyFineCents: row.dailyFineCents,
      blockOverdueCount: row.blockOverdueCount,
      blockFineCents: row.blockFineCents,
    })
    ElMessage.success(t('policies.saved'))
  } catch (e) {
    ElMessage.error(errorMessage(e))
  }
}
</script>

<template>
  <el-card shadow="never">
    <h3>{{ t('policies.title') }}</h3>
    <el-table v-loading="loading" :data="rows">
      <el-table-column :label="t('readers.fields.readerType')" width="110">
        <template #default="{ row }">
          <el-tag effect="plain">{{ t(`readers.types.${row.readerType}`) }}</el-tag>
        </template>
      </el-table-column>
      <el-table-column :label="t('policies.loanDays')" width="150">
        <template #default="{ row }">
          <el-input-number v-model="row.loanDays" :min="1" :max="365" size="small" />
        </template>
      </el-table-column>
      <el-table-column :label="t('policies.maxLoans')" width="150">
        <template #default="{ row }">
          <el-input-number v-model="row.maxLoans" :min="1" :max="200" size="small" />
        </template>
      </el-table-column>
      <el-table-column :label="t('policies.maxRenewals')" width="150">
        <template #default="{ row }">
          <el-input-number v-model="row.maxRenewals" :min="0" :max="10" size="small" />
        </template>
      </el-table-column>
      <el-table-column :label="`${t('policies.dailyFine')}(分)`" width="150">
        <template #default="{ row }">
          <el-input-number v-model="row.dailyFineCents" :min="0" :max="10000" size="small" />
        </template>
      </el-table-column>
      <el-table-column :label="t('policies.blockOverdueCount')" width="170">
        <template #default="{ row }">
          <el-input-number v-model="row.blockOverdueCount" :min="1" :max="100" size="small" />
        </template>
      </el-table-column>
      <el-table-column :label="`${t('policies.blockFine')}(分)`" width="170">
        <template #default="{ row }">
          <el-input-number v-model="row.blockFineCents" :min="1" :max="1000000" size="small" />
        </template>
      </el-table-column>
      <el-table-column :label="t('table.actions')" width="90" fixed="right">
        <template #default="{ row }">
          <el-button type="primary" size="small" @click="save(row as PolicyView)">{{ t('policies.save') }}</el-button>
        </template>
      </el-table-column>
    </el-table>
  </el-card>
</template>
