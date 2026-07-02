<script setup lang="ts">
import { useI18n } from 'vue-i18n'
import { ArrowDown } from '@element-plus/icons-vue'
import { persistLocale, type AppLocale } from '@/i18n'
import { apiUpdateLocale } from '@/api/auth'
import { useAuthStore } from '@/stores/auth'

const { locale } = useI18n()
const auth = useAuthStore()

async function switchTo(next: AppLocale) {
  locale.value = next
  persistLocale(next)
  if (auth.isAuthenticated) {
    try {
      await apiUpdateLocale(next)
    } catch {
      // preference persists locally even if the server call fails
    }
  }
}
</script>

<template>
  <el-dropdown trigger="click" @command="switchTo">
    <span class="lang-trigger">
      {{ locale === 'zh-CN' ? '中文' : 'EN' }}
      <el-icon><ArrowDown /></el-icon>
    </span>
    <template #dropdown>
      <el-dropdown-menu>
        <el-dropdown-item command="zh-CN" :disabled="locale === 'zh-CN'">简体中文</el-dropdown-item>
        <el-dropdown-item command="en" :disabled="locale === 'en'">English</el-dropdown-item>
      </el-dropdown-menu>
    </template>
  </el-dropdown>
</template>

<style scoped>
.lang-trigger {
  display: inline-flex;
  align-items: center;
  gap: 4px;
  cursor: pointer;
  color: inherit;
  font-size: 14px;
}
</style>
