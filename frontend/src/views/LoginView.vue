<script setup lang="ts">
import { computed, reactive, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { useI18n } from 'vue-i18n'
import { ElMessage, type FormInstance, type FormRules } from 'element-plus'
import { User, Lock } from '@element-plus/icons-vue'
import { useAuthStore } from '@/stores/auth'
import { errorMessage } from '@/api/http'
import LanguageSwitcher from '@/components/LanguageSwitcher.vue'

const { t } = useI18n()
const auth = useAuthStore()
const router = useRouter()
const route = useRoute()

const formRef = ref<FormInstance>()
const loading = ref(false)
const form = reactive({ username: '', password: '' })

const rules = computed<FormRules>(() => ({
  username: [{ required: true, message: t('login.usernameRequired'), trigger: 'blur' }],
  password: [{ required: true, message: t('login.passwordRequired'), trigger: 'blur' }],
}))

async function submit() {
  const valid = await formRef.value?.validate().catch(() => false)
  if (!valid) return
  loading.value = true
  try {
    await auth.login(form.username, form.password)
    const redirect = typeof route.query.redirect === 'string' ? route.query.redirect : null
    if (redirect) {
      router.push(redirect)
    } else {
      router.push(auth.isAdmin ? { name: 'admin-dashboard' } : { name: 'home' })
    }
  } catch (error) {
    ElMessage.error(errorMessage(error))
  } finally {
    loading.value = false
  }
}
</script>

<template>
  <div class="login-page">
    <aside class="brand-panel">
      <div class="brand-content">
        <h1 class="wordmark">Libris</h1>
        <p class="tagline">{{ t('brand.tagline') }}</p>
        <p class="subtitle">{{ t('brand.subtitle') }}</p>
      </div>
      <div class="brand-footer">© 2026 Libris</div>
    </aside>

    <main class="form-panel">
      <div class="form-top">
        <LanguageSwitcher />
      </div>
      <div class="form-wrap">
        <h2>{{ t('login.title') }}</h2>
        <el-form
          ref="formRef"
          :model="form"
          :rules="rules"
          label-position="top"
          size="large"
          @keyup.enter="submit"
        >
          <el-form-item :label="t('login.username')" prop="username">
            <el-input v-model="form.username" :prefix-icon="User" autocomplete="username" />
          </el-form-item>
          <el-form-item :label="t('login.password')" prop="password">
            <el-input
              v-model="form.password"
              type="password"
              show-password
              :prefix-icon="Lock"
              autocomplete="current-password"
            />
          </el-form-item>
          <el-button
            type="primary"
            size="large"
            class="submit-btn"
            :loading="loading"
            @click="submit"
          >
            {{ t('login.submit') }}
          </el-button>
        </el-form>
      </div>
    </main>
  </div>
</template>

<style scoped>
.login-page {
  display: flex;
  min-height: 100vh;
}

.brand-panel {
  flex: 1.2;
  display: flex;
  flex-direction: column;
  justify-content: center;
  padding: 48px;
  color: #fff;
  background:
    radial-gradient(1200px 600px at 20% 10%, rgba(255, 255, 255, 0.08), transparent),
    linear-gradient(160deg, #002a52 0%, #003e74 55%, #1f5a94 100%);
}

.wordmark {
  font-size: 64px;
  margin: 0;
  letter-spacing: 2px;
  font-weight: 700;
}

.tagline {
  font-size: 22px;
  opacity: 0.92;
  margin: 12px 0 4px;
}

.subtitle {
  font-size: 15px;
  opacity: 0.7;
  margin: 0;
}

.brand-footer {
  position: absolute;
  bottom: 24px;
  font-size: 12px;
  opacity: 0.5;
}

.form-panel {
  flex: 1;
  display: flex;
  flex-direction: column;
  background: #fff;
}

.form-top {
  display: flex;
  justify-content: flex-end;
  padding: 20px 28px 0;
}

.form-wrap {
  flex: 1;
  display: flex;
  flex-direction: column;
  justify-content: center;
  max-width: 380px;
  width: 100%;
  margin: 0 auto;
  padding: 0 24px 80px;
}

.form-wrap h2 {
  margin-bottom: 28px;
  font-weight: 600;
}

.submit-btn {
  width: 100%;
  margin-top: 8px;
}

@media (max-width: 860px) {
  .brand-panel {
    display: none;
  }
}
</style>
