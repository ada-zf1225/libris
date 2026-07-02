<script setup lang="ts">
import { ref } from 'vue'
import { useI18n } from 'vue-i18n'
import { ElMessage, ElMessageBox } from 'element-plus'
import { Search, Right, Back, WarnTriangleFilled } from '@element-plus/icons-vue'
import {
  adminCirculation,
  adminReaders,
  type ReaderSummary,
  type FineView,
} from '@/api/admin'
import { errorMessage } from '@/api/http'
import { money, date } from '@/utils/format'

const { t } = useI18n()

const readerKey = ref('')
const reader = ref<ReaderSummary | null>(null)
const fines = ref<FineView[]>([])
const loadingReader = ref(false)

const checkoutBarcode = ref('')
const checkinBarcode = ref('')
const lostBarcode = ref('')
const busy = ref(false)

async function lookupReader() {
  const key = readerKey.value.trim()
  if (!key) return
  loadingReader.value = true
  try {
    reader.value = await adminCirculation.reader(key)
    fines.value = await adminReaders.fines(reader.value.id)
  } catch (e) {
    reader.value = null
    fines.value = []
    ElMessage.error(errorMessage(e))
  } finally {
    loadingReader.value = false
  }
}

async function refreshReader() {
  if (!reader.value) return
  try {
    reader.value = await adminCirculation.reader(String(reader.value.id))
    fines.value = await adminReaders.fines(reader.value.id)
  } catch {
    /* keep the stale card visible */
  }
}

async function doCheckout() {
  const barcode = checkoutBarcode.value.trim()
  if (!barcode) return
  if (!reader.value) {
    ElMessage.warning(t('desk.noReader'))
    return
  }
  busy.value = true
  try {
    const result = await adminCirculation.checkout(barcode, reader.value.id)
    ElMessage.success(
      t('desk.checkoutOk', { title: result.bookTitle, due: date(result.dueAt) }) +
        (result.fulfilledHold ? ` · ${t('desk.checkoutHoldFulfilled')}` : ''),
    )
    checkoutBarcode.value = ''
    await refreshReader()
  } catch (e) {
    ElMessage.error(errorMessage(e))
  } finally {
    busy.value = false
  }
}

async function doCheckin() {
  const barcode = checkinBarcode.value.trim()
  if (!barcode) return
  busy.value = true
  try {
    const result = await adminCirculation.checkin(barcode)
    checkinBarcode.value = ''
    const fineNote =
      result.fineCents != null
        ? `<p style="color:#c45656;margin-top:8px">${t('desk.fineIncurred', {
            amount: money(result.fineCents),
            days: result.overdueDays,
          })}</p>`
        : ''
    if (result.routing === 'TO_HOLD_SHELF') {
      await ElMessageBox.alert(
        `<strong>${t('desk.routeToHoldShelf', {
          title: result.bookTitle,
          reader: result.holdReaderName ?? '?',
        })}</strong>${fineNote}`,
        t('desk.checkin'),
        { dangerouslyUseHTMLString: true, type: 'warning' },
      ).catch(() => {})
    } else {
      await ElMessageBox.alert(
        `${t('desk.routeToShelf', { title: result.bookTitle })}${fineNote}`,
        t('desk.checkin'),
        { dangerouslyUseHTMLString: true, type: 'success' },
      ).catch(() => {})
    }
    await refreshReader()
  } catch (e) {
    ElMessage.error(errorMessage(e))
  } finally {
    busy.value = false
  }
}

async function doRenew(loanId: number) {
  try {
    const result = await adminCirculation.renew(loanId)
    ElMessage.success(t('desk.renewOk', { due: date(result.newDueAt) }))
    await refreshReader()
  } catch (e) {
    ElMessage.error(errorMessage(e))
  }
}

async function doMarkLost() {
  const barcode = lostBarcode.value.trim()
  if (!barcode) return
  try {
    await ElMessageBox.confirm(t('desk.markLostConfirm', { barcode }), t('desk.markLost'), {
      type: 'warning',
    })
  } catch {
    return
  }
  try {
    const result = (await adminCirculation.markLost(barcode)) as { fineCents: number | null }
    const suffix = result.fineCents
      ? t('desk.lostFineSuffix', { amount: money(result.fineCents) })
      : ''
    ElMessage.success(t('desk.lostOk', { fine: suffix }))
    lostBarcode.value = ''
    await refreshReader()
  } catch (e) {
    ElMessage.error(errorMessage(e))
  }
}

async function settleFine(fine: FineView, waive: boolean) {
  try {
    if (waive) {
      await adminCirculation.waiveFine(fine.id)
    } else {
      await adminCirculation.payFine(fine.id)
    }
    await refreshReader()
  } catch (e) {
    ElMessage.error(errorMessage(e))
  }
}
</script>

<template>
  <div class="desk">
    <el-row :gutter="16">
      <!-- reader panel -->
      <el-col :xs="24" :md="14">
        <el-card shadow="never">
          <div class="lookup-row">
            <el-input
              v-model="readerKey"
              :placeholder="t('desk.readerPlaceholder')"
              :prefix-icon="Search"
              size="large"
              clearable
              @keyup.enter="lookupReader"
            />
            <el-button type="primary" size="large" :loading="loadingReader" @click="lookupReader">
              {{ t('desk.lookup') }}
            </el-button>
          </div>

          <template v-if="reader">
            <div class="reader-head">
              <div>
                <span class="reader-name">{{ reader.displayName }}</span>
                <span class="reader-sub">@{{ reader.username }} · #{{ reader.id }}</span>
              </div>
              <div class="reader-tags">
                <el-tag v-if="reader.readerType" size="small">
                  {{ t(`readers.types.${reader.readerType}`) }}
                </el-tag>
                <el-tag :type="reader.status === 'BLOCKED' ? 'danger' : 'success'" size="small">
                  {{ reader.status === 'BLOCKED' ? t('desk.blocked') : t('desk.active') }}
                </el-tag>
              </div>
            </div>

            <div class="reader-stats">
              <el-tag type="info" effect="plain">
                {{ t('desk.activeLoans', { n: reader.activeLoanCount, max: reader.maxLoans }) }}
              </el-tag>
              <el-tag v-if="reader.overdueCount > 0" type="danger" effect="plain">
                <el-icon><WarnTriangleFilled /></el-icon>
                {{ t('desk.overdue', { n: reader.overdueCount }) }}
              </el-tag>
              <el-tag v-if="reader.unpaidFineCents > 0" type="warning" effect="plain">
                {{ t('desk.unpaidFines', { amount: money(reader.unpaidFineCents) }) }}
              </el-tag>
            </div>

            <el-table :data="reader.activeLoans" size="small" class="loans-table">
              <el-table-column prop="bookTitle" :label="t('books.fields.title')" min-width="180" />
              <el-table-column prop="barcode" :label="t('books.barcode')" width="110" />
              <el-table-column :label="t('desk.dueAt')" width="130">
                <template #default="{ row }">
                  <span :class="{ 'overdue-text': row.overdue }">{{ date(row.dueAt) }}</span>
                </template>
              </el-table-column>
              <el-table-column prop="renewCount" :label="t('desk.renewCount')" width="90" />
              <el-table-column :label="t('table.actions')" width="90">
                <template #default="{ row }">
                  <el-button link type="primary" size="small" @click="doRenew(row.id)">
                    {{ t('desk.renew') }}
                  </el-button>
                </template>
              </el-table-column>
            </el-table>

            <template v-if="fines.length > 0">
              <h4 class="fines-title">{{ t('desk.fines') }}</h4>
              <el-table :data="fines" size="small">
                <el-table-column prop="bookTitle" :label="t('books.fields.title')" min-width="160" />
                <el-table-column :label="t('desk.fines')" width="100">
                  <template #default="{ row }">{{ money(row.amountCents) }}</template>
                </el-table-column>
                <el-table-column width="90">
                  <template #default="{ row }">
                    <el-tag size="small" effect="plain">
                      {{ t(`desk.fineReason.${row.reason}`) }}
                    </el-tag>
                  </template>
                </el-table-column>
                <el-table-column :label="t('table.status')" width="90">
                  <template #default="{ row }">
                    <el-tag
                      size="small"
                      :type="row.status === 'UNPAID' ? 'danger' : 'info'"
                    >
                      {{ t(`desk.fineStatus.${row.status}`) }}
                    </el-tag>
                  </template>
                </el-table-column>
                <el-table-column :label="t('table.actions')" width="130">
                  <template #default="{ row }">
                    <template v-if="row.status === 'UNPAID'">
                      <el-button link type="primary" size="small" @click="settleFine(row as FineView, false)">
                        {{ t('desk.payFine') }}
                      </el-button>
                      <el-button link type="warning" size="small" @click="settleFine(row as FineView, true)">
                        {{ t('desk.waiveFine') }}
                      </el-button>
                    </template>
                  </template>
                </el-table-column>
              </el-table>
            </template>
          </template>
          <el-empty v-else :description="t('desk.noReader')" :image-size="90" />
        </el-card>
      </el-col>

      <!-- action panel -->
      <el-col :xs="24" :md="10">
        <el-card shadow="never" class="action-card">
          <h3><el-icon><Right /></el-icon> {{ t('desk.checkout') }}</h3>
          <p class="hint">{{ t('desk.scanCheckout') }}</p>
          <el-input
            v-model="checkoutBarcode"
            placeholder="LB000001"
            size="large"
            :disabled="busy"
            clearable
            @keyup.enter="doCheckout"
          >
            <template #append>
              <el-button :loading="busy" @click="doCheckout">{{ t('desk.checkout') }}</el-button>
            </template>
          </el-input>
        </el-card>

        <el-card shadow="never" class="action-card">
          <h3><el-icon><Back /></el-icon> {{ t('desk.checkin') }}</h3>
          <p class="hint">{{ t('desk.scanCheckin') }}</p>
          <el-input
            v-model="checkinBarcode"
            placeholder="LB000001"
            size="large"
            :disabled="busy"
            clearable
            @keyup.enter="doCheckin"
          >
            <template #append>
              <el-button :loading="busy" @click="doCheckin">{{ t('desk.checkin') }}</el-button>
            </template>
          </el-input>
        </el-card>

        <el-card shadow="never" class="action-card lost-card">
          <h3>{{ t('desk.markLost') }}</h3>
          <el-input
            v-model="lostBarcode"
            placeholder="LB000001"
            :disabled="busy"
            clearable
            @keyup.enter="doMarkLost"
          >
            <template #append>
              <el-button @click="doMarkLost">{{ t('desk.markLost') }}</el-button>
            </template>
          </el-input>
        </el-card>
      </el-col>
    </el-row>
  </div>
</template>

<style scoped>
.lookup-row {
  display: flex;
  gap: 10px;
  margin-bottom: 16px;
}

.reader-head {
  display: flex;
  justify-content: space-between;
  align-items: baseline;
}

.reader-name {
  font-size: 20px;
  font-weight: 600;
}

.reader-sub {
  margin-left: 8px;
  color: #8a919f;
  font-size: 13px;
}

.reader-tags {
  display: flex;
  gap: 6px;
}

.reader-stats {
  display: flex;
  gap: 8px;
  margin: 12px 0;
  flex-wrap: wrap;
}

.loans-table {
  margin-top: 4px;
}

.overdue-text {
  color: #c45656;
  font-weight: 600;
}

.fines-title {
  margin: 18px 0 8px;
}

.action-card {
  margin-bottom: 16px;
}

.action-card h3 {
  display: flex;
  align-items: center;
  gap: 6px;
  margin: 0 0 4px;
}

.hint {
  color: #8a919f;
  font-size: 13px;
  margin: 0 0 10px;
}

.lost-card :deep(.el-card__body) {
  padding-top: 14px;
}
</style>
