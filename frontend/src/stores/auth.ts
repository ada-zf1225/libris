import { defineStore } from 'pinia'
import { ref, computed } from 'vue'
import { apiLogin, apiLogout, apiMe, apiMfaVerify, type Me } from '@/api/auth'
import { passkeys } from '@/api/security'

export const useAuthStore = defineStore('auth', () => {
  const user = ref<Me | null>(null)
  const bootstrapped = ref(false)
  const mfaPending = ref(false)

  const isAuthenticated = computed(() => user.value !== null)
  const isStaff = computed(() => user.value?.role === 'SUPER_ADMIN' || user.value?.role === 'LIBRARIAN')
  const isSuperAdmin = computed(() => user.value?.role === 'SUPER_ADMIN')

  function hasPermission(permission: string): boolean {
    return user.value?.permissions.includes(permission) ?? false
  }

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

  /** @returns true when a second factor is still required */
  async function login(username: string, password: string): Promise<boolean> {
    const result = await apiLogin(username, password)
    if (result.mfaRequired) {
      mfaPending.value = true
      return true
    }
    user.value = result.me
    mfaPending.value = false
    return false
  }

  async function verifyMfa(code: string) {
    const result = await apiMfaVerify(code)
    user.value = result.me
    mfaPending.value = false
  }

  async function loginWithPasskey() {
    await passkeys.authenticate()
    user.value = await apiMe()
    mfaPending.value = false
  }

  async function refresh() {
    try {
      user.value = await apiMe()
    } catch {
      /* keep current state */
    }
  }

  async function logout() {
    try {
      await apiLogout()
    } finally {
      user.value = null
      mfaPending.value = false
    }
  }

  function invalidate() {
    user.value = null
  }

  return {
    user, bootstrapped, mfaPending,
    isAuthenticated, isStaff, isSuperAdmin, hasPermission,
    bootstrap, login, verifyMfa, loginWithPasskey, refresh, logout, invalidate,
  }
})
