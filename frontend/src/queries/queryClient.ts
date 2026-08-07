import { QueryClient } from '@tanstack/vue-query'

import { ApiClientError } from '@/api/client/http'

/**
 * 全站共用的 Query 設定。
 *
 * <p>這裡的預設值取代了原本散落在每個 view 裡的 `ref(loading)` / `ref(error)` / try-catch。
 */
export function createAppQueryClient(): QueryClient {
  return new QueryClient({
    defaultOptions: {
      queries: {
        /*
         * 30 秒內視為新鮮，不重新請求。
         *
         * 這是「從動態牆點進詳情頁再返回」不會整頁重抓的原因——
         * 也是改用 Query 之後最直接的體感差異。
         */
        staleTime: 30_000,

        /*
         * 只重試伺服器端或網路的暫時性問題。
         *
         * 4xx 重試沒有意義：401 不會因為再試一次就變成有權限，404 也不會冒出資料。
         * 盲目重試只是讓使用者多等三倍的時間才看到同一個錯誤。
         */
        retry: (failureCount, error) => {
          if (error instanceof ApiClientError && error.status !== undefined && error.status < 500) {
            return false
          }
          return failureCount < 2
        },

        // 切回分頁時重新驗證，讓長時間停留的分頁不會顯示過期內容
        refetchOnWindowFocus: true,
      },
      mutations: {
        // 寫入不自動重試：重複送出的後果由各個 mutation 自行判斷
        // （按讚是冪等的可以重試，發文則不行）
        retry: false,
      },
    },
  })
}
