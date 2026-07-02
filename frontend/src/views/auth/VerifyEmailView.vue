<script setup lang="ts">
import { onMounted, ref } from 'vue'
import { useRoute } from 'vue-router'
import { useI18n } from 'vue-i18n'
import { apiVerifyEmail } from '@/api/auth'

const { t } = useI18n()
const route = useRoute()
const state = ref<'pending' | 'ok' | 'fail'>('pending')

onMounted(async () => {
  const token = typeof route.query.token === 'string' ? route.query.token : ''
  if (!token) {
    state.value = 'fail'
    return
  }
  try {
    await apiVerifyEmail(token)
    state.value = 'ok'
  } catch {
    state.value = 'fail'
  }
})
</script>

<template>
  <div class="auth-page">
    <el-card shadow="never" class="auth-card">
      <el-result
        v-if="state === 'ok'"
        icon="success"
        :title="t('resetFlow.verifyOk')"
      >
        <template #extra>
          <router-link :to="{ name: 'home' }">
            <el-button type="primary">Libris</el-button>
          </router-link>
        </template>
      </el-result>
      <el-result v-else-if="state === 'fail'" icon="error" :title="t('resetFlow.verifyFail')" />
      <el-skeleton v-else :rows="3" animated />
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
  width: 460px;
  border-radius: 12px;
}
</style>
