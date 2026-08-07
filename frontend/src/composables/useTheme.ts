import { computed, ref } from 'vue'

/**
 * 主題偏好（淺色 / 深色 / 跟隨系統）。
 *
 * <p>首屏的套用不在這裡，而在 `public/theme-init.js`：那段必須在第一次繪製之前
 * 同步執行，等到 Vue 掛載才處理會先閃一下白底。這個 composable 負責的是
 * 掛載之後的切換與保存。
 *
 * <p>storage key 必須與 theme-init.js 一致，否則重新整理後偏好會遺失。
 */
const STORAGE_KEY = 'message-me:theme'

export type ThemePreference = 'light' | 'dark' | 'system'

/**
 * 模組層級的單一狀態。
 *
 * <p>主題是全域的，每個元件各自持有一份會導致切換後畫面不同步。
 */
const preference = ref<ThemePreference>(readStoredPreference())

function readStoredPreference(): ThemePreference {
  try {
    const stored = localStorage.getItem(STORAGE_KEY)
    return stored === 'light' || stored === 'dark' ? stored : 'system'
  } catch {
    // 隱私設定可能禁止存取 localStorage；此時視為未設定過
    return 'system'
  }
}

function prefersDark(): boolean {
  return window.matchMedia('(prefers-color-scheme: dark)').matches
}

function apply(next: ThemePreference): void {
  const dark = next === 'dark' || (next === 'system' && prefersDark())
  document.documentElement.classList.toggle('dark', dark)
}

export function useTheme() {
  const isDark = computed(
    () => preference.value === 'dark' || (preference.value === 'system' && prefersDark()),
  )

  function setPreference(next: ThemePreference): void {
    preference.value = next
    apply(next)
    try {
      if (next === 'system') {
        // 移除而非存入 'system'：沒有紀錄本身就代表「跟隨系統」，
        // 與 theme-init.js 的判斷邏輯一致
        localStorage.removeItem(STORAGE_KEY)
      } else {
        localStorage.setItem(STORAGE_KEY, next)
      }
    } catch {
      // 無法保存時仍然套用，只是重新整理後會回到系統偏好
    }
  }

  /** 在淺色與深色之間切換。目前跟隨系統時，切到與系統相反的那一邊。 */
  function toggle(): void {
    setPreference(isDark.value ? 'light' : 'dark')
  }

  return { preference, isDark, setPreference, toggle }
}
