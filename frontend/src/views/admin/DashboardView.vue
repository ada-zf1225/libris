<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { useI18n } from 'vue-i18n'
import { ElMessage } from 'element-plus'
import type { EChartsOption } from 'echarts'
import { http, errorMessage } from '@/api/http'
import { money } from '@/utils/format'
import EChart from '@/components/EChart.vue'

interface Dashboard {
  totals: {
    books: number
    copies: number
    readers: number
    activeLoans: number
    overdueLoans: number
    readyHolds: number
    unpaidFineCents: number
  }
  trend: { day: string; loans: number; returns: number }[]
  topBooks: { bookId: number; title: string; author: string; loanCount: number }[]
  categoryShare: { code: string; nameZh: string; nameEn: string; loanCount: number }[]
  overdueRate: number
}

const { t, locale } = useI18n()
const data = ref<Dashboard | null>(null)

onMounted(async () => {
  try {
    data.value = await http.get<Dashboard>('/api/admin/stats').then((r) => r.data)
  } catch (e) {
    ElMessage.error(errorMessage(e))
  }
})

const trendOption = computed<EChartsOption>(() => ({
  tooltip: { trigger: 'axis' },
  legend: { data: [t('dash.loansSeries'), t('dash.returnsSeries')] },
  grid: { left: 40, right: 16, top: 40, bottom: 24 },
  xAxis: { type: 'category', data: data.value?.trend.map((p) => p.day) ?? [] },
  yAxis: { type: 'value', minInterval: 1 },
  series: [
    {
      name: t('dash.loansSeries'),
      type: 'line',
      smooth: true,
      areaStyle: { opacity: 0.12 },
      itemStyle: { color: '#003e74' },
      data: data.value?.trend.map((p) => p.loans) ?? [],
    },
    {
      name: t('dash.returnsSeries'),
      type: 'line',
      smooth: true,
      itemStyle: { color: '#529b2e' },
      data: data.value?.trend.map((p) => p.returns) ?? [],
    },
  ],
}))

const topBooksOption = computed<EChartsOption>(() => ({
  tooltip: {},
  grid: { left: 8, right: 30, top: 10, bottom: 24, containLabel: true },
  xAxis: { type: 'value', minInterval: 1 },
  yAxis: {
    type: 'category',
    inverse: true,
    data: (data.value?.topBooks ?? []).map((b) => (b.title.length > 14 ? b.title.slice(0, 14) + '…' : b.title)),
  },
  series: [
    {
      type: 'bar',
      itemStyle: { color: '#1f5a94', borderRadius: [0, 4, 4, 0] },
      data: (data.value?.topBooks ?? []).map((b) => b.loanCount),
    },
  ],
}))

const shareOption = computed<EChartsOption>(() => ({
  tooltip: { trigger: 'item' },
  series: [
    {
      type: 'pie',
      radius: ['42%', '72%'],
      itemStyle: { borderRadius: 6, borderColor: '#fff', borderWidth: 2 },
      label: { formatter: '{b}: {c}' },
      data: (data.value?.categoryShare ?? []).map((c) => ({
        name: locale.value === 'zh-CN' ? `${c.code} ${c.nameZh}` : `${c.code} ${c.nameEn}`,
        value: c.loanCount,
      })),
    },
  ],
}))
</script>

<template>
  <div v-if="data">
    <div class="stat-grid">
      <el-card shadow="never" class="stat-card">
        <div class="stat-num">{{ data.totals.books }}</div>
        <div class="stat-label">{{ t('dash.books') }}</div>
      </el-card>
      <el-card shadow="never" class="stat-card">
        <div class="stat-num">{{ data.totals.copies }}</div>
        <div class="stat-label">{{ t('dash.copies') }}</div>
      </el-card>
      <el-card shadow="never" class="stat-card">
        <div class="stat-num">{{ data.totals.readers }}</div>
        <div class="stat-label">{{ t('dash.readers') }}</div>
      </el-card>
      <el-card shadow="never" class="stat-card">
        <div class="stat-num">{{ data.totals.activeLoans }}</div>
        <div class="stat-label">{{ t('dash.activeLoans') }}</div>
      </el-card>
      <el-card shadow="never" class="stat-card" :class="{ warn: data.totals.overdueLoans > 0 }">
        <div class="stat-num">{{ data.totals.overdueLoans }}</div>
        <div class="stat-label">{{ t('dash.overdueLoans') }}</div>
      </el-card>
      <el-card shadow="never" class="stat-card">
        <div class="stat-num">{{ data.totals.readyHolds }}</div>
        <div class="stat-label">{{ t('dash.readyHolds') }}</div>
      </el-card>
      <el-card shadow="never" class="stat-card" :class="{ warn: data.totals.unpaidFineCents > 0 }">
        <div class="stat-num">{{ money(data.totals.unpaidFineCents) }}</div>
        <div class="stat-label">{{ t('dash.unpaidFines') }}</div>
      </el-card>
      <el-card shadow="never" class="stat-card">
        <div class="stat-num">{{ data.overdueRate }}%</div>
        <div class="stat-label">{{ t('dash.overdueRate') }}</div>
      </el-card>
    </div>

    <el-row :gutter="16" class="charts-row">
      <el-col :xs="24" :lg="14">
        <el-card shadow="never">
          <h4>{{ t('dash.trend') }}</h4>
          <EChart :option="trendOption" height="320px" />
        </el-card>
      </el-col>
      <el-col :xs="24" :lg="10">
        <el-card shadow="never">
          <h4>{{ t('dash.categoryShare') }}</h4>
          <EChart :option="shareOption" height="320px" />
        </el-card>
      </el-col>
    </el-row>

    <el-card shadow="never" class="charts-row">
      <h4>{{ t('dash.topBooks') }}</h4>
      <EChart :option="topBooksOption" height="360px" />
    </el-card>
  </div>
</template>

<style scoped>
.stat-grid {
  display: grid;
  grid-template-columns: repeat(auto-fit, minmax(140px, 1fr));
  gap: 12px;
}

.stat-card {
  text-align: center;
  border-radius: 10px;
}

.stat-card.warn .stat-num {
  color: #c45656;
}

.stat-num {
  font-size: 24px;
  font-weight: 700;
}

.stat-label {
  color: #8a919f;
  font-size: 12px;
  margin-top: 2px;
}

.charts-row {
  margin-top: 16px;
}

.charts-row h4 {
  margin: 0 0 8px;
}
</style>
