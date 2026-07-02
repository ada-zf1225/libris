<script setup lang="ts">
import { useI18n } from 'vue-i18n'
import { useAuthStore } from '@/stores/auth'

const { t } = useI18n()
const auth = useAuthStore()
</script>

<template>
  <div class="page-container">
    <el-card shadow="never" class="welcome-card">
      <h2>{{ t('home.welcome') }}{{ auth.user ? ` · ${auth.user.displayName}` : '' }}</h2>
      <el-empty :description="t('home.discoveryHint')" />
    </el-card>
  </div>
</template>

<style scoped>
.welcome-card {
  border-radius: 10px;
}
</style>
