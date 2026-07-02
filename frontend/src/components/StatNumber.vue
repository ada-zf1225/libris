<script setup lang="ts">
import { computed, toRef } from 'vue'
import { useCountUp } from '@/composables/useCountUp'

const props = defineProps<{ value: number; money?: boolean }>()
const display = useCountUp(toRef(props, 'value'))
const text = computed(() => (props.money ? `¥${(display.value / 100).toFixed(2)}` : String(display.value)))
</script>

<template>
  <span>{{ text }}</span>
</template>
