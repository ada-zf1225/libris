<script setup lang="ts">
import { computed } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { useI18n } from 'vue-i18n'
import {
  ArrowDown,
  DataAnalysis,
  Collection,
  User,
  SetUp,
  ShoppingCart,
  Document,
  Refresh,
} from '@element-plus/icons-vue'
import { useAuthStore } from '@/stores/auth'
import LanguageSwitcher from '@/components/LanguageSwitcher.vue'
import NotificationBell from '@/components/NotificationBell.vue'

const { t } = useI18n()
const auth = useAuthStore()
const router = useRouter()
const route = useRoute()

const activeMenu = computed(() => route.path)

async function handleCommand(command: string) {
  if (command === 'logout') {
    await auth.logout()
    router.push({ name: 'login' })
  }
}
</script>

<template>
  <el-container class="admin-shell">
    <el-aside width="220px" class="sidebar">
      <div class="side-brand">
        <span class="brand-name">Libris</span>
        <span class="brand-tag">Staff</span>
      </div>
      <el-menu :default-active="activeMenu" router class="side-menu">
        <el-menu-item index="/admin">
          <el-icon><DataAnalysis /></el-icon>
          <span>{{ t('nav.admin.dashboard') }}</span>
        </el-menu-item>
        <el-menu-item index="/admin/circulation">
          <el-icon><Refresh /></el-icon>
          <span>{{ t('nav.admin.circulation') }}</span>
        </el-menu-item>
        <el-menu-item index="/admin/books">
          <el-icon><Collection /></el-icon>
          <span>{{ t('nav.admin.books') }}</span>
        </el-menu-item>
        <el-menu-item index="/admin/readers">
          <el-icon><User /></el-icon>
          <span>{{ t('nav.admin.readers') }}</span>
        </el-menu-item>
        <el-menu-item index="/admin/policies">
          <el-icon><SetUp /></el-icon>
          <span>{{ t('nav.admin.policies') }}</span>
        </el-menu-item>
        <el-menu-item index="/admin/suggestions">
          <el-icon><ShoppingCart /></el-icon>
          <span>{{ t('nav.admin.suggestions') }}</span>
        </el-menu-item>
        <el-menu-item index="/admin/logs">
          <el-icon><Document /></el-icon>
          <span>{{ t('nav.admin.logs') }}</span>
        </el-menu-item>
      </el-menu>
    </el-aside>

    <el-container>
      <el-header class="admin-header">
        <div />
        <div class="header-actions">
          <NotificationBell />
          <LanguageSwitcher />
          <el-dropdown v-if="auth.user" trigger="click" @command="handleCommand">
            <span class="user-trigger">
              {{ auth.user.displayName }}
              <el-icon><ArrowDown /></el-icon>
            </span>
            <template #dropdown>
              <el-dropdown-menu>
                <el-dropdown-item command="logout">{{ t('common.logout') }}</el-dropdown-item>
              </el-dropdown-menu>
            </template>
          </el-dropdown>
        </div>
      </el-header>
      <el-main class="admin-main">
        <router-view />
      </el-main>
    </el-container>
  </el-container>
</template>

<style scoped>
.admin-shell {
  min-height: 100vh;
}

.sidebar {
  background: #001f3d;
  color: #fff;
}

.side-brand {
  display: flex;
  align-items: baseline;
  gap: 8px;
  padding: 18px 20px;
}

.brand-name {
  color: #fff;
  font-size: 22px;
  font-weight: 700;
  letter-spacing: 1px;
}

.brand-tag {
  color: #7ea4c8;
  font-size: 12px;
  text-transform: uppercase;
}

.side-menu {
  border-right: none;
  background: transparent;
  --el-menu-text-color: #b6c8da;
  --el-menu-hover-bg-color: rgba(255, 255, 255, 0.06);
  --el-menu-active-color: #fff;
}

.side-menu .el-menu-item.is-active {
  background: rgba(255, 255, 255, 0.1);
}

.admin-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  background: #fff;
  border-bottom: 1px solid #e5e8ec;
}

.header-actions {
  display: flex;
  align-items: center;
  gap: 20px;
}

.user-trigger {
  display: inline-flex;
  align-items: center;
  gap: 4px;
  cursor: pointer;
  font-size: 14px;
}

.admin-main {
  background: var(--libris-bg);
}
</style>
