import { fileURLToPath, URL } from 'node:url'

import { defineConfig } from 'vitest/config'
import vue from '@vitejs/plugin-vue'
import Components from 'unplugin-vue-components/vite'
import { NaiveUiResolver } from 'unplugin-vue-components/resolvers'

// https://vite.dev/config/
export default defineConfig({
  plugins: [
    vue(),
    // Naive UI 元件按需自動引入：模板寫 <n-button> 即可，不必在每個 SFC 手動 import。
    // 型別宣告產生於 src/components.d.ts 並納入版控，讓 CI 的 type-check 不必先跑一次 build。
    // dirs 設為空陣列：專案自己的元件維持顯式 import，import 路徑本身就是最好的來源說明。
    Components({
      dirs: [],
      dts: 'src/components.d.ts',
      resolvers: [NaiveUiResolver()],
    }),
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
  },
})
