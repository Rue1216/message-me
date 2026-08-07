import { clsx, type ClassValue } from 'clsx'
import { twMerge } from 'tailwind-merge'

/**
 * 合併 class 名稱。
 *
 * <p>clsx 負責處理條件式與陣列，tailwind-merge 負責解決 Tailwind 的衝突：
 * 直接串接 `px-2` 與 `px-4` 時，兩者都會留在 class 屬性裡，最終由 CSS 的順序決定勝負
 * ——那取決於樣式表的產生順序，不是呼叫端的意圖。twMerge 讓後者確實覆蓋前者，
 * 元件的預設樣式因此能被使用端可靠地覆寫。
 */
export function cn(...inputs: ClassValue[]): string {
  return twMerge(clsx(inputs))
}
