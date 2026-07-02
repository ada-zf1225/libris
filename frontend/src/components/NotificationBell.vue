<script setup lang="ts">
import { onMounted, onUnmounted, ref } from 'vue'
import { useI18n } from 'vue-i18n'
import { Bell } from '@element-plus/icons-vue'
import { http } from '@/api/http'
import type { PageResponse } from '@/api/admin'
import { dateTime } from '@/utils/format'

interface NotificationView {
  id: number
  type: string
  title: string
  body: string | null
  readAt: string | null
  createdAt: string
}

const { t } = useI18n()

const unread = ref(0)
const items = ref<NotificationView[]>([])
const visible = ref(false)
let timer: ReturnType<typeof setInterval> | null = null

async function refreshCount() {
  unread.value = await http
    .get<number>('/api/my/notifications/unread-count')
    .then((r) => r.data)
    .catch(() => 0)
}

async function loadList() {
  const { data } = await http
    .get<PageResponse<NotificationView>>('/api/my/notifications', { params: { size: 10 } })
    .catch(() => ({ data: null }))
  if (data) items.value = data.content
}

async function open() {
  visible.value = true
  await loadList()
}

async function markRead(n: NotificationView) {
  if (n.readAt) return
  await http.post(`/api/my/notifications/${n.id}/read`).catch(() => {})
  n.readAt = new Date().toISOString()
  await refreshCount()
}

async function markAll() {
  await http.post('/api/my/notifications/read-all').catch(() => {})
  await Promise.all([loadList(), refreshCount()])
}

onMounted(() => {
  refreshCount()
  timer = setInterval(refreshCount, 60_000)
})

onUnmounted(() => {
  if (timer) clearInterval(timer)
})
</script>

<template>
  <el-popover :visible="visible" placement="bottom-end" :width="380" trigger="click"
    @update:visible="(v: boolean) => (visible = v)">
    <template #reference>
      <el-badge :value="unread" :hidden="unread === 0" :max="99" class="bell-badge">
        <el-icon class="bell-icon" @click="open"><Bell /></el-icon>
      </el-badge>
    </template>

    <div class="notif-head">
      <strong>{{ t('notif.title') }}</strong>
      <el-button v-if="unread > 0" link type="primary" size="small" @click="markAll">
        {{ t('notif.markAll') }}
      </el-button>
    </div>
    <el-scrollbar max-height="360px">
      <el-empty v-if="items.length === 0" :description="t('notif.empty')" :image-size="60" />
      <div
        v-for="n in items"
        :key="n.id"
        class="notif-item"
        :class="{ unread: !n.readAt }"
        @click="markRead(n)"
      >
        <p class="notif-title">{{ n.title }}</p>
        <p v-if="n.body" class="notif-body">{{ n.body }}</p>
        <p class="notif-time">{{ dateTime(n.createdAt) }}</p>
      </div>
    </el-scrollbar>
  </el-popover>
</template>

<style scoped>
.bell-badge {
  cursor: pointer;
}

.bell-icon {
  font-size: 19px;
  color: inherit;
  vertical-align: middle;
}

.notif-head {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 8px;
}

.notif-item {
  padding: 8px 10px;
  border-radius: 8px;
  cursor: pointer;
}

.notif-item:hover {
  background: #f2f5f9;
}

.notif-item.unread {
  background: #eef4fb;
}

.notif-title {
  margin: 0;
  font-size: 13px;
  font-weight: 600;
}

.notif-body {
  margin: 2px 0 0;
  font-size: 12px;
  color: #6b7280;
}

.notif-time {
  margin: 4px 0 0;
  font-size: 11px;
  color: #9aa3af;
}
</style>
