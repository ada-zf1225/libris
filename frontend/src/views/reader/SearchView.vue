<script setup lang="ts">
import { computed, onMounted, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { useI18n } from 'vue-i18n'
import { ElMessage } from 'element-plus'
import { Search } from '@element-plus/icons-vue'
import {
  discovery,
  type BookHit,
  type PaperHit,
  type Facets,
  type Suggestion,
} from '@/api/discovery'
import { apiCategories, type Category } from '@/api/admin'
import { errorMessage } from '@/api/http'
import BookCover from '@/components/BookCover.vue'

const { t, locale } = useI18n()
const route = useRoute()
const router = useRouter()

const tab = ref<'books' | 'papers'>('books')
const q = ref((route.query.q as string) ?? '')
const categoryId = ref<number | null>(null)
const language = ref<string | null>(null)
const decade = ref<string | null>(null)
const availableOnly = ref(false)
const sort = ref<'relevance' | 'newest' | 'title'>('relevance')
const page = ref(0)
const size = 10

const books = ref<BookHit[]>([])
const papers = ref<PaperHit[]>([])
const facets = ref<Facets | null>(null)
const total = ref(0)
const loading = ref(false)
const categories = ref<Category[]>([])

const categoryLabel = computed(() => {
  const map = new Map(categories.value.map((c) => [c.id, c]))
  return (id: number | string) => {
    const c = map.get(Number(id))
    return c ? (locale.value === 'zh-CN' ? `${c.code} ${c.nameZh}` : `${c.code} ${c.nameEn}`) : String(id)
  }
})

async function run() {
  loading.value = true
  try {
    if (tab.value === 'books') {
      const yearFrom = decade.value ? Number(decade.value) : null
      const yearTo = decade.value ? Number(decade.value) + 9 : null
      const result = await discovery.books({
        q: q.value || undefined,
        categoryId: categoryId.value,
        language: language.value,
        yearFrom,
        yearTo,
        availableOnly: availableOnly.value,
        sort: sort.value,
        page: page.value,
        size,
      })
      books.value = result.content
      facets.value = result.facets
      total.value = result.total
    } else {
      const result = await discovery.papers({ q: q.value || undefined, page: page.value, size })
      papers.value = result.content
      total.value = result.total
      facets.value = null
    }
  } catch (e) {
    ElMessage.error(errorMessage(e))
  } finally {
    loading.value = false
  }
}

function newSearch() {
  page.value = 0
  router.replace({ query: q.value ? { q: q.value } : {} })
  run()
}

function clearFilters() {
  categoryId.value = null
  language.value = null
  decade.value = null
  availableOnly.value = false
  page.value = 0
  run()
}

watch([categoryId, language, decade, availableOnly, sort], () => {
  page.value = 0
  run()
})

watch(tab, () => {
  page.value = 0
  run()
})

onMounted(async () => {
  categories.value = await apiCategories().catch(() => [])
  await run()
})

// suggestions
const fetchSuggestions = async (query: string, cb: (results: { value: string }[]) => void) => {
  if (!query || query.length < 2) {
    cb([])
    return
  }
  const list: Suggestion[] = await discovery.suggest(query).catch(() => [])
  cb(list.map((s) => ({ value: s.text })))
}

const hasFilter = computed(
  () => categoryId.value !== null || language.value || decade.value || availableOnly.value,
)
</script>

<template>
  <div class="search-page">
    <div class="search-hero">
      <div class="hero-inner">
        <el-autocomplete
          v-model="q"
          :fetch-suggestions="fetchSuggestions"
          :placeholder="t('search.placeholder')"
          :prefix-icon="Search"
          size="large"
          clearable
          class="search-box glow-focus"
          @select="newSearch"
          @keyup.enter="newSearch"
          @clear="newSearch"
        >
          <template #append>
            <el-button :icon="Search" @click="newSearch">{{ t('common.search') }}</el-button>
          </template>
        </el-autocomplete>
        <el-tabs v-model="tab" class="scope-tabs">
          <el-tab-pane :label="t('search.tabBooks')" name="books" />
          <el-tab-pane :label="t('search.tabPapers')" name="papers" />
        </el-tabs>
      </div>
    </div>

    <div class="page-container results-area">
      <el-row :gutter="20">
        <!-- facets -->
        <el-col v-if="tab === 'books'" :xs="24" :sm="7" :md="6">
          <el-card shadow="never" class="facet-card">
            <div class="facet-head">
              <span>{{ t('search.facets.title') }}</span>
              <el-button v-if="hasFilter" link type="primary" size="small" @click="clearFilters">
                {{ t('search.facets.clear') }}
              </el-button>
            </div>

            <template v-if="facets">
              <h5>{{ t('search.facets.availability') }}</h5>
              <el-checkbox v-model="availableOnly">
                {{ t('search.facets.availableOnly') }}({{ facets.availableCount }})
              </el-checkbox>

              <h5>{{ t('search.facets.category') }}</h5>
              <ul class="facet-list">
                <li
                  v-for="(n, key) in facets.byCategory"
                  :key="key"
                  :class="{ active: categoryId === Number(key) }"
                  @click="categoryId = categoryId === Number(key) ? null : Number(key)"
                >
                  <span class="facet-label">{{ categoryLabel(key) }}</span>
                  <span class="facet-count">{{ n }}</span>
                </li>
              </ul>

              <h5>{{ t('search.facets.language') }}</h5>
              <ul class="facet-list">
                <li
                  v-for="(n, key) in facets.byLanguage"
                  :key="key"
                  :class="{ active: language === key }"
                  @click="language = language === key ? null : String(key)"
                >
                  <span class="facet-label">{{ key }}</span>
                  <span class="facet-count">{{ n }}</span>
                </li>
              </ul>

              <h5>{{ t('search.facets.decade') }}</h5>
              <ul class="facet-list">
                <li
                  v-for="(n, key) in facets.byDecade"
                  :key="key"
                  :class="{ active: decade === key }"
                  @click="decade = decade === key ? null : String(key)"
                >
                  <span class="facet-label">{{ key }}{{ t('search.decadeSuffix') }}</span>
                  <span class="facet-count">{{ n }}</span>
                </li>
              </ul>
            </template>
          </el-card>
        </el-col>

        <!-- results -->
        <el-col :xs="24" :sm="tab === 'books' ? 17 : 24" :md="tab === 'books' ? 18 : 24">
          <div class="result-toolbar">
            <span class="result-count">{{ t('search.results', { n: total }) }}</span>
            <el-select v-if="tab === 'books'" v-model="sort" size="small" class="sort-select">
              <el-option value="relevance" :label="t('search.sort.relevance')" />
              <el-option value="newest" :label="t('search.sort.newest')" />
              <el-option value="title" :label="t('search.sort.title')" />
            </el-select>
          </div>

          <div class="result-list">
            <template v-if="loading">
              <el-card v-for="i in 4" :key="i" shadow="never" class="skeleton-card">
                <el-skeleton animated :rows="2" />
              </el-card>
            </template>
            <template v-else-if="tab === 'books'">
              <el-empty v-if="!loading && books.length === 0" :description="t('search.noResults')" />
              <router-link
                v-for="hit in books"
                :key="hit.id"
                :to="{ name: 'book-detail', params: { id: hit.id } }"
                class="hit-card stagger-in"
              >
                <BookCover :src="hit.coverUrl" :title="hit.title" class="hit-cover" />
                <div class="hit-body">
                  <h3 class="hit-title">{{ hit.title }}</h3>
                  <p class="hit-meta">{{ hit.author }} · {{ hit.publisher }}</p>
                  <p v-if="hit.intro" class="hit-intro">{{ hit.intro }}</p>
                  <div class="hit-tags">
                    <el-tag v-if="hit.available > 0" type="success" size="small">
                      {{ t('search.available', { n: hit.available }) }}
                    </el-tag>
                    <el-tag v-else type="danger" size="small">{{ t('search.allOut') }}</el-tag>
                    <el-tag size="small" effect="plain">{{ categoryLabel(hit.categoryId) }}</el-tag>
                    <el-tag size="small" effect="plain">{{ hit.language }}</el-tag>
                  </div>
                </div>
              </router-link>
            </template>

            <template v-else>
              <el-empty v-if="!loading && papers.length === 0" :description="t('search.noResults')" />
              <div v-for="p in papers" :key="p.id" class="hit-card paper-card stagger-in">
                <div class="hit-body">
                  <h3 class="hit-title">{{ p.title }}</h3>
                  <p class="hit-meta">{{ p.authors }}</p>
                  <p class="hit-meta venue">{{ p.venue }} · {{ p.year }}<template v-if="p.pages"> · pp. {{ p.pages }}</template></p>
                  <p v-if="p.abstractText" class="hit-intro">{{ p.abstractText }}</p>
                  <div class="hit-tags">
                    <el-tag v-if="p.doi" size="small" effect="plain">DOI: {{ p.doi }}</el-tag>
                    <a v-if="p.url" :href="p.url" target="_blank" rel="noopener" class="src-link">
                      {{ t('detail.viewSource') }} ↗
                    </a>
                  </div>
                </div>
              </div>
            </template>
          </div>

          <el-pagination
            v-if="total > size"
            class="pager"
            layout="prev, pager, next"
            :total="total"
            :page-size="size"
            :current-page="page + 1"
            @current-change="(p: number) => { page = p - 1; run() }"
          />
        </el-col>
      </el-row>
    </div>
  </div>
</template>

<style scoped>
.search-hero {
  background: linear-gradient(150deg, #002a52 0%, #003e74 70%, #14508a 100%);
  padding: 36px 24px 0;
}

.hero-inner {
  max-width: 860px;
  margin: 0 auto;
}

.search-box {
  width: 100%;
}

.scope-tabs {
  margin-top: 10px;
  --el-text-color-primary: #cfe0f0;
}

.scope-tabs :deep(.el-tabs__item) {
  color: #9fb9d4;
}

.scope-tabs :deep(.el-tabs__item.is-active) {
  color: #fff;
}

.scope-tabs :deep(.el-tabs__nav-wrap::after) {
  background: transparent;
}

.results-area {
  padding-top: 20px;
}

.facet-card {
  border-radius: 10px;
}

.facet-head {
  display: flex;
  justify-content: space-between;
  align-items: center;
  font-weight: 600;
}

.facet-card h5 {
  margin: 16px 0 6px;
  color: #5c6470;
}

.facet-list {
  list-style: none;
  padding: 0;
  margin: 0;
}

.facet-list li {
  display: flex;
  justify-content: space-between;
  padding: 4px 6px;
  border-radius: 6px;
  cursor: pointer;
  font-size: 13px;
}

.facet-list li:hover {
  background: #eef2f7;
}

.facet-list li.active {
  background: #e1ecf7;
  color: var(--libris-primary);
  font-weight: 600;
}

.facet-label {
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.facet-count {
  color: #9aa3af;
}

.result-toolbar {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 10px;
}

.result-count {
  color: #5c6470;
  font-size: 14px;
}

.sort-select {
  width: 130px;
}

.hit-card {
  display: flex;
  gap: 16px;
  background: var(--el-bg-color);
  border: 1px solid var(--libris-card-border);
  border-radius: 10px;
  padding: 16px;
  margin-bottom: 12px;
  text-decoration: none;
  color: inherit;
  transition: box-shadow 0.15s ease, transform 0.15s ease;
}

.skeleton-card {
  margin-bottom: 12px;
  border-radius: 10px;
}

.hit-card:hover {
  box-shadow: 0 4px 16px rgba(0, 42, 82, 0.1);
  transform: translateY(-1px);
}

.hit-cover {
  width: 72px;
  height: 100px;
  flex-shrink: 0;
}

.hit-title {
  margin: 0 0 4px;
  font-size: 17px;
  color: var(--libris-primary);
}

.hit-meta {
  margin: 0 0 6px;
  color: #5c6470;
  font-size: 13px;
}

.venue {
  font-style: italic;
}

.hit-intro {
  margin: 0 0 8px;
  color: #7b828e;
  font-size: 13px;
  display: -webkit-box;
  -webkit-line-clamp: 2;
  line-clamp: 2;
  -webkit-box-orient: vertical;
  overflow: hidden;
}

.hit-tags {
  display: flex;
  gap: 6px;
  align-items: center;
  flex-wrap: wrap;
}

.src-link {
  font-size: 13px;
  color: var(--libris-primary);
}

.pager {
  margin-top: 16px;
  justify-content: center;
}
</style>
