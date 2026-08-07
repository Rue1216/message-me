/*
 * 首屏套用深色模式，避免白底閃爍（FOUC）。
 *
 * 這段必須在 CSS 套用之前同步執行，因此放在 <head> 且不加 defer。
 *
 * 為什麼是獨立的檔案而不是 index.html 裡的 inline script：
 * 內容安全政策的 script-src 只允許 'self'（見 frontend/nginx.conf），
 * inline script 會被瀏覽器擋下。同源的 .js 檔則完全合規，
 * 也不需要為了一段程式碼去維護 CSP hash。
 *
 * 判斷順序：使用者的明確選擇優先於系統偏好——設定過就該被記住。
 * 這裡的 storage key 必須與 src/composables/useTheme.ts 一致。
 */
(function () {
  try {
    var stored = localStorage.getItem('message-me:theme')
    var prefersDark = window.matchMedia('(prefers-color-scheme: dark)').matches
    var dark = stored === 'dark' || (stored !== 'light' && prefersDark)
    if (dark) {
      document.documentElement.classList.add('dark')
    }
  } catch {
    // localStorage 可能因隱私設定而無法存取；此時沿用淺色，不影響其他功能
  }
})()
