import { describe, expect, it } from 'vitest'

import { formatDateTime, formatRelativeTime } from '@/utils/format/datetime'

const now = new Date('2026-08-07T12:00:00')

describe('formatDateTime', () => {
  it('格式化為年月日與時分', () => {
    expect(formatDateTime('2026-08-07T09:05:00')).toBe('2026/08/07 09:05')
  })

  it('無法解析時原樣回傳，不讓畫面出現 Invalid Date', () => {
    expect(formatDateTime('not-a-date')).toBe('not-a-date')
  })
})

describe('formatRelativeTime', () => {
  it.each([
    ['2026-08-07T11:59:30', '剛剛'],
    ['2026-08-07T11:57:00', '3 分鐘前'],
    ['2026-08-07T09:00:00', '3 小時前'],
    ['2026-08-05T12:00:00', '2 天前'],
  ])('%s 顯示為 %s', (value, expected) => {
    expect(formatRelativeTime(value, now)).toBe(expected)
  })

  it('超過一週改用絕對時間，避免出現難以換算的「37 天前」', () => {
    expect(formatRelativeTime('2026-06-01T08:30:00', now)).toBe('2026/06/01 08:30')
  })

  it('伺服器與瀏覽器時鐘不同步時，未來時間顯示為剛剛', () => {
    expect(formatRelativeTime('2026-08-07T12:00:30', now)).toBe('剛剛')
  })
})
