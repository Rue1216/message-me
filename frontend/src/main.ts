import { createApp } from 'vue'
import { createPinia } from 'pinia'

import App from '@/App.vue'
import router from '@/router'
import { setUnauthorizedHandler } from '@/api/http'
import { useAuthStore } from '@/stores/auth'

import '@/assets/main.css'

const app = createApp(App)
const pinia = createPinia()

// Pinia 必須先於 router 安裝：導航守衛在第一次導航時就會用到 authStore
app.use(pinia)

/**
 * 權杖失效的統一處置。
 *
 * Axios 攔截器偵測到 401 後只負責清掉儲存的權杖，「接下來要去哪裡」屬於應用層決定，
 * 因此在這裡接起來——順帶記住當前位置，登入後可以回到原本要看的頁面。
 */
setUnauthorizedHandler(() => {
  useAuthStore(pinia).signOut()
  const current = router.currentRoute.value
  if (current.name !== 'login') {
    void router.replace({ name: 'login', query: { redirect: current.fullPath } })
  }
})

app.use(router)
app.mount('#app')
