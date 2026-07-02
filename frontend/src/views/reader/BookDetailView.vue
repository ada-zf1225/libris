<script setup lang="ts">
import { onMounted, ref, watch } from 'vue'
import { useRoute } from 'vue-router'
import { useI18n } from 'vue-i18n'
import { ElMessage } from 'element-plus'
import { Star, StarFilled, CollectionTag, Document } from '@element-plus/icons-vue'
import { discovery, type BookDetail } from '@/api/discovery'
import { my } from '@/api/my'
import { apiCategories, type Category } from '@/api/admin'
import { errorMessage } from '@/api/http'
import { money, date } from '@/utils/format'
import BookCover from '@/components/BookCover.vue'

const { t, locale } = useI18n()
const route = useRoute()

const detail = ref<BookDetail | null>(null)
const categories = ref<Category[]>([])
const loading = ref(false)
const holdBusy = ref(false)

function categoryLabel(id: number): string {
  const c = categories.value.find((x) => x.id === id)
  return c ? (locale.value === 'zh-CN' ? `${c.code} ${c.nameZh}` : `${c.code} ${c.nameEn}`) : '—'
}

async function load() {
  loading.value = true
  try {
    detail.value = await discovery.detail(Number(route.params.id))
  } catch (e) {
    ElMessage.error(errorMessage(e))
  } finally {
    loading.value = false
  }
}

onMounted(async () => {
  categories.value = await apiCategories().catch(() => [])
  await load()
})

watch(() => route.params.id, load)

async function placeHold() {
  if (!detail.value) return
  holdBusy.value = true
  try {
    const placed = await discovery.placeHold(detail.value.id)
    ElMessage.success(t('detail.holdPlaced', { position: placed.queuePosition }))
    await load()
  } catch (e) {
    ElMessage.error(errorMessage(e))
  } finally {
    holdBusy.value = false
  }
}

async function toggleFavorite() {
  if (!detail.value) return
  try {
    if (detail.value.amIFavorite) {
      await my.removeFavorite(detail.value.id)
    } else {
      await my.addFavorite(detail.value.id)
    }
    detail.value.amIFavorite = !detail.value.amIFavorite
  } catch (e) {
    ElMessage.error(errorMessage(e))
  }
}

async function copyBibtex() {
  if (!detail.value) return
  const d = detail.value
  const year = d.pubDate ? d.pubDate.slice(0, 4) : 'n.d.'
  const key = (d.author.split(/[,,、\s]/)[0] || 'book') + year
  const bibtex = `@book{${key},
  title     = {${d.title}},
  author    = {${d.author}},
  publisher = {${d.publisher}},
  year      = {${year}},
  isbn      = {${d.isbn}}
}`
  await navigator.clipboard.writeText(bibtex)
  ElMessage.success(t('detail.bibtexCopied'))
}

const statusTag = (status: string) =>
  ((
    { IN_LIBRARY: 'success', ON_LOAN: 'warning', ON_HOLD_SHELF: 'primary', LOST: 'danger' }
  )[status] ?? 'info') as 'success' | 'warning' | 'primary' | 'danger' | 'info'
</script>

<template>
  <div v-loading="loading" class="page-container">
    <template v-if="detail">
      <el-card shadow="never" class="detail-card">
        <div class="detail-head">
          <BookCover :src="detail.coverUrl" :title="detail.title" class="detail-cover" />
          <div class="detail-main">
            <h1>{{ detail.title }}</h1>
            <p class="author">{{ detail.author }}</p>

            <el-descriptions :column="2" size="small" class="meta">
              <el-descriptions-item :label="t('detail.publisher')">{{ detail.publisher }}</el-descriptions-item>
              <el-descriptions-item :label="t('detail.pubDate')">{{ date(detail.pubDate) }}</el-descriptions-item>
              <el-descriptions-item label="ISBN">{{ detail.isbn }}</el-descriptions-item>
              <el-descriptions-item :label="t('detail.price')">{{ money(detail.priceCents) }}</el-descriptions-item>
              <el-descriptions-item :label="t('detail.category')">{{ categoryLabel(detail.categoryId) }}</el-descriptions-item>
              <el-descriptions-item :label="t('detail.language')">{{ detail.language }}</el-descriptions-item>
            </el-descriptions>

            <div class="cta-row">
              <el-tag v-if="detail.availableCount > 0" type="success">
                {{ t('search.available', { n: detail.availableCount }) }}
              </el-tag>
              <template v-else>
                <el-tag type="danger">{{ t('search.allOut') }}</el-tag>
                <el-button
                  v-if="detail.holdable && !detail.amIHolding"
                  type="primary"
                  :loading="holdBusy"
                  :icon="CollectionTag"
                  @click="placeHold"
                >
                  {{ t('detail.hold') }}
                  <template v-if="detail.holdQueueLength > 0">
                    ({{ t('detail.holdQueue', { n: detail.holdQueueLength }) }})
                  </template>
                </el-button>
                <el-tag v-if="detail.amIHolding" type="primary" effect="plain">
                  {{ t('detail.holding') }}
                </el-tag>
              </template>

              <el-button
                :icon="detail.amIFavorite ? StarFilled : Star"
                :type="detail.amIFavorite ? 'warning' : 'default'"
                plain
                @click="toggleFavorite"
              >
                {{ detail.amIFavorite ? t('detail.unfavorite') : t('detail.favorite') }}
              </el-button>
              <el-button :icon="Document" plain @click="copyBibtex">{{ t('detail.bibtex') }}</el-button>
            </div>
          </div>
        </div>

        <template v-if="detail.intro">
          <h3>{{ t('detail.intro') }}</h3>
          <p class="intro">{{ detail.intro }}</p>
        </template>

        <h3>{{ t('detail.copies') }}</h3>
        <el-table :data="detail.copies" size="small">
          <el-table-column prop="barcode" :label="t('books.barcode')" width="120" />
          <el-table-column prop="callNumber" :label="t('detail.holdings')" width="150" />
          <el-table-column prop="location" :label="t('books.location')" min-width="140" />
          <el-table-column :label="t('table.status')" min-width="160">
            <template #default="{ row }">
              <el-tag size="small" :type="statusTag(row.status)">
                {{ t(`books.copyStatus.${row.status}`) }}
              </el-tag>
              <span v-if="row.dueAt" class="due-note">{{ t('detail.dueBack', { date: date(row.dueAt) }) }}</span>
            </template>
          </el-table-column>
        </el-table>
      </el-card>

      <template v-if="detail.related.length > 0">
        <h3 class="related-title">{{ t('detail.related') }}</h3>
        <div class="related-grid">
          <router-link
            v-for="r in detail.related"
            :key="r.id"
            :to="{ name: 'book-detail', params: { id: r.id } }"
            class="related-card"
          >
            <BookCover :src="r.coverUrl" :title="r.title" class="related-cover" />
            <p class="related-name">{{ r.title }}</p>
            <p class="related-author">{{ r.author }}</p>
          </router-link>
        </div>
      </template>
    </template>
  </div>
</template>

<style scoped>
.detail-card {
  border-radius: 10px;
}

.detail-head {
  display: flex;
  gap: 24px;
}

.detail-cover {
  width: 150px;
  height: 210px;
  flex-shrink: 0;
}

.detail-main {
  flex: 1;
  min-width: 0;
}

.detail-main h1 {
  margin: 0 0 4px;
  font-size: 26px;
  color: var(--libris-primary);
}

.author {
  color: #5c6470;
  margin: 0 0 14px;
}

.meta {
  max-width: 560px;
}

.cta-row {
  display: flex;
  align-items: center;
  gap: 10px;
  margin-top: 16px;
  flex-wrap: wrap;
}

.intro {
  color: #4a505a;
  line-height: 1.8;
  white-space: pre-line;
}

.due-note {
  margin-left: 8px;
  color: #b8823a;
  font-size: 12px;
}

.related-title {
  margin: 26px 0 12px;
}

.related-grid {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(130px, 1fr));
  gap: 16px;
}

.related-card {
  text-decoration: none;
  color: inherit;
}

.related-cover {
  width: 100%;
  aspect-ratio: 5 / 7;
}

.related-name {
  margin: 8px 0 2px;
  font-size: 13px;
  font-weight: 600;
  display: -webkit-box;
  -webkit-line-clamp: 2;
  line-clamp: 2;
  -webkit-box-orient: vertical;
  overflow: hidden;
}

.related-author {
  margin: 0;
  font-size: 12px;
  color: #8a919f;
}

@media (max-width: 640px) {
  .detail-head {
    flex-direction: column;
  }
}
</style>
