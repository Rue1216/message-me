import { fileURLToPath, URL } from 'node:url'

import { defineConfig } from 'vitest/config'
import vue from '@vitejs/plugin-vue'
import tailwindcss from '@tailwindcss/vite'

// https://vite.dev/config/
export default defineConfig({
  plugins: [
    vue(),
    // Tailwind v4 以 Vite 外掛的形式運作，不再需要 postcss.config 與 tailwind.config。
    // 樣式在建置期就編成一份靜態 CSS，執行期不注入任何 <style>——
    // 這正是 nginx.conf 的 style-src 能收成 'self' 的前提（見該檔註解）。
    tailwindcss(),
  ],
  resolve: {
    alias: {
      '@': fileURLToPath(new URL('./src', import.meta.url)),
    },
  },
  server: {
    // 前端固定使用 3001，與容器環境的對外連接埠一致，兩種啟動方式的網址不會不同。
    // strictPort：埠被占用時直接失敗，而不是安靜地換一個埠讓人對著舊網址發呆。
    port: 3001,
    strictPort: true,
    // 本機單獨開發時，將 API 請求轉給 Spring Boot；容器環境則由 Nginx 反向代理
    proxy: {
      '/api': {
        target: 'http://localhost:8081',
        changeOrigin: true,
      },
      // 上傳的圖片同樣由後端提供，讓本機開發也看得到剛上傳的圖
      '/uploads': {
        target: 'http://localhost:8081',
        changeOrigin: true,
      },
    },
  },
  test: {
    environment: 'jsdom',
    // 測試一律集中在各模組的 __tests__/ 下，瀏覽原始碼目錄時只會看到實作檔本身
    include: ['src/**/__tests__/**/*.spec.ts'],
    globals: true,
    // 預設的 5s/10s 在機器高負載時（CI 共用 runner、與後端建置並行）會使
    // beforeEach 的 router 導航與元件掛載偶發逾時——失敗點是 hook 而非斷言。
    // 放寬逾時只影響「多慢才算失敗」，不會拖慢正常情況下的執行速度。
    testTimeout: 15000,
    hookTimeout: 30000,
  },
})
