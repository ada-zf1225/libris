import { defineStore } from 'pinia'
import { ref, computed } from 'vue'
import { apiLogin, apiLogout, apiMe, type Me } from '@/api/auth'

export const useAuthStore = defineStore('auth', () => {
  const user = ref<Me | null>(null)
  const bootstrapped = ref(false)

  const isAuthenticated = computed(() => user.value !== null)
  const isAdmin = computed(() => user.value?.role === 'ADMIN')

  /** Loads the current session once on app start; 401 simply means "guest". */
  async function bootstrap() {
    if (bootstrapped.value) return
    try {
      user.value = await apiMe()
    } catch {
      user.value = null
    } finally {
      bootstrapped.value = true
    }
  }

  async function login(username: string, password: string) {
    user.value = await apiLogin(username, password)
  }

  async function logout() {
    try {
      await apiLogout()
    } finally {
      user.value = null
    }
  }

  function invalidate() {
    user.value = null
  }

  return { user, bootstrapped, isAuthenticated, isAdmin, bootstrap, login, logout, invalidate }
})
