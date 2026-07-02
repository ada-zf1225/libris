<script setup lang="ts">
import { ref } from 'vue'

const props = defineProps<{ src: string | null; title: string }>()
const failed = ref(false)
const loaded = ref(false)

function initial(): string {
  return props.title ? props.title.charAt(0) : '书'
}
</script>

<template>
  <div class="cover">
    <img
      v-if="src && !failed"
      :src="src"
      :alt="title"
      loading="lazy"
      :class="{ loaded }"
      @load="loaded = true"
      @error="failed = true"
    />
    <div v-else class="fallback">
      <span>{{ initial() }}</span>
    </div>
  </div>
</template>

<style scoped>
.cover {
  border-radius: 6px;
  overflow: hidden;
  background: #eef2f7;
}

.cover img {
  width: 100%;
  height: 100%;
  object-fit: cover;
  display: block;
  opacity: 0;
  transform: scale(1.03);
  transition: opacity 0.45s ease, transform 0.6s ease;
}

.cover img.loaded {
  opacity: 1;
  transform: none;
}

.fallback {
  width: 100%;
  height: 100%;
  display: flex;
  align-items: center;
  justify-content: center;
  background: linear-gradient(160deg, #003e74 0%, #1f5a94 100%);
  color: rgba(255, 255, 255, 0.85);
  font-size: 26px;
  font-weight: 600;
}
</style>
