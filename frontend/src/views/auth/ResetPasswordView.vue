<script setup lang="ts">
import { reactive, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { useI18n } from 'vue-i18n'
import { ElMessage } from 'element-plus'
import { Lock } from '@element-plus/icons-vue'
import { apiResetPassword } from '@/api/auth'
import { errorMessage } from '@/api/http'

const { t } = useI18n()
const route = useRoute()
const router = useRouter()

const token = typeof route.query.token === 'string' ? route.query.token : ''
const form = reactive({ password: '', confirm: '' })
const loading = ref(false)

async function submit() {
  if (!form.password || form.password !== form.confirm) {
    ElMessage.error(t('myLib.settingsForm.mismatch'))
    return
  }
  loading.value = true
  try {
    await apiResetPassword(token, form.password)
    ElMessage.success(t('resetFlow.resetOk'))
    router.push({ name: 'login' })
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
      <h2>{{ t('resetFlow.resetTitle') }}</h2>
      <el-result v-if="!token" icon="error" :title="t('resetFlow.tokenMissing')" />
      <template v-else>
        <el-input
          v-model="form.password"
          type="password"
          show-password
          size="large"
          class="field"
          :prefix-icon="Lock"
          :placeholder="t('myLib.settingsForm.newPassword')"
        />
        <el-input
          v-model="form.confirm"
          type="password"
          show-password
          size="large"
          class="field"
          :prefix-icon="Lock"
          :placeholder="t('myLib.settingsForm.confirmPassword')"
          @keyup.enter="submit"
        />
        <el-button type="primary" size="large" class="btn" :loading="loading" @click="submit">
          {{ t('resetFlow.resetSubmit') }}
        </el-button>
      </template>
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

.field {
  margin-bottom: 14px;
}

.btn {
  width: 100%;
  margin-top: 4px;
}

.back {
  display: inline-block;
  margin-top: 16px;
  font-size: 13px;
  color: var(--libris-primary);
  text-decoration: none;
}
</style>
