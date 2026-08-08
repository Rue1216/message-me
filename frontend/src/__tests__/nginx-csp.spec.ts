import { describe, expect, it } from 'vitest'

// 以 Vite 的 ?raw 取得檔案內容，而不是 node:fs：
// src/ 屬於 tsconfig.app.json 這個瀏覽器端的專案，它的 types 不含 node，讀不到內建模組。
import nginxConf from '../../nginx.conf?raw'

/**
 * 內容安全政策的回歸測試。
 *
 * <p>CSP 只在部署環境生效——Vite 的開發伺服器不送這個標頭，單元測試裡也沒有瀏覽器去執行它。
 * 因此政策與程式碼一旦對不上（例如程式產生 blob: 網址、政策卻不允許），
 * 開發與測試都會全綠，直到有人開著容器操作才發現。這個測試補的正是那個縫隙。
 *
 * <p>nginx.conf 裡的 add_header 不會累加，安全標頭必須在每個自訂標頭的 location 重寫一次，
 * 因此同一份政策存在多份副本——漏改其中一份是這個檔案最容易犯的錯，這裡一併檢查。
 */
const policies = [
  ...nginxConf.matchAll(/add_header\s+Content-Security-Policy\s+"([^"]+)"/g),
].map((match) => match[1] ?? '')

/** 應用程式本體的政策；以 script-src 區別於 /uploads/ 那份 default-src 'none' 的政策。 */
const appPolicies = policies.filter((policy) => policy.includes('script-src'))

/** 使用者上傳目錄的政策。 */
const uploadPolicies = policies.filter((policy) => policy.includes("default-src 'none'"))

describe('nginx 的 Content-Security-Policy', () => {
  it('應用程式本體的政策在多個 location 中重複，且完全一致', () => {
    // 全站一份、/index.html 一份；數量或內容對不上代表有人只改了其中一處
    expect(appPolicies).toHaveLength(2)
    expect(new Set(appPolicies).size).toBe(1)
  })

  it('允許 blob: 圖片，頭像與發文的本機預覽才顯示得出來', () => {
    for (const policy of appPolicies) {
      const imgSrc = /img-src ([^;]+)/.exec(policy)?.[1] ?? ''
      expect(imgSrc).toContain('blob:')
    }
  })

  it('使用者上傳目錄的政策維持最嚴格，不因此放寬', () => {
    expect(uploadPolicies).toHaveLength(1)
    expect(uploadPolicies[0]).not.toContain('blob:')
    expect(uploadPolicies[0]).toContain('sandbox')
  })
})
