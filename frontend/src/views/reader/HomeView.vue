<script setup lang="ts">
import { onMounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import { useI18n } from 'vue-i18n'
import { Search } from '@element-plus/icons-vue'
import { discovery, type BookHit } from '@/api/discovery'
import BookCover from '@/components/BookCover.vue'

const { t } = useI18n()
const router = useRouter()

const q = ref('')
const featured = ref<BookHit[]>([])

function go() {
  router.push({ name: 'search', query: q.value ? { q: q.value } : {} })
}

onMounted(async () => {
  const result = await discovery
    .books({ sort: 'newest', page: 0, size: 12 })
    .catch(() => null)
  featured.value = result?.content ?? []
})
</script>

<template>
  <div>
    <section class="hero">
      <h1 class="hero-in">{{ t('brand.name') }} · {{ t('brand.library') }}</h1>
      <p class="hero-tagline">{{ t('brand.tagline') }}</p>
      <el-input
        v-model="q"
        :placeholder="t('search.placeholder')"
        :prefix-icon="Search"
        size="large"
        class="hero-search hero-in-late glow-focus"
        @keyup.enter="go"
      >
        <template #append>
          <el-button :icon="Search" @click="go">{{ t('common.search') }}</el-button>
        </template>
      </el-input>
    </section>

    <div class="page-container">
      <div class="shelf-grid">
        <router-link
          v-for="book in featured"
          :key="book.id"
          :to="{ name: 'book-detail', params: { id: book.id } }"
          class="shelf-card stagger-in"
        >
          <BookCover :src="book.coverUrl" :title="book.title" class="shelf-cover" />
          <p class="shelf-name">{{ book.title }}</p>
          <p class="shelf-author">{{ book.author }}</p>
        </router-link>
      </div>
    </div>
  </div>
</template>

<style scoped>
.hero {
  background:
    radial-gradient(900px 400px at 80% -10%, rgba(255, 255, 255, 0.1), transparent),
    linear-gradient(150deg, #002a52 0%, #003e74 70%, #14508a 100%);
  color: #fff;
  text-align: center;
  padding: 56px 24px 64px;
}

.hero h1 {
  margin: 0 0 6px;
  font-size: 34px;
  letter-spacing: 1px;
}

.hero-tagline {
  opacity: 0.75;
  margin: 0 0 24px;
}

.hero-search {
  max-width: 620px;
}

.shelf-grid {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(140px, 1fr));
  gap: 18px;
}

.shelf-card {
  text-decoration: none;
  color: inherit;
}

.shelf-cover {
  width: 100%;
  aspect-ratio: 5 / 7;
  box-shadow: 0 2px 10px rgba(0, 42, 82, 0.12);
}

.shelf-name {
  margin: 8px 0 2px;
  font-size: 13px;
  font-weight: 600;
  display: -webkit-box;
  -webkit-line-clamp: 2;
  line-clamp: 2;
  -webkit-box-orient: vertical;
  overflow: hidden;
}

.shelf-author {
  margin: 0;
  font-size: 12px;
  color: #8a919f;
}
</style>
