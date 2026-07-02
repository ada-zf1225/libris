<script setup lang="ts">
import { ref } from 'vue'
import { useI18n } from 'vue-i18n'
import { ElMessage } from 'element-plus'
import { Message } from '@element-plus/icons-vue'
import { apiForgotPassword } from '@/api/auth'
import { errorMessage } from '@/api/http'

const { t } = useI18n()
const account = ref('')
const sent = ref(false)
const loading = ref(false)

async function submit() {
  if (!account.value.trim()) return
  loading.value = true
  try {
    await apiForgotPassword(account.value.trim())
    sent.value = true
  } catch (e) {
    ElMessage.error(errorMessage(e))
  } finally {
    loading.value = false
  }
}
</script>

<template>
  <div class="auth-page">
    <el-card shadow="never" class="auth-card">
      <h2>{{ t('resetFlow.forgotTitle') }}</h2>
      <template v-if="!sent">
        <p class="hint">{{ t('resetFlow.forgotHint') }}</p>
        <el-input
          v-model="account"
          size="large"
          :prefix-icon="Message"
          :placeholder="t('resetFlow.accountPlaceholder')"
          @keyup.enter="submit"
        />
        <el-button type="primary" size="large" class="btn" :loading="loading" @click="submit">
          {{ t('resetFlow.sendLink') }}
        </el-button>
      </template>
      <el-result v-else icon="success" :title="t('resetFlow.sentTitle')" :sub-title="t('resetFlow.sentHint')" />
      <router-link :to="{ name: 'login' }" class="back">← {{ t('login.title') }}</router-link>
    </el-card>
  </div>
</template>

<style scoped>
.auth-page {
  min-height: 100vh;
  display: flex;
  align-items: center;
  justify-content: center;
  background: linear-gradient(160deg, #002a52 0%, #003e74 55%, #1f5a94 100%);
  padding: 24px;
}

.auth-card {
  width: 420px;
  border-radius: 12px;
  padding: 8px;
}

.hint {
  color: #6b7280;
  font-size: 14px;
}

.btn {
  width: 100%;
  margin-top: 16px;
}

.back {
  display: inline-block;
  margin-top: 16px;
  font-size: 13px;
  color: var(--libris-primary);
  text-decoration: none;
}
</style>
