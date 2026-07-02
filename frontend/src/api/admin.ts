import { http } from './http'

// ---------- shared ----------

export interface PageResponse<T> {
  content: T[]
  page: number
  size: number
  totalElements: number
  totalPages: number
}

export interface Category {
  id: number
  code: string
  nameZh: string
  nameEn: string
}

export async function apiCategories(): Promise<Category[]> {
  const { data } = await http.get<Category[]>('/api/categories')
  return data
}

// ---------- catalogue ----------

export interface BookView {
  id: number
  title: string
  author: string
  publisher: string
  isbn: string
  intro: string | null
  language: string
  priceCents: number | null
  pubDate: string | null
  categoryId: number
  coverUrl: string | null
  createdAt: string
}

export interface BookInput {
  title: string
  author: string
  publisher: string
  isbn: string
  intro?: string
  language: string
  priceCents?: number | null
  pubDate?: string | null
  categoryId: number
  coverUrl?: string | null
}

export interface CopyView {
  id: number
  bookId: number
  barcode: string
  callNumber: string
  location: string
  status: 'IN_LIBRARY' | 'ON_LOAN' | 'ON_HOLD_SHELF' | 'LOST'
}

export const adminBooks = {
  list: (params: { q?: string; categoryId?: number | null; page?: number; size?: number }) =>
    http.get<PageResponse<BookView>>('/api/admin/books', { params }).then((r) => r.data),
  create: (body: BookInput) => http.post<BookView>('/api/admin/books', body).then((r) => r.data),
  update: (id: number, body: BookInput) =>
    http.put<BookView>(`/api/admin/books/${id}`, body).then((r) => r.data),
  remove: (id: number) => http.delete(`/api/admin/books/${id}`),
  copies: (id: number) => http.get<CopyView[]>(`/api/admin/books/${id}/copies`).then((r) => r.data),
  addCopies: (id: number, count: number, location: string) =>
    http.post<CopyView[]>(`/api/admin/books/${id}/copies`, { count, location }).then((r) => r.data),
  removeCopy: (copyId: number) => http.delete(`/api/admin/books/copies/${copyId}`),
}

// ---------- patrons ----------

export interface ReaderView {
  id: number
  username: string
  displayName: string
  status: 'ACTIVE' | 'BLOCKED'
  email: string | null
  phone: string | null
  readerType: 'TEACHER' | 'STUDENT' | null
  sex: string | null
  birth: string | null
  address: string | null
}

export interface ReaderInput {
  username: string
  displayName: string
  email?: string
  phone?: string
  readerType: 'TEACHER' | 'STUDENT'
  sex?: string
  birth?: string | null
  address?: string
  initialPassword?: string
}

export const adminReaders = {
  list: (params: { q?: string; page?: number; size?: number }) =>
    http.get<PageResponse<ReaderView>>('/api/admin/readers', { params }).then((r) => r.data),
  create: (body: ReaderInput) =>
    http.post<ReaderView>('/api/admin/readers', body).then((r) => r.data),
  update: (id: number, body: ReaderInput) =>
    http.put<ReaderView>(`/api/admin/readers/${id}`, body).then((r) => r.data),
  block: (id: number) => http.post(`/api/admin/readers/${id}/block`),
  unblock: (id: number) => http.post(`/api/admin/readers/${id}/unblock`),
  fines: (id: number) => http.get<FineView[]>(`/api/admin/readers/${id}/fines`).then((r) => r.data),
}

// ---------- circulation ----------

export interface LoanView {
  id: number
  copyId: number
  barcode: string
  bookId: number
  bookTitle: string
  loanedAt: string
  dueAt: string
  returnedAt: string | null
  renewCount: number
  overdue: boolean
}

export interface FineView {
  id: number
  loanId: number
  bookTitle: string
  amountCents: number
  reason: 'OVERDUE' | 'LOST'
  status: 'UNPAID' | 'PAID' | 'WAIVED'
  createdAt: string
  paidAt: string | null
}

export interface ReaderSummary {
  id: number
  username: string
  displayName: string
  status: 'ACTIVE' | 'BLOCKED'
  readerType: string | null
  email: string | null
  phone: string | null
  activeLoanCount: number
  maxLoans: number
  overdueCount: number
  unpaidFineCents: number
  activeLoans: LoanView[]
}

export interface CheckoutResult {
  loanId: number
  bookTitle: string
  barcode: string
  dueAt: string
  fulfilledHold: boolean
}

export interface CheckinResult {
  bookTitle: string
  barcode: string
  routing: 'TO_SHELF' | 'TO_HOLD_SHELF'
  holdReaderName: string | null
  fineCents: number | null
  overdueDays: number
}

export const adminCirculation = {
  reader: (key: string) =>
    http
      .get<ReaderSummary>(`/api/admin/circulation/readers/${encodeURIComponent(key)}`)
      .then((r) => r.data),
  checkout: (barcode: string, readerId: number) =>
    http.post<CheckoutResult>('/api/admin/circulation/checkout', { barcode, readerId }).then((r) => r.data),
  checkin: (barcode: string) =>
    http.post<CheckinResult>('/api/admin/circulation/checkin', { barcode }).then((r) => r.data),
  renew: (loanId: number) =>
    http.post('/api/admin/circulation/renew', { loanId }).then((r) => r.data),
  markLost: (barcode: string) =>
    http.post('/api/admin/circulation/mark-lost', { barcode }).then((r) => r.data),
  payFine: (fineId: number) => http.post(`/api/admin/fines/${fineId}/pay`),
  waiveFine: (fineId: number) => http.post(`/api/admin/fines/${fineId}/waive`),
}

// ---------- policies & logs ----------

export interface PolicyView {
  id: number
  readerType: string
  loanDays: number
  maxLoans: number
  maxRenewals: number
  dailyFineCents: number
  blockOverdueCount: number
  blockFineCents: number
}

export const adminPolicies = {
  list: () => http.get<PolicyView[]>('/api/admin/policies').then((r) => r.data),
  update: (id: number, body: Omit<PolicyView, 'id' | 'readerType'>) =>
    http.put<PolicyView>(`/api/admin/policies/${id}`, body).then((r) => r.data),
}

export interface LogView {
  id: number
  operatorId: number | null
  action: string
  copyId: number | null
  readerId: number | null
  detail: Record<string, unknown> | null
  createdAt: string
}

export const adminLogs = {
  list: (params: { page?: number; size?: number }) =>
    http.get<PageResponse<LogView>>('/api/admin/circulation-logs', { params }).then((r) => r.data),
}
