import { http } from './http'

export interface Me {
  id: number
  username: string
  displayName: string
  role: 'SUPER_ADMIN' | 'LIBRARIAN' | 'READER'
  status: 'ACTIVE' | 'BLOCKED' | 'DISABLED'
  email: string | null
  preferredLocale: string
  readerType: 'TEACHER' | 'STUDENT' | null
  permissions: string[]
  emailVerified: boolean
  mfaEnabled: boolean
  passkeyCount: number
}

export interface LoginResponse {
  mfaRequired: boolean
  me: Me | null
}

export async function apiLogin(username: string, password: string): Promise<LoginResponse> {
  const { data } = await http.post<LoginResponse>('/api/auth/login', { username, password })
  return data
}

export async function apiMfaVerify(code: string): Promise<LoginResponse> {
  const { data } = await http.post<LoginResponse>('/api/auth/mfa/verify', { code })
  return data
}

export async function apiMe(): Promise<Me> {
  const { data } = await http.get<Me>('/api/auth/me')
  return data
}

export async function apiLogout(): Promise<void> {
  await http.post('/api/auth/logout')
}

export async function apiChangePassword(oldPassword: string, newPassword: string): Promise<void> {
  await http.post('/api/auth/change-password', { oldPassword, newPassword })
}

export async function apiUpdateLocale(locale: string): Promise<void> {
  await http.put('/api/auth/locale', { locale })
}

export async function apiForgotPassword(usernameOrEmail: string): Promise<void> {
  await http.post('/api/auth/forgot-password', { usernameOrEmail })
}

export async function apiResetPassword(token: string, newPassword: string): Promise<void> {
  await http.post('/api/auth/reset-password', { token, newPassword })
}

export async function apiVerifyEmail(token: string): Promise<void> {
  await http.post('/api/auth/verify-email', { token })
}
