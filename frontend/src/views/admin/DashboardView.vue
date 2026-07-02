<script setup lang="ts">
import { useI18n } from 'vue-i18n'

const { t } = useI18n()
</script>

<template>
  <el-card shadow="never" class="dashboard-card">
    <h2>{{ t('nav.admin.dashboard') }}</h2>
    <el-empty :description="t('admin.dashboardHint')" />
  </el-card>
</template>

<style scoped>
.dashboard-card {
  border-radius: 10px;
}
</style>
