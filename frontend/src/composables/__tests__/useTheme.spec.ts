import { beforeEach, describe, expect, it, vi } from 'vitest'

import { useTheme } from '@/composables/useTheme'

/** 與 public/theme-init.js 共用同一個 key——不一致就會導致重新整理後偏好遺失。 */
const STORAGE_KEY = 'message-me:theme'

function mockSystemPrefersDark(dark: boolean): void {
  vi.stubGlobal(
    'matchMedia',
    vi.fn().mockReturnValue({ matches: dark, addEventListener: vi.fn(), removeEventListener: vi.fn() }),
  )
}

describe('useTheme', () => {
  beforeEach(() => {
    localStorage.clear()
    document.documentElement.classList.remove('dark')
    mockSystemPrefersDark(false)
  })

  it('切換到深色時加上 .dark 並記住偏好', () => {
    const { setPreference } = useTheme()

    setPreference('dark')

    expect(document.documentElement.classList.contains('dark')).toBe(true)
    expect(localStorage.getItem(STORAGE_KEY)).toBe('dark')
  })

  it('切換到淺色時移除 .dark', () => {
    const { setPreference } = useTheme()

    setPreference('dark')
    setPreference('light')

    expect(document.documentElement.classList.contains('dark')).toBe(false)
    expect(localStorage.getItem(STORAGE_KEY)).toBe('light')
  })

  it('選擇「跟隨系統」時清除紀錄，而不是存入 system', () => {
    const { setPreference } = useTheme()

    setPreference('dark')
    setPreference('system')

    // 沒有紀錄本身就代表跟隨系統，與 theme-init.js 的判斷一致
    expect(localStorage.getItem(STORAGE_KEY)).toBeNull()
  })

  it('跟隨系統且系統為深色時套用深色', () => {
    mockSystemPrefersDark(true)
    const { setPreference, isDark } = useTheme()

    setPreference('system')

    expect(isDark.value).toBe(true)
    expect(document.documentElement.classList.contains('dark')).toBe(true)
  })

  it('明確的選擇優先於系統偏好', () => {
    mockSystemPrefersDark(true)
    const { setPreference, isDark } = useTheme()

    setPreference('light')

    expect(isDark.value).toBe(false)
  })

  it('toggle 在深淺之間切換', () => {
    const { setPreference, toggle, isDark } = useTheme()

    setPreference('light')
    toggle()

    expect(isDark.value).toBe(true)
  })
})
