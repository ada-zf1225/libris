<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue'
import { useI18n } from 'vue-i18n'
import { ElMessage, ElMessageBox, type FormInstance } from 'element-plus'
import { Search, Plus } from '@element-plus/icons-vue'
import {
  adminBooks,
  apiCategories,
  type BookView,
  type BookInput,
  type Category,
  type CopyView,
} from '@/api/admin'
import { errorMessage } from '@/api/http'
import { money, date } from '@/utils/format'

const { t, locale } = useI18n()

const rows = ref<BookView[]>([])
const total = ref(0)
const page = ref(0)
const size = ref(20)
const q = ref('')
const categoryId = ref<number | null>(null)
const loading = ref(false)
const categories = ref<Category[]>([])

const categoryName = computed(() => {
  const map = new Map(categories.value.map((c) => [c.id, c]))
  return (id: number) => {
    const c = map.get(id)
    if (!c) return '—'
    return locale.value === 'zh-CN' ? `${c.code} ${c.nameZh}` : `${c.code} ${c.nameEn}`
  }
})

async function load() {
  loading.value = true
  try {
    const result = await adminBooks.list({
      q: q.value || undefined,
      categoryId: categoryId.value ?? undefined,
      page: page.value,
      size: size.value,
    })
    rows.value = result.content
    total.value = result.totalElements
  } catch (e) {
    ElMessage.error(errorMessage(e))
  } finally {
    loading.value = false
  }
}

onMounted(async () => {
  try {
    categories.value = await apiCategories()
  } catch {
    /* selector stays empty */
  }
  await load()
})

// ---------- create / edit dialog ----------

const dialogVisible = ref(false)
const editingId = ref<number | null>(null)
const formRef = ref<FormInstance>()
const form = reactive<BookInput & { priceYuan: number | null }>({
  title: '',
  author: '',
  publisher: '',
  isbn: '',
  intro: '',
  language: '中文',
  priceCents: null,
  priceYuan: null,
  pubDate: null,
  categoryId: 9,
  coverUrl: '',
})

function openCreate() {
  editingId.value = null
  Object.assign(form, {
    title: '',
    author: '',
    publisher: '',
    isbn: '',
    intro: '',
    language: '中文',
    priceCents: null,
    priceYuan: null,
    pubDate: null,
    categoryId: 9,
    coverUrl: '',
  })
  dialogVisible.value = true
}

function openEdit(row: BookView) {
  editingId.value = row.id
  Object.assign(form, {
    title: row.title,
    author: row.author,
    publisher: row.publisher,
    isbn: row.isbn,
    intro: row.intro ?? '',
    language: row.language,
    priceCents: row.priceCents,
    priceYuan: row.priceCents == null ? null : row.priceCents / 100,
    pubDate: row.pubDate,
    categoryId: row.categoryId,
    coverUrl: row.coverUrl ?? '',
  })
  dialogVisible.value = true
}

async function submit() {
  const valid = await formRef.value?.validate().catch(() => false)
  if (!valid) return
  const body: BookInput = {
    title: form.title,
    author: form.author,
    publisher: form.publisher,
    isbn: form.isbn,
    intro: form.intro || undefined,
    language: form.language,
    priceCents: form.priceYuan == null ? null : Math.round(form.priceYuan * 100),
    pubDate: form.pubDate || null,
    categoryId: form.categoryId,
    coverUrl: form.coverUrl || null,
  }
  try {
    if (editingId.value == null) {
      await adminBooks.create(body)
    } else {
      await adminBooks.update(editingId.value, body)
    }
    dialogVisible.value = false
    await load()
  } catch (e) {
    ElMessage.error(errorMessage(e))
  }
}

async function remove(row: BookView) {
  try {
    await ElMessageBox.confirm(t('books.deleteConfirm', { title: row.title }), t('books.delete'), {
      type: 'warning',
    })
  } catch {
    return
  }
  try {
    await adminBooks.remove(row.id)
    await load()
  } catch (e) {
    ElMessage.error(errorMessage(e))
  }
}

// ---------- copies drawer ----------

const drawerVisible = ref(false)
const drawerBook = ref<BookView | null>(null)
const copies = ref<CopyView[]>([])
const addCount = ref(1)
const addLocation = ref('总馆一层综合区')

async function openCopies(row: BookView) {
  drawerBook.value = row
  drawerVisible.value = true
  copies.value = await adminBooks.copies(row.id).catch(() => [])
}

async function addCopies() {
  if (!drawerBook.value) return
  try {
    await adminBooks.addCopies(drawerBook.value.id, addCount.value, addLocation.value)
    copies.value = await adminBooks.copies(drawerBook.value.id)
  } catch (e) {
    ElMessage.error(errorMessage(e))
  }
}

async function removeCopy(copy: CopyView) {
  try {
    await ElMessageBox.confirm(
      t('books.removeCopyConfirm', { barcode: copy.barcode }),
      t('books.delete'),
      { type: 'warning' },
    )
  } catch {
    return
  }
  try {
    await adminBooks.removeCopy(copy.id)
    if (drawerBook.value) copies.value = await adminBooks.copies(drawerBook.value.id)
  } catch (e) {
    ElMessage.error(errorMessage(e))
  }
}

const copyTagType = (status: CopyView['status']) =>
  ({ IN_LIBRARY: 'success', ON_LOAN: 'warning', ON_HOLD_SHELF: 'primary', LOST: 'danger' })[
    status
  ] as 'success' | 'warning' | 'primary' | 'danger'
</script>

<template>
  <el-card shadow="never">
    <div class="toolbar">
      <el-input
        v-model="q"
        :placeholder="t('books.searchPlaceholder')"
        :prefix-icon="Search"
        clearable
        class="search-input"
        @keyup.enter="page = 0; load()"
        @clear="page = 0; load()"
      />
      <el-select
        v-model="categoryId"
        :placeholder="t('books.allCategories')"
        clearable
        class="category-select"
        @change="page = 0; load()"
      >
        <el-option
          v-for="c in categories"
          :key="c.id"
          :value="c.id"
          :label="locale === 'zh-CN' ? `${c.code} ${c.nameZh}` : `${c.code} ${c.nameEn}`"
        />
      </el-select>
      <div class="spacer" />
      <el-button type="primary" :icon="Plus" @click="openCreate">{{ t('books.add') }}</el-button>
    </div>

    <el-table v-loading="loading" :data="rows">
      <el-table-column prop="title" :label="t('books.fields.title')" min-width="220" show-overflow-tooltip />
      <el-table-column prop="author" :label="t('books.fields.author')" min-width="140" show-overflow-tooltip />
      <el-table-column prop="publisher" :label="t('books.fields.publisher')" min-width="150" show-overflow-tooltip />
      <el-table-column prop="isbn" label="ISBN" width="140" />
      <el-table-column :label="t('books.fields.category')" width="150">
        <template #default="{ row }">{{ categoryName(row.categoryId) }}</template>
      </el-table-column>
      <el-table-column :label="t('books.fields.price')" width="100">
        <template #default="{ row }">{{ money(row.priceCents) }}</template>
      </el-table-column>
      <el-table-column :label="t('books.fields.pubDate')" width="110">
        <template #default="{ row }">{{ date(row.pubDate) }}</template>
      </el-table-column>
      <el-table-column :label="t('table.actions')" width="200" fixed="right">
        <template #default="{ row }">
          <el-button link type="primary" size="small" @click="openCopies(row as BookView)">
            {{ t('books.copies') }}
          </el-button>
          <el-button link type="primary" size="small" @click="openEdit(row as BookView)">
            {{ t('books.edit') }}
          </el-button>
          <el-button link type="danger" size="small" @click="remove(row as BookView)">
            {{ t('books.delete') }}
          </el-button>
        </template>
      </el-table-column>
    </el-table>

    <el-pagination
      class="pager"
      layout="total, prev, pager, next"
      :total="total"
      :page-size="size"
      :current-page="page + 1"
      @current-change="(p: number) => { page = p - 1; load() }"
    />
  </el-card>

  <!-- create / edit -->
  <el-dialog v-model="dialogVisible" :title="editingId == null ? t('books.add') : t('books.edit')" width="640">
    <el-form ref="formRef" :model="form" label-width="110px">
      <el-form-item :label="t('books.fields.title')" prop="title" :rules="{ required: true, message: t('form.required') }">
        <el-input v-model="form.title" />
      </el-form-item>
      <el-form-item :label="t('books.fields.author')" prop="author" :rules="{ required: true, message: t('form.required') }">
        <el-input v-model="form.author" />
      </el-form-item>
      <el-form-item :label="t('books.fields.publisher')" prop="publisher" :rules="{ required: true, message: t('form.required') }">
        <el-input v-model="form.publisher" />
      </el-form-item>
      <el-row :gutter="12">
        <el-col :span="12">
          <el-form-item label="ISBN" prop="isbn" :rules="{ required: true, message: t('form.required') }">
            <el-input v-model="form.isbn" />
          </el-form-item>
        </el-col>
        <el-col :span="12">
          <el-form-item :label="t('books.fields.language')" prop="language" :rules="{ required: true, message: t('form.required') }">
            <el-select v-model="form.language">
              <el-option value="中文" label="中文" />
              <el-option value="英文" label="英文 / English" />
            </el-select>
          </el-form-item>
        </el-col>
      </el-row>
      <el-row :gutter="12">
        <el-col :span="8">
          <el-form-item :label="t('books.fields.price')">
            <el-input-number v-model="form.priceYuan" :min="0" :precision="2" :controls="false" style="width: 100%" />
          </el-form-item>
        </el-col>
        <el-col :span="8">
          <el-form-item :label="t('books.fields.pubDate')">
            <el-date-picker v-model="form.pubDate" type="date" value-format="YYYY-MM-DD" style="width: 100%" />
          </el-form-item>
        </el-col>
        <el-col :span="8">
          <el-form-item :label="t('books.fields.category')" prop="categoryId" :rules="{ required: true, message: t('form.required') }">
            <el-select v-model="form.categoryId">
              <el-option
                v-for="c in categories"
                :key="c.id"
                :value="c.id"
                :label="locale === 'zh-CN' ? `${c.code} ${c.nameZh}` : `${c.code} ${c.nameEn}`"
              />
            </el-select>
          </el-form-item>
        </el-col>
      </el-row>
      <el-form-item :label="t('books.fields.intro')">
        <el-input v-model="form.intro" type="textarea" :rows="3" maxlength="4000" />
      </el-form-item>
      <el-form-item :label="t('books.fields.cover')">
        <el-input v-model="form.coverUrl" />
      </el-form-item>
    </el-form>
    <template #footer>
      <el-button @click="dialogVisible = false">{{ t('common.cancel') }}</el-button>
      <el-button type="primary" @click="submit">
        {{ editingId == null ? t('form.create') : t('form.save') }}
      </el-button>
    </template>
  </el-dialog>

  <!-- copies drawer -->
  <el-drawer v-model="drawerVisible" :title="drawerBook ? t('books.copiesOf', { title: drawerBook.title }) : ''" size="520">
    <div class="copies-toolbar">
      <el-input-number v-model="addCount" :min="1" :max="50" />
      <el-input v-model="addLocation" class="location-input" :placeholder="t('books.location')" />
      <el-button type="primary" @click="addCopies">{{ t('books.addCopies') }}</el-button>
    </div>
    <el-table :data="copies" size="small">
      <el-table-column prop="barcode" :label="t('books.barcode')" width="110" />
      <el-table-column prop="callNumber" :label="t('books.callNumber')" width="130" />
      <el-table-column prop="location" :label="t('books.location')" min-width="120" />
      <el-table-column :label="t('table.status')" width="90">
        <template #default="{ row }">
          <el-tag size="small" :type="copyTagType(row.status)">
            {{ t(`books.copyStatus.${row.status}`) }}
          </el-tag>
        </template>
      </el-table-column>
      <el-table-column :label="t('table.actions')" width="80">
        <template #default="{ row }">
          <el-button link type="danger" size="small" @click="removeCopy(row as CopyView)">
            {{ t('books.delete') }}
          </el-button>
        </template>
      </el-table-column>
    </el-table>
  </el-drawer>
</template>

<style scoped>
.toolbar {
  display: flex;
  gap: 10px;
  margin-bottom: 14px;
}

.search-input {
  width: 260px;
}

.category-select {
  width: 220px;
}

.spacer {
  flex: 1;
}

.pager {
  margin-top: 14px;
  justify-content: flex-end;
}

.copies-toolbar {
  display: flex;
  gap: 8px;
  margin-bottom: 12px;
}

.location-input {
  flex: 1;
}
</style>
