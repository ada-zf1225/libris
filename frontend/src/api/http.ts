import axios, { AxiosError } from 'axios'
import { i18n } from '@/i18n'

export interface ProblemDetail {
  type?: string
  title?: string
  status?: number
  detail?: string
  errors?: Record<string, string>
}

export const http = axios.create({
  baseURL: '/',
  timeout: 15000,
  // axios reads the XSRF-TOKEN cookie and echoes it on mutating requests
  xsrfCookieName: 'XSRF-TOKEN',
  xsrfHeaderName: 'X-XSRF-TOKEN',
  withXSRFToken: true,
})

/** Extracts a human-readable message from an RFC 7807 problem response. */
export function errorMessage(error: unknown): string {
  const { t } = i18n.global
  if (axios.isAxiosError(error)) {
    const axiosError = error as AxiosError<ProblemDetail>
    const problem = axiosError.response?.data
    if (problem?.errors && Object.keys(problem.errors).length > 0) {
      return Object.values(problem.errors)[0]
    }
    if (problem?.detail) {
      return problem.detail
    }
    if (!axiosError.response) {
      return t('errors.network')
    }
  }
  return t('errors.network')
}

let onUnauthorized: (() => void) | null = null

export function setUnauthorizedHandler(handler: () => void) {
  onUnauthorized = handler
}

http.interceptors.response.use(
  (response) => response,
  (error: AxiosError) => {
    const url = error.config?.url ?? ''
    if (
      error.response?.status === 401 &&
      !url.includes('/api/auth/login') &&
      !url.includes('/api/auth/me')
    ) {
      onUnauthorized?.()
    }
    return Promise.reject(error)
  },
)
