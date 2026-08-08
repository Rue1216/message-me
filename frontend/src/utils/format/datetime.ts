/**
 * 時間顯示。
 *
 * <p>後端的 `LocalDateTime` 序列化為不帶時區的 ISO 字串（例如 `2026-08-07T09:30:00`）。
 * 依 ECMAScript 規範，這種格式會被當成「本地時間」解析——正好符合本專案的情境：
 * 容器與瀏覽器都在 `Asia/Taipei`，不需要再做時區換算。
 */

const ABSOLUTE_FORMATTER = new Intl.DateTimeFormat('zh-TW', {
  year: 'numeric',
  month: '2-digit',
  day: '2-digit',
  hour: '2-digit',
  minute: '2-digit',
  hour12: false,
})

/**
 * Intl 會在日期與時間之間插入窄空格（U+2009 / U+202F）。
 * 換成一般空格，讓輸出不隨 ICU 版本改變，使用者複製貼上時也不會夾帶看不見的字元。
 */
const NARROW_SPACES = /[  ]/g

const MINUTE = 60_000
const HOUR = 60 * MINUTE
const DAY = 24 * HOUR

function parse(value: string): Date | null {
  const date = new Date(value)
  return Number.isNaN(date.getTime()) ? null : date
}

/** 完整時間，例如 `2026/08/07 09:30`。時間無法解析時原樣回傳，不讓畫面出現 Invalid Date。 */
export function formatDateTime(value: string): string {
  const date = parse(value)
  return date ? ABSOLUTE_FORMATTER.format(date).replace(NARROW_SPACES, ' ') : value
}

/**
 * 相對時間，例如「3 分鐘前」。
 *
 * 超過一週改用絕對時間——「37 天前」對使用者而言比日期更難換算。
 *
 * @param now 現在時刻，預設為系統時間；獨立成參數是為了讓測試不必依賴當下時鐘
 */
export function formatRelativeTime(value: string, now: Date = new Date()): string {
  const date = parse(value)
  if (!date) {
    return value
  }

  const elapsed = now.getTime() - date.getTime()
  // 伺服器與瀏覽器時鐘些微不同步時 elapsed 可能為負，顯示為「剛剛」比顯示未來時間合理
  if (elapsed < MINUTE) {
    return '剛剛'
  }
  if (elapsed < HOUR) {
    return `${Math.floor(elapsed / MINUTE)} 分鐘前`
  }
  if (elapsed < DAY) {
    return `${Math.floor(elapsed / HOUR)} 小時前`
  }
  if (elapsed < 7 * DAY) {
    return `${Math.floor(elapsed / DAY)} 天前`
  }
  return formatDateTime(value)
}
