import { http } from './http'
import type { LoanView, FineView, PageResponse } from './admin'

export interface Overview {
  activeLoans: number
  overdue: number
  activeHolds: number
  unpaidFineCents: number
  favorites: number
}

export interface HoldView {
  id: number
  bookId: number
  bookTitle: string
  status: 'QUEUED' | 'READY' | 'FULFILLED' | 'EXPIRED' | 'CANCELLED'
  queuedAt: string
  expiresAt: string | null
  queuePosition: number
}

export interface FavoriteView {
  id: number
  bookId: number
  title: string
  author: string
  coverUrl: string | null
  createdAt: string
}

export interface SuggestionView {
  id: number
  title: string
  author: string | null
  isbn: string | null
  reason: string | null
  status: 'PENDING' | 'APPROVED' | 'REJECTED'
  reply: string | null
  createdAt: string
  handledAt: string | null
}

export const my = {
  overview: () => http.get<Overview>('/api/my/overview').then((r) => r.data),
  loans: () => http.get<LoanView[]>('/api/my/loans').then((r) => r.data),
  history: (page = 0, size = 10) =>
    http.get<PageResponse<LoanView>>('/api/my/loans/history', { params: { page, size } }).then((r) => r.data),
  renew: (loanId: number) =>
    http.post<{ loanId: number; newDueAt: string; renewCount: number }>(`/api/my/loans/${loanId}/renew`).then((r) => r.data),
  holds: () => http.get<HoldView[]>('/api/my/holds').then((r) => r.data),
  cancelHold: (id: number) => http.delete(`/api/my/holds/${id}`),
  fines: () => http.get<FineView[]>('/api/my/fines').then((r) => r.data),
  payFine: (id: number) => http.post(`/api/my/fines/${id}/pay`),
  favorites: () => http.get<FavoriteView[]>('/api/my/favorites').then((r) => r.data),
  addFavorite: (bookId: number) => http.post(`/api/my/favorites/${bookId}`),
  removeFavorite: (bookId: number) => http.delete(`/api/my/favorites/${bookId}`),
  suggestions: () => http.get<SuggestionView[]>('/api/my/suggestions').then((r) => r.data),
  suggest: (body: { title: string; author?: string; isbn?: string; reason?: string }) =>
    http.post<SuggestionView>('/api/my/suggestions', body).then((r) => r.data),
}
