<script setup lang="ts">
import { onMounted, reactive, ref } from 'vue'
import { useI18n } from 'vue-i18n'
import { ElMessage, ElMessageBox, type FormInstance } from 'element-plus'
import {
  Reading,
  AlarmClock,
  CollectionTag,
  Money,
  Star,
} from '@element-plus/icons-vue'
import { my, type Overview, type HoldView, type FavoriteView, type SuggestionView } from '@/api/my'
import SecurityPanel from '@/components/SecurityPanel.vue'
import type { LoanView, FineView } from '@/api/admin'
import { apiChangePassword } from '@/api/auth'
import { errorMessage } from '@/api/http'
import { money, date, dateTime } from '@/utils/format'
import BookCover from '@/components/BookCover.vue'

const { t } = useI18n()

const tab = ref('overview')
const overview = ref<Overview | null>(null)
const loans = ref<LoanView[]>([])
const history = ref<LoanView[]>([])
const historyTotal = ref(0)
const historyPage = ref(0)
const holds = ref<HoldView[]>([])
const fines = ref<FineView[]>([])
const favorites = ref<FavoriteView[]>([])
const suggestions = ref<SuggestionView[]>([])

async function refreshOverview() {
  overview.value = await my.overview().catch(() => null)
}

async function loadTab(name: string) {
  try {
    switch (name) {
      case 'overview':
        await refreshOverview()
        break
      case 'loans':
        loans.value = await my.loans()
        break
      case 'history': {
        const result = await my.history(historyPage.value)
        history.value = result.content
        historyTotal.value = result.totalElements
        break
      }
      case 'holds':
        holds.value = await my.holds()
        break
      case 'fines':
        fines.value = await my.fines()
        break
      case 'favorites':
        favorites.value = await my.favorites()
        break
      case 'suggestions':
        suggestions.value = await my.suggestions()
        break
    }
  } catch (e) {
    ElMessage.error(errorMessage(e))
  }
}

onMounted(() => loadTab('overview'))

async function renew(loan: LoanView) {
  try {
    const result = await my.renew(loan.id)
    ElMessage.success(t('myLib.renewed', { due: date(result.newDueAt) }))
    await loadTab('loans')
  } catch (e) {
    ElMessage.error(errorMessage(e))
  }
}

async function cancelHold(hold: HoldView) {
  try {
    await ElMessageBox.confirm(hold.bookTitle, t('myLib.cancelHold'), { type: 'warning' })
  } catch {
    return
  }
  try {
    await my.cancelHold(hold.id)
    await loadTab('holds')
  } catch (e) {
    ElMessage.error(errorMessage(e))
  }
}

async function payFine(fine: FineView) {
  try {
    await my.payFine(fine.id)
    ElMessage.success(t('myLib.paid'))
    await loadTab('fines')
  } catch (e) {
    ElMessage.error(errorMessage(e))
  }
}

async function unfavorite(f: FavoriteView) {
  await my.removeFavorite(f.bookId).catch(() => {})
  await loadTab('favorites')
}

// suggestion form
const suggestFormRef = ref<FormInstance>()
const suggestForm = reactive({ title: '', author: '', isbn: '', reason: '' })

async function submitSuggestion() {
  const valid = await suggestFormRef.value?.validate().catch(() => false)
  if (!valid) return
  try {
    await my.suggest({ ...suggestForm })
    ElMessage.success(t('myLib.suggestForm.submitted'))
    Object.assign(suggestForm, { title: '', author: '', isbn: '', reason: '' })
    await loadTab('suggestions')
  } catch (e) {
    ElMessage.error(errorMessage(e))
  }
}

// settings
const pwdFormRef = ref<FormInstance>()
const pwdForm = reactive({ oldPassword: '', newPassword: '', confirm: '' })

async function changePassword() {
  if (pwdForm.newPassword !== pwdForm.confirm) {
    ElMessage.error(t('myLib.settingsForm.mismatch'))
    return
  }
  try {
    await apiChangePassword(pwdForm.oldPassword, pwdForm.newPassword)
    ElMessage.success(t('myLib.settingsForm.changed'))
    Object.assign(pwdForm, { oldPassword: '', newPassword: '', confirm: '' })
  } catch (e) {
    ElMessage.error(errorMessage(e))
  }
}

const holdTagType = (status: HoldView['status']) =>
  ((
    { QUEUED: 'info', READY: 'success', FULFILLED: 'primary', EXPIRED: 'danger', CANCELLED: 'info' }
  )[status] ?? 'info') as 'info' | 'success' | 'primary' | 'danger'
</script>

<template>
  <div class="page-container">
    <h2 class="page-title">{{ t('myLib.title') }}</h2>

    <el-tabs v-model="tab" @tab-change="(name: string | number) => loadTab(String(name))">
      <!-- overview -->
      <el-tab-pane :label="t('myLib.overview')" name="overview">
        <div v-if="overview" class="stat-grid">
          <el-card shadow="never" class="stat-card">
            <el-icon class="stat-icon"><Reading /></el-icon>
            <div class="stat-num">{{ overview.activeLoans }}</div>
            <div class="stat-label">{{ t('myLib.stat.activeLoans') }}</div>
          </el-card>
          <el-card shadow="never" class="stat-card" :class="{ warn: overview.overdue > 0 }">
            <el-icon class="stat-icon"><AlarmClock /></el-icon>
            <div class="stat-num">{{ overview.overdue }}</div>
            <div class="stat-label">{{ t('myLib.stat.overdue') }}</div>
          </el-card>
          <el-card shadow="never" class="stat-card">
            <el-icon class="stat-icon"><CollectionTag /></el-icon>
            <div class="stat-num">{{ overview.activeHolds }}</div>
            <div class="stat-label">{{ t('myLib.stat.holds') }}</div>
          </el-card>
          <el-card shadow="never" class="stat-card" :class="{ warn: overview.unpaidFineCents > 0 }">
            <el-icon class="stat-icon"><Money /></el-icon>
            <div class="stat-num">{{ money(overview.unpaidFineCents) }}</div>
            <div class="stat-label">{{ t('myLib.stat.unpaidFines') }}</div>
          </el-card>
          <el-card shadow="never" class="stat-card">
            <el-icon class="stat-icon"><Star /></el-icon>
            <div class="stat-num">{{ overview.favorites }}</div>
            <div class="stat-label">{{ t('myLib.stat.favorites') }}</div>
          </el-card>
        </div>
      </el-tab-pane>

      <!-- current loans -->
      <el-tab-pane :label="t('myLib.loans')" name="loans">
        <el-empty v-if="loans.length === 0" :description="t('myLib.emptyLoans')" />
        <el-table v-else :data="loans">
          <el-table-column prop="bookTitle" :label="t('books.fields.title')" min-width="220" />
          <el-table-column prop="barcode" :label="t('books.barcode')" width="120" />
          <el-table-column :label="t('desk.loanedAt')" width="120">
            <template #default="{ row }">{{ date(row.loanedAt) }}</template>
          </el-table-column>
          <el-table-column :label="t('desk.dueAt')" width="140">
            <template #default="{ row }">
              <span :class="{ 'overdue-text': row.overdue }">{{ date(row.dueAt) }}</span>
            </template>
          </el-table-column>
          <el-table-column prop="renewCount" :label="t('desk.renewCount')" width="90" />
          <el-table-column :label="t('table.actions')" width="100">
            <template #default="{ row }">
              <el-button link type="primary" @click="renew(row as LoanView)">{{ t('myLib.renew') }}</el-button>
            </template>
          </el-table-column>
        </el-table>
      </el-tab-pane>

      <!-- history -->
      <el-tab-pane :label="t('myLib.history')" name="history">
        <el-table :data="history">
          <el-table-column prop="bookTitle" :label="t('books.fields.title')" min-width="220" />
          <el-table-column :label="t('desk.loanedAt')" width="130">
            <template #default="{ row }">{{ date(row.loanedAt) }}</template>
          </el-table-column>
          <el-table-column :label="t('myLib.returnedAt')" width="130">
            <template #default="{ row }">{{ date(row.returnedAt) }}</template>
          </el-table-column>
        </el-table>
        <el-pagination
          v-if="historyTotal > 10"
          class="pager"
          layout="prev, pager, next"
          :total="historyTotal"
          :page-size="10"
          :current-page="historyPage + 1"
          @current-change="(p: number) => { historyPage = p - 1; loadTab('history') }"
        />
      </el-tab-pane>

      <!-- holds -->
      <el-tab-pane :label="t('myLib.holds')" name="holds">
        <el-empty v-if="holds.length === 0" :description="t('myLib.emptyHolds')" />
        <el-table v-else :data="holds">
          <el-table-column prop="bookTitle" :label="t('books.fields.title')" min-width="220" />
          <el-table-column :label="t('table.status')" min-width="200">
            <template #default="{ row }">
              <el-tag size="small" :type="holdTagType(row.status)">
                {{ t(`myLib.holdStatus.${row.status}`) }}
              </el-tag>
              <span v-if="row.status === 'QUEUED'" class="hold-note">
                {{ t('myLib.holdQueued', { n: row.queuePosition }) }}
              </span>
              <span v-else-if="row.status === 'READY' && row.expiresAt" class="hold-note ready">
                {{ t('myLib.holdReady', { date: date(row.expiresAt) }) }}
              </span>
            </template>
          </el-table-column>
          <el-table-column :label="t('table.actions')" width="120">
            <template #default="{ row }">
              <el-button
                v-if="row.status === 'QUEUED' || row.status === 'READY'"
                link
                type="danger"
                @click="cancelHold(row as HoldView)"
              >
                {{ t('myLib.cancelHold') }}
              </el-button>
            </template>
          </el-table-column>
        </el-table>
      </el-tab-pane>

      <!-- fines -->
      <el-tab-pane :label="t('myLib.fines')" name="fines">
        <el-empty v-if="fines.length === 0" :description="t('myLib.emptyFines')" />
        <el-table v-else :data="fines">
          <el-table-column prop="bookTitle" :label="t('books.fields.title')" min-width="200" />
          <el-table-column width="110">
            <template #default="{ row }">{{ money(row.amountCents) }}</template>
          </el-table-column>
          <el-table-column width="110">
            <template #default="{ row }">
              <el-tag size="small" effect="plain">{{ t(`desk.fineReason.${row.reason}`) }}</el-tag>
            </template>
          </el-table-column>
          <el-table-column :label="t('table.status')" width="100">
            <template #default="{ row }">
              <el-tag size="small" :type="row.status === 'UNPAID' ? 'danger' : 'info'">
                {{ t(`desk.fineStatus.${row.status}`) }}
              </el-tag>
            </template>
          </el-table-column>
          <el-table-column width="120">
            <template #default="{ row }">{{ dateTime(row.createdAt) }}</template>
          </el-table-column>
          <el-table-column :label="t('table.actions')" width="170">
            <template #default="{ row }">
              <el-button
                v-if="row.status === 'UNPAID'"
                type="primary"
                size="small"
                @click="payFine(row as FineView)"
              >
                {{ t('myLib.payMock') }}
              </el-button>
            </template>
          </el-table-column>
        </el-table>
      </el-tab-pane>

      <!-- favorites -->
      <el-tab-pane :label="t('myLib.favorites')" name="favorites">
        <el-empty v-if="favorites.length === 0" :description="t('myLib.emptyFavorites')" />
        <div v-else class="fav-grid">
          <div v-for="f in favorites" :key="f.id" class="fav-card">
            <router-link :to="{ name: 'book-detail', params: { id: f.bookId } }">
              <BookCover :src="f.coverUrl" :title="f.title" class="fav-cover" />
            </router-link>
            <p class="fav-name">{{ f.title }}</p>
            <p class="fav-author">{{ f.author }}</p>
            <el-button link type="danger" size="small" @click="unfavorite(f)">
              {{ t('detail.unfavorite') }}
            </el-button>
          </div>
        </div>
      </el-tab-pane>

      <!-- suggestions -->
      <el-tab-pane :label="t('myLib.suggestions')" name="suggestions">
        <el-card shadow="never" class="suggest-card">
          <h4>{{ t('myLib.suggestForm.title') }}</h4>
          <el-form ref="suggestFormRef" :model="suggestForm" label-width="90px">
            <el-row :gutter="12">
              <el-col :span="12">
                <el-form-item
                  :label="t('myLib.suggestForm.bookTitle')"
                  prop="title"
                  :rules="{ required: true, message: t('form.required') }"
                >
                  <el-input v-model="suggestForm.title" />
                </el-form-item>
              </el-col>
              <el-col :span="12">
                <el-form-item :label="t('myLib.suggestForm.author')">
                  <el-input v-model="suggestForm.author" />
                </el-form-item>
              </el-col>
            </el-row>
            <el-row :gutter="12">
              <el-col :span="12">
                <el-form-item :label="t('myLib.suggestForm.isbn')">
                  <el-input v-model="suggestForm.isbn" />
                </el-form-item>
              </el-col>
              <el-col :span="12">
                <el-form-item :label="t('myLib.suggestForm.reason')">
                  <el-input v-model="suggestForm.reason" />
                </el-form-item>
              </el-col>
            </el-row>
            <el-button type="primary" @click="submitSuggestion">
              {{ t('myLib.suggestForm.submit') }}
            </el-button>
          </el-form>
        </el-card>

        <el-empty v-if="suggestions.length === 0" :description="t('myLib.emptySuggestions')" />
        <el-table v-else :data="suggestions">
          <el-table-column prop="title" :label="t('myLib.suggestForm.bookTitle')" min-width="180" />
          <el-table-column prop="author" :label="t('myLib.suggestForm.author')" width="140" />
          <el-table-column :label="t('table.status')" width="100">
            <template #default="{ row }">
              <el-tag
                size="small"
                :type="row.status === 'APPROVED' ? 'success' : row.status === 'REJECTED' ? 'danger' : 'info'"
              >
                {{ row.status }}
              </el-tag>
            </template>
          </el-table-column>
          <el-table-column prop="reply" min-width="160" />
          <el-table-column width="120">
            <template #default="{ row }">{{ date(row.createdAt) }}</template>
          </el-table-column>
        </el-table>
      </el-tab-pane>

      <!-- settings -->
      <el-tab-pane :label="t('myLib.settings')" name="settings">
        <SecurityPanel />
        <el-card shadow="never" class="settings-card">
          <h4>{{ t('myLib.settingsForm.changePassword') }}</h4>
          <el-form ref="pwdFormRef" :model="pwdForm" label-width="120px" class="pwd-form">
            <el-form-item :label="t('myLib.settingsForm.oldPassword')" prop="oldPassword"
              :rules="{ required: true, message: t('form.required') }">
              <el-input v-model="pwdForm.oldPassword" type="password" show-password />
            </el-form-item>
            <el-form-item :label="t('myLib.settingsForm.newPassword')" prop="newPassword"
              :rules="[{ required: true, message: t('form.required') }, { min: 8, message: t('form.required') }]">
              <el-input v-model="pwdForm.newPassword" type="password" show-password />
            </el-form-item>
            <el-form-item :label="t('myLib.settingsForm.confirmPassword')" prop="confirm"
              :rules="{ required: true, message: t('form.required') }">
              <el-input v-model="pwdForm.confirm" type="password" show-password />
            </el-form-item>
            <el-button type="primary" @click="changePassword">
              {{ t('myLib.settingsForm.changePassword') }}
            </el-button>
          </el-form>
        </el-card>
      </el-tab-pane>
    </el-tabs>
  </div>
</template>

<style scoped>
.page-title {
  margin: 4px 0 14px;
}

.stat-grid {
  display: grid;
  grid-template-columns: repeat(auto-fit, minmax(160px, 1fr));
  gap: 14px;
}

.stat-card {
  text-align: center;
  border-radius: 10px;
  padding: 6px 0;
}

.stat-card.warn .stat-num {
  color: #c45656;
}

.stat-icon {
  font-size: 22px;
  color: var(--libris-primary);
}

.stat-num {
  font-size: 26px;
  font-weight: 700;
  margin: 4px 0;
}

.stat-label {
  color: #8a919f;
  font-size: 13px;
}

.overdue-text {
  color: #c45656;
  font-weight: 600;
}

.hold-note {
  margin-left: 8px;
  font-size: 12px;
  color: #8a919f;
}

.hold-note.ready {
  color: #529b2e;
  font-weight: 600;
}

.pager {
  margin-top: 14px;
  justify-content: center;
}

.fav-grid {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(130px, 1fr));
  gap: 16px;
}

.fav-card {
  text-align: center;
}

.fav-cover {
  width: 100%;
  aspect-ratio: 5 / 7;
}

.fav-name {
  margin: 8px 0 2px;
  font-size: 13px;
  font-weight: 600;
}

.fav-author {
  margin: 0 0 4px;
  font-size: 12px;
  color: #8a919f;
}

.suggest-card {
  margin-bottom: 16px;
  border-radius: 10px;
}

.settings-card {
  max-width: 560px;
  border-radius: 10px;
}

.pwd-form {
  max-width: 460px;
}
</style>
