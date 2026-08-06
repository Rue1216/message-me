import pluginVue from 'eslint-plugin-vue'
import { defineConfigWithVueTs, vueTsConfigs } from '@vue/eslint-config-typescript'

export default defineConfigWithVueTs(
  {
    name: 'app/files-to-lint',
    files: ['**/*.ts', '**/*.mts', '**/*.tsx', '**/*.vue'],
  },
  {
    name: 'app/files-to-ignore',
    ignores: ['**/dist/**', '**/coverage/**', '**/node_modules/**'],
  },

  pluginVue.configs['flat/recommended'],
  vueTsConfigs.recommended,

  {
    name: 'app/security-rules',
    rules: {
      // 設計文件 §7.3：輸出端一律依賴 Vue 的 {{ }} 自動轉義。
      // v-html 會直接寫入 innerHTML，是本專案唯一可能的 XSS 出口，因此在 lint 階段封鎖。
      'vue/no-v-html': 'error',
    },
  },
)
