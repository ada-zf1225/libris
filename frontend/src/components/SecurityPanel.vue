<script setup lang="ts">
import { onMounted, reactive, ref } from 'vue'
import { useI18n } from 'vue-i18n'
import { ElMessage, ElMessageBox } from 'element-plus'
import QRCode from 'qrcode'
import { Key, Message, Lock } from '@element-plus/icons-vue'
import { useAuthStore } from '@/stores/auth'
import { errorMessage } from '@/api/http'
import {
  passkeys,
  requestEmailVerification,
  totpConfirm,
  totpDisable,
  totpSetup,
  regenerateRecoveryCodes,
  type PasskeyView,
} from '@/api/security'
import { dateTime } from '@/utils/format'

const { t } = useI18n()
const auth = useAuthStore()

// ---------- email ----------

const emailSent = ref(false)

async function sendVerification() {
  try {
    await requestEmailVerification()
    emailSent.value = true
    ElMessage.success(t('security.verifySent'))
  } catch (e) {
    ElMessage.error(errorMessage(e))
  }
}

// ---------- TOTP ----------

const totpDialog = ref(false)
const qrDataUrl = ref('')
const secret = ref('')
const confirmCode = ref('')
const recoveryCodes = ref<string[]>([])
const disableForm = reactive({ password: '', code: '' })
const disableDialog = ref(false)

async function startTotp() {
  try {
    const setup = await totpSetup()
    secret.value = setup.secret
    qrDataUrl.value = await QRCode.toDataURL(setup.otpauthUrl, { width: 220, margin: 1 })
    confirmCode.value = ''
    recoveryCodes.value = []
    totpDialog.value = true
  } catch (e) {
    ElMessage.error(errorMessage(e))
  }
}

async function confirmTotp() {
  try {
    const result = await totpConfirm(confirmCode.value.trim())
    recoveryCodes.value = result.recoveryCodes
    await auth.refresh()
  } catch (e) {
    ElMessage.error(errorMessage(e))
  }
}

async function doDisable() {
  try {
    await totpDisable(disableForm.password, disableForm.code.trim())
    disableDialog.value = false
    disableForm.password = ''
    disableForm.code = ''
    await auth.refresh()
    ElMessage.success(t('security.totpDisabled'))
  } catch (e) {
    ElMessage.error(errorMessage(e))
  }
}

async function regenCodes() {
  try {
    const { value } = await ElMessageBox.prompt(t('security.regenPrompt'), t('security.regenTitle'), {
      inputPlaceholder: '123456',
    })
    const result = await regenerateRecoveryCodes(String(value ?? '').trim())
    recoveryCodes.value = result.recoveryCodes
    totpDialog.value = true
    qrDataUrl.value = ''
  } catch (e) {
    if (e !== 'cancel') ElMessage.error(errorMessage(e))
  }
}

function copyRecovery() {
  navigator.clipboard.writeText(recoveryCodes.value.join('\n'))
  ElMessage.success(t('security.copied'))
}

// ---------- passkeys ----------

const passkeySupported = passkeys.supported()
const keyList = ref<PasskeyView[]>([])

async function loadPasskeys() {
  keyList.value = await passkeys.list().catch(() => [])
}

async function addPasskey() {
  let label = ''
  try {
    const result = await ElMessageBox.prompt(t('security.passkeyLabelPrompt'), t('security.addPasskey'), {
      inputValue: 'My device',
    })
    label = result.value || 'Passkey'
  } catch {
    return
  }
  try {
    await passkeys.register(label)
    ElMessage.success(t('security.passkeyAdded'))
    await Promise.all([loadPasskeys(), auth.refresh()])
  } catch (e) {
    if ((e as Error)?.name !== 'NotAllowedError') {
      ElMessage.error(errorMessage(e))
    }
  }
}

async function removePasskey(key: PasskeyView) {
  try {
    await ElMessageBox.confirm(key.label, t('security.removePasskey'), { type: 'warning' })
  } catch {
    return
  }
  await passkeys.remove(key.credentialId).catch((e) => ElMessage.error(errorMessage(e)))
  await Promise.all([loadPasskeys(), auth.refresh()])
}

onMounted(loadPasskeys)
</script>

<template>
  <div class="security-panel">
    <!-- email verification -->
    <el-card shadow="never" class="sec-card">
      <div class="sec-row">
        <div>
          <h4><el-icon><Message /></el-icon> {{ t('security.email') }}</h4>
          <p class="sec-desc">
            {{ auth.user?.email ?? '—' }}
            <el-tag v-if="auth.user?.emailVerified" type="success" size="small">{{ t('security.verified') }}</el-tag>
            <el-tag v-else type="warning" size="small">{{ t('security.unverified') }}</el-tag>
          </p>
        </div>
        <el-button
          v-if="auth.user && !auth.user.emailVerified"
          :disabled="emailSent"
          @click="sendVerification"
        >
          {{ emailSent ? t('security.verifySentShort') : t('security.sendVerification') }}
        </el-button>
      </div>
    </el-card>

    <!-- TOTP -->
    <el-card shadow="never" class="sec-card">
      <div class="sec-row">
        <div>
          <h4><el-icon><Lock /></el-icon> {{ t('security.totp') }}</h4>
          <p class="sec-desc">{{ t('security.totpDesc') }}</p>
        </div>
        <div class="sec-actions">
          <template v-if="auth.user?.mfaEnabled">
            <el-tag type="success">{{ t('security.enabled') }}</el-tag>
            <el-button size="small" @click="regenCodes">{{ t('security.regenTitle') }}</el-button>
            <el-button size="small" type="danger" plain @click="disableDialog = true">
              {{ t('security.disable') }}
            </el-button>
          </template>
          <el-button v-else type="primary" @click="startTotp">{{ t('security.enable') }}</el-button>
        </div>
      </div>
    </el-card>

    <!-- passkeys -->
    <el-card shadow="never" class="sec-card">
      <div class="sec-row">
        <div>
          <h4><el-icon><Key /></el-icon> {{ t('security.passkeys') }}</h4>
          <p class="sec-desc">{{ t('security.passkeysDesc') }}</p>
        </div>
        <el-button v-if="passkeySupported" type="primary" @click="addPasskey">
          {{ t('security.addPasskey') }}
        </el-button>
      </div>
      <el-table v-if="keyList.length > 0" :data="keyList" size="small" class="key-table">
        <el-table-column prop="label" :label="t('security.passkeyLabel')" min-width="140" />
        <el-table-column width="180">
          <template #default="{ row }">{{ dateTime(row.createdAt) }}</template>
        </el-table-column>
        <el-table-column :label="t('table.actions')" width="90">
          <template #default="{ row }">
            <el-button link type="danger" size="small" @click="removePasskey(row as PasskeyView)">
              {{ t('books.delete') }}
            </el-button>
          </template>
        </el-table-column>
      </el-table>
    </el-card>

    <!-- TOTP setup dialog -->
    <el-dialog v-model="totpDialog" :title="t('security.totp')" width="480" :close-on-click-modal="false">
      <template v-if="recoveryCodes.length === 0">
        <ol class="setup-steps">
          <li>{{ t('security.step1') }}</li>
          <li>{{ t('security.step2') }}</li>
        </ol>
        <div class="qr-wrap">
          <img v-if="qrDataUrl" :src="qrDataUrl" alt="TOTP QR" />
          <code class="secret">{{ secret }}</code>
        </div>
        <el-input
          v-model="confirmCode"
          size="large"
          maxlength="6"
          class="code-input"
          placeholder="123456"
          @keyup.enter="confirmTotp"
        />
        <el-button type="primary" size="large" class="wide-btn" @click="confirmTotp">
          {{ t('security.confirmEnable') }}
        </el-button>
      </template>
      <template v-else>
        <el-alert type="warning" :closable="false" :title="t('security.recoveryTitle')"
                  :description="t('security.recoveryHint')" show-icon class="rec-alert" />
        <div class="rec-grid">
          <code v-for="code in recoveryCodes" :key="code">{{ code }}</code>
        </div>
        <el-button class="wide-btn" @click="copyRecovery">{{ t('security.copy') }}</el-button>
        <el-button type="primary" class="wide-btn done-btn" @click="totpDialog = false">
          {{ t('common.ok') }}
        </el-button>
      </template>
    </el-dialog>

    <!-- TOTP disable dialog -->
    <el-dialog v-model="disableDialog" :title="t('security.disable')" width="420">
      <el-input
        v-model="disableForm.password"
        type="password"
        show-password
        class="field"
        :placeholder="t('myLib.settingsForm.oldPassword')"
      />
      <el-input v-model="disableForm.code" class="field" maxlength="12" :placeholder="t('login.mfaPlaceholder')" />
      <template #footer>
        <el-button @click="disableDialog = false">{{ t('common.cancel') }}</el-button>
        <el-button type="danger" @click="doDisable">{{ t('security.disable') }}</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<style scoped>
.sec-card {
  border-radius: 10px;
  margin-bottom: 14px;
}

.sec-row {
  display: flex;
  justify-content: space-between;
  align-items: center;
  gap: 16px;
}

.sec-row h4 {
  display: flex;
  align-items: center;
  gap: 6px;
  margin: 0 0 4px;
}

.sec-desc {
  margin: 0;
  color: #6b7280;
  font-size: 13px;
}

.sec-actions {
  display: flex;
  align-items: center;
  gap: 8px;
}

.key-table {
  margin-top: 12px;
}

.setup-steps {
  color: #4a505a;
  font-size: 14px;
  padding-left: 18px;
}

.qr-wrap {
  text-align: center;
  margin: 12px 0;
}

.secret {
  display: block;
  font-size: 12px;
  color: #6b7280;
  word-break: break-all;
  margin-top: 6px;
}

.code-input :deep(input) {
  text-align: center;
  font-size: 20px;
  letter-spacing: 5px;
}

.wide-btn {
  width: 100%;
  margin: 12px 0 0;
}

.done-btn {
  margin-top: 8px;
}

.rec-alert {
  margin-bottom: 12px;
}

.rec-grid {
  display: grid;
  grid-template-columns: repeat(2, 1fr);
  gap: 8px;
}

.rec-grid code {
  background: #f2f5f9;
  border-radius: 6px;
  padding: 6px 10px;
  text-align: center;
  font-size: 14px;
}

.field {
  margin-bottom: 12px;
}
</style>
