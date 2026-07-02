import { http } from './http'

export interface Me {
  id: number
  username: string
  displayName: string
  role: 'ADMIN' | 'READER'
  status: 'ACTIVE' | 'BLOCKED'
  email: string | null
  preferredLocale: string
  readerType: 'TEACHER' | 'STUDENT' | null
}

export async function apiLogin(username: string, password: string): Promise<Me> {
  const { data } = await http.post<Me>('/api/auth/login', { username, password })
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
