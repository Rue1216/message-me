import { createApp } from 'vue'
import { createPinia } from 'pinia'
import { VueQueryPlugin } from '@tanstack/vue-query'

import App from '@/App.vue'
import router from '@/router'
import { setUnauthorizedHandler } from '@/api/client/http'
import { createAppQueryClient } from '@/queries/queryClient'
import { useAuthStore } from '@/stores/auth'

import '@/assets/main.css'

const app = createApp(App)
const pinia = createPinia()
const queryClient = createAppQueryClient()

// Pinia 必須先於 router 安裝：導航守衛在第一次導航時就會用到 authStore
app.use(pinia)

/**
 * 權杖失效的統一處置。
 *
 * Axios 攔截器偵測到 401 後只負責清掉儲存的權杖，「接下來要去哪裡」屬於應用層決定，
 * 因此在這裡接起來——順帶記住當前位置，登入後可以回到原本要看的頁面。
 *
 * 同時清空 Query 的快取：那些資料是以前一位使用者的身分取得的，
 * 留著會讓下一位登入者短暫看到不屬於自己的狀態（例如別人的 likedByMe）。
 */
setUnauthorizedHandler(() => {
  useAuthStore(pinia).signOut()
  queryClient.clear()

  const current = router.currentRoute.value
  if (current.name !== 'login') {
    void router.replace({ name: 'login', query: { redirect: current.fullPath } })
  }
})

app.use(VueQueryPlugin, { queryClient })
app.use(router)
app.mount('#app')
