<script setup lang="ts">
import { computed, nextTick, reactive, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { useI18n } from 'vue-i18n'
import { ElMessage, type FormInstance, type FormRules } from 'element-plus'
import { User, Lock, Key } from '@element-plus/icons-vue'
import { useAuthStore } from '@/stores/auth'
import { errorMessage } from '@/api/http'
import { passkeys } from '@/api/security'
import LanguageSwitcher from '@/components/LanguageSwitcher.vue'

const { t } = useI18n()
const auth = useAuthStore()
const router = useRouter()
const route = useRoute()

const formRef = ref<FormInstance>()
const loading = ref(false)
const form = reactive({ username: '', password: '' })

const step = ref<'password' | 'mfa'>('password')
const mfaCode = ref('')
const mfaInput = ref<HTMLInputElement>()
const passkeySupported = passkeys.supported()

const rules = computed<FormRules>(() => ({
  username: [{ required: true, message: t('login.usernameRequired'), trigger: 'blur' }],
  password: [{ required: true, message: t('login.passwordRequired'), trigger: 'blur' }],
}))

function afterLogin() {
  const redirect = typeof route.query.redirect === 'string' ? route.query.redirect : null
  if (redirect) {
    router.push(redirect)
  } else {
    router.push(auth.isStaff ? { name: 'admin-dashboard' } : { name: 'home' })
  }
}

async function submit() {
  const valid = await formRef.value?.validate().catch(() => false)
  if (!valid) return
  loading.value = true
  try {
    const needMfa = await auth.login(form.username, form.password)
    if (needMfa) {
      step.value = 'mfa'
      await nextTick()
      mfaInput.value?.focus()
    } else {
      afterLogin()
    }
  } catch (error) {
    ElMessage.error(errorMessage(error))
  } finally {
    loading.value = false
  }
}

async function submitMfa() {
  if (!mfaCode.value.trim()) return
  loading.value = true
  try {
    await auth.verifyMfa(mfaCode.value.trim())
    afterLogin()
  } catch (error) {
    ElMessage.error(errorMessage(error))
    mfaCode.value = ''
  } finally {
    loading.value = false
  }
}

async function withPasskey() {
  loading.value = true
  try {
    await auth.loginWithPasskey()
    afterLogin()
  } catch (error) {
    if ((error as Error)?.name !== 'NotAllowedError') {
      ElMessage.error(errorMessage(error))
    }
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
        <template v-if="step === 'password'">
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
              <el-input v-model="form.username" :prefix-icon="User" autocomplete="username webauthn" />
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
            <div class="aux-row">
              <router-link :to="{ name: 'forgot-password' }" class="aux-link">
                {{ t('login.forgot') }}
              </router-link>
            </div>
            <el-button type="primary" size="large" class="submit-btn" :loading="loading" @click="submit">
              {{ t('login.submit') }}
            </el-button>
            <el-divider v-if="passkeySupported" class="divider">{{ t('login.or') }}</el-divider>
            <el-button
              v-if="passkeySupported"
              size="large"
              class="submit-btn passkey-btn"
              :icon="Key"
              :loading="loading"
              @click="withPasskey"
            >
              {{ t('login.passkey') }}
            </el-button>
          </el-form>
        </template>

        <template v-else>
          <h2>{{ t('login.mfaTitle') }}</h2>
          <p class="mfa-hint">{{ t('login.mfaHint') }}</p>
          <el-input
            ref="mfaInput"
            v-model="mfaCode"
            size="large"
            maxlength="12"
            class="mfa-input"
            :placeholder="t('login.mfaPlaceholder')"
            @keyup.enter="submitMfa"
          />
          <el-button type="primary" size="large" class="submit-btn mfa-btn" :loading="loading" @click="submitMfa">
            {{ t('login.mfaSubmit') }}
          </el-button>
          <el-button link class="back-link" @click="step = 'password'">
            ← {{ t('common.cancel') }}
          </el-button>
        </template>
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

.aux-row {
  display: flex;
  justify-content: flex-end;
  margin: -6px 0 14px;
}

.aux-link {
  font-size: 13px;
  color: var(--libris-primary);
  text-decoration: none;
}

.submit-btn {
  width: 100%;
}

.divider {
  margin: 18px 0;
}

.passkey-btn {
  margin-left: 0;
}

.mfa-hint {
  color: #6b7280;
  font-size: 14px;
  margin: -14px 0 20px;
}

.mfa-input :deep(input) {
  text-align: center;
  font-size: 22px;
  letter-spacing: 6px;
}

.mfa-btn {
  margin-top: 16px;
}

.back-link {
  margin-top: 12px;
}

@media (max-width: 860px) {
  .brand-panel {
    display: none;
  }
}
</style>
