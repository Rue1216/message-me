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
    name: 'app/typescript-adjustments',
    rules: {
      /*
       * 關閉 vue/require-default-prop。
       *
       * 這條規則來自 Options API 的年代：那時 props 的型別資訊只存在於執行期，
       * 沒有預設值就無從得知一個未傳入的 prop 會是什麼。
       * 在 `defineProps<{ class?: string }>()` 之下，型別已經明確說了它是 `string | undefined`，
       * 而 `class`、`description` 這類 prop 的「沒有值」本來就是有意義的狀態——
       * 為了滿足規則而補上 `default: undefined` 只是把同一件事寫兩遍。
       */
      'vue/require-default-prop': 'off',
    },
  },

  {
    name: 'app/security-rules',
    rules: {
      // 設計文件 §7.3：輸出端一律依賴 Vue 的 {{ }} 自動轉義。
      // v-html 會直接寫入 innerHTML，是本專案唯一可能的 XSS 出口，因此在 lint 階段封鎖。
      'vue/no-v-html': 'error',

      /*
       * 封鎖 style 屬性與 :style 綁定。
       *
       * 兩者的理由不同，不應混為一談：
       *
       * 靜態的 style="..." 屬性：會出現在送給瀏覽器的 HTML 中，
       * 而 Nginx 的 CSP 已把 style-src 收緊為 'self'（見 nginx.conf），
       * 這類屬性會被實際擋下。這一條是安全相關的。
       *
       * 動態的 :style 綁定：Vue 透過 CSSOM 套用（style.cssText / setProperty，
       * 見 @vue/runtime-dom 的 patchStyle），而 CSP **不攔截 CSSOM**，
       * 因此它其實不會被擋下。禁用它的理由純粹是樣式的一致性——
       * 專案已全面採用 Tailwind，讓樣式同時存在於 class 與 inline 兩處，
       * 只會使「這個元素為什麼長這樣」變得需要兩邊對照。
       *
       * 需要動態樣式時的替代做法：Tailwind 的條件式 class、arbitrary value，
       * 或以 data-* 屬性搭配 CSS 選擇器。
       */
      'vue/no-restricted-static-attribute': [
        'error',
        { key: 'style', message: "請改用 Tailwind class；HTML 中的 style 屬性會被 CSP（style-src 'self'）擋下" },
      ],
      'vue/no-restricted-syntax': [
        'error',
        {
          selector: "VAttribute[directive=true][key.name.name='bind'][key.argument.name='style']",
          message: '請改用 Tailwind class 或 data-* 屬性，讓樣式只有一個來源',
        },
      ],
    },
  },
)
