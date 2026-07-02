import { http } from './http'

export interface BookHit {
  id: number
  title: string
  author: string
  publisher: string
  isbn: string
  language: string
  pubDate: string | null
  categoryId: number
  coverUrl: string | null
  intro: string | null
  copies: number
  available: number
}

export interface PaperHit {
  id: number
  title: string
  authors: string
  venue: string
  year: number
  pages: string | null
  abstractText: string | null
  doi: string | null
  url: string | null
}

export interface Facets {
  byCategory: Record<string, number>
  byLanguage: Record<string, number>
  byDecade: Record<string, number>
  availableCount: number
  total: number
}

export interface SearchResult<T> {
  content: T[]
  total: number
  page: number
  size: number
  facets: Facets | null
}

export interface BookSearchParams {
  q?: string
  categoryId?: number | null
  language?: string | null
  yearFrom?: number | null
  yearTo?: number | null
  availableOnly?: boolean
  sort?: 'relevance' | 'newest' | 'title'
  page?: number
  size?: number
}

export interface Suggestion {
  text: string
  type: 'title' | 'author'
}

export interface CopyAvailability {
  copyId: number
  barcode: string
  callNumber: string
  location: string
  status: 'IN_LIBRARY' | 'ON_LOAN' | 'ON_HOLD_SHELF' | 'LOST'
  dueAt: string | null
}

export interface BookDetail {
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
  copies: CopyAvailability[]
  availableCount: number
  holdQueueLength: number
  holdable: boolean
  amIHolding: boolean
  amIFavorite: boolean
  related: BookHit[]
}

export const discovery = {
  books: (params: BookSearchParams) =>
    http.get<SearchResult<BookHit>>('/api/books', { params }).then((r) => r.data),
  suggest: (q: string) =>
    http.get<Suggestion[]>('/api/books/suggest', { params: { q } }).then((r) => r.data),
  detail: (id: number) => http.get<BookDetail>(`/api/books/${id}`).then((r) => r.data),
  placeHold: (bookId: number) =>
    http.post<{ holdId: number; queuePosition: number }>(`/api/books/${bookId}/holds`).then((r) => r.data),
  papers: (params: { q?: string; yearFrom?: number | null; yearTo?: number | null; page?: number; size?: number }) =>
    http.get<SearchResult<PaperHit>>('/api/papers', { params }).then((r) => r.data),
}
