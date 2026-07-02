import { ref, watch, type Ref } from 'vue'

/** Animates a number towards its target with an ease-out curve. */
export function useCountUp(source: Ref<number>, durationMs = 900) {
  const display = ref(0)
  let raf = 0

  function animate(from: number, to: number) {
    cancelAnimationFrame(raf)
    const start = performance.now()
    const step = (now: number) => {
      const t = Math.min(1, (now - start) / durationMs)
      const eased = 1 - Math.pow(1 - t, 3)
      display.value = Math.round(from + (to - from) * eased)
      if (t < 1) raf = requestAnimationFrame(step)
    }
    raf = requestAnimationFrame(step)
  }

  watch(source, (to, from) => animate(from ?? 0, to), { immediate: true })
  return display
}
