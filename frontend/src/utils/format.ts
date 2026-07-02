import { i18n } from '@/i18n'

/** Formats integer cents as a localized CNY amount, e.g. 1280 → "¥12.80". */
export function money(cents: number | null | undefined): string {
  if (cents == null) return '—'
  return `¥${(cents / 100).toFixed(2)}`
}

export function date(value: string | null | undefined): string {
  if (!value) return '—'
  const locale = i18n.global.locale.value === 'zh-CN' ? 'zh-CN' : 'en-GB'
  return new Date(value).toLocaleDateString(locale)
}

export function dateTime(value: string | null | undefined): string {
  if (!value) return '—'
  const locale = i18n.global.locale.value === 'zh-CN' ? 'zh-CN' : 'en-GB'
  return new Date(value).toLocaleString(locale, { dateStyle: 'short', timeStyle: 'short' })
}
