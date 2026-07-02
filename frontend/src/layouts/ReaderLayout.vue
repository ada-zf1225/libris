<script setup lang="ts">
import { useRouter } from 'vue-router'
import { useI18n } from 'vue-i18n'
import { ArrowDown } from '@element-plus/icons-vue'
import { useAuthStore } from '@/stores/auth'
import LanguageSwitcher from '@/components/LanguageSwitcher.vue'
import NotificationBell from '@/components/NotificationBell.vue'

const { t } = useI18n()
const auth = useAuthStore()
const router = useRouter()

async function handleCommand(command: string) {
  if (command === 'logout') {
    await auth.logout()
    router.push({ name: 'login' })
  }
}
</script>

<template>
  <div class="reader-shell">
    <header class="topbar">
      <div class="topbar-inner">
        <router-link :to="{ name: 'home' }" class="brand">
          <span class="brand-name">Libris</span>
          <span class="brand-sub">{{ t('brand.library') }}</span>
        </router-link>

        <nav class="actions">
          <router-link :to="{ name: 'search' }" class="nav-link">{{ t('nav.discovery') }}</router-link>
          <router-link :to="{ name: 'my-library' }" class="nav-link">{{ t('nav.myLibrary') }}</router-link>
          <NotificationBell class="on-dark" />
          <LanguageSwitcher class="on-dark" />
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
        </nav>
      </div>
    </header>

    <main class="content">
      <router-view />
    </main>
  </div>
</template>

<style scoped>
.reader-shell {
  min-height: 100vh;
  display: flex;
  flex-direction: column;
}

.topbar {
  background: linear-gradient(90deg, #002a52, #003e74);
  color: #fff;
}

.topbar-inner {
  max-width: 1200px;
  margin: 0 auto;
  padding: 0 24px;
  height: 60px;
  display: flex;
  align-items: center;
  justify-content: space-between;
}

.brand {
  display: flex;
  align-items: baseline;
  gap: 10px;
  color: #fff;
  text-decoration: none;
}

.brand-name {
  font-size: 24px;
  font-weight: 700;
  letter-spacing: 1px;
}

.brand-sub {
  font-size: 13px;
  opacity: 0.75;
}

.actions {
  display: flex;
  align-items: center;
  gap: 20px;
}

.user-trigger {
  display: inline-flex;
  align-items: center;
  gap: 4px;
  cursor: pointer;
  color: #fff;
  font-size: 14px;
}

.on-dark {
  color: #fff;
}

.nav-link {
  color: rgba(255, 255, 255, 0.85);
  text-decoration: none;
  font-size: 14px;
}

.nav-link:hover,
.nav-link.router-link-active {
  color: #fff;
}

.content {
  flex: 1;
}
</style>
