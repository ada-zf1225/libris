<script setup lang="ts">
import { computed } from 'vue'
import { Sunny, Moon, Monitor } from '@element-plus/icons-vue'
import { themeMode, cycleTheme } from '@/composables/theme'

const icon = computed(() => (themeMode.value === 'light' ? Sunny : themeMode.value === 'dark' ? Moon : Monitor))
const title = computed(() => themeMode.value)
</script>

<template>
  <button class="theme-toggle" :title="title" @click="cycleTheme">
    <el-icon><component :is="icon" /></el-icon>
  </button>
</template>

<style scoped>
.theme-toggle {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  width: 30px;
  height: 30px;
  border: none;
  border-radius: 8px;
  background: transparent;
  color: inherit;
  cursor: pointer;
  font-size: 17px;
  transition: background 0.2s ease, transform 0.2s ease;
}

.theme-toggle:hover {
  background: rgba(127, 127, 127, 0.18);
  transform: rotate(12deg) scale(1.08);
}
</style>
