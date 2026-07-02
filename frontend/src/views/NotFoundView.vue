<script setup lang="ts">
import { useRouter } from 'vue-router'

const router = useRouter()
</script>

<template>
  <div class="nf-wrap">
    <el-result icon="warning" title="404">
      <template #extra>
        <el-button type="primary" @click="router.push('/')">Libris</el-button>
      </template>
    </el-result>
  </div>
</template>

<style scoped>
.nf-wrap {
  min-height: 100vh;
  display: flex;
  align-items: center;
  justify-content: center;
}
</style>
