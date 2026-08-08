import { createRouter, createWebHistory } from 'vue-router'

import { useAuthStore } from '@/stores/auth'

declare module 'vue-router' {
  interface RouteMeta {
    /** 未登入者不得進入，會被導向登入頁並記住原本要去的位置。 */
    requiresAuth?: boolean
    /** 已登入者不需要再看（登入、註冊），直接導回動態牆。 */
    guestOnly?: boolean
    /** 頁面標題，附加於瀏覽器分頁。 */
    title?: string
  }
}

export const APP_TITLE = 'Message Me'

/**
 * 路由表。
 *
 * <p>除了登入與註冊之外一律以動態 import 載入，讓首屏只下載動態牆需要的程式碼。
 * 讀取類頁面（動態牆、發文詳情）刻意不設 `requiresAuth`——與後端的存取原則一致：
 * 訪客可以先看看再決定要不要註冊。
 */
const router = createRouter({
  history: createWebHistory(),
  routes: [
    {
      path: '/',
      name: 'home',
      component: () => import('@/views/HomeView.vue'),
      meta: { title: '動態牆' },
    },
    {
      path: '/posts/:postId(\\d+)',
      name: 'post-detail',
      component: () => import('@/views/PostDetailView.vue'),
      props: true,
      meta: { title: '發文詳情' },
    },
    {
      path: '/login',
      name: 'login',
      component: () => import('@/views/LoginView.vue'),
      meta: { guestOnly: true, title: '登入' },
    },
    {
      path: '/register',
      name: 'register',
      component: () => import('@/views/RegisterView.vue'),
      meta: { guestOnly: true, title: '註冊' },
    },
    {
      path: '/users/:userId(\\d+)',
      name: 'user-profile',
      component: () => import('@/views/UserProfileView.vue'),
      props: true,
      meta: { title: '個人檔案' },
    },
    {
      path: '/search',
      name: 'search',
      component: () => import('@/views/SearchView.vue'),
      meta: { title: '搜尋' },
    },
    {
      // 標籤名稱可能含中文，vue-router 會自動處理編碼與解碼
      path: '/tags/:name',
      name: 'tag',
      component: () => import('@/views/TagView.vue'),
      props: true,
      meta: { title: '標籤' },
    },
    {
      path: '/profile',
      name: 'profile',
      component: () => import('@/views/ProfileView.vue'),
      meta: { requiresAuth: true, title: '個人檔案' },
    },
    {
      path: '/settings/account',
      name: 'account-settings',
      component: () => import('@/views/AccountSettingsView.vue'),
      meta: { requiresAuth: true, title: '帳號設定' },
    },
    {
      path: '/:pathMatch(.*)*',
      name: 'not-found',
      component: () => import('@/views/NotFoundView.vue'),
      meta: { title: '找不到頁面' },
    },
  ],
  // 換頁回到頂端，但用上一頁 / 下一頁返回時保留原本的捲動位置
  scrollBehavior(_to, _from, savedPosition) {
    return savedPosition ?? { top: 0 }
  },
})

/**
 * 導航守衛。
 *
 * <p>這裡擋的是「畫面」而不是「資料」——真正的授權在後端，前端攔截只是避免使用者
 * 進到一個必然失敗的頁面。因此判斷依據僅為本地是否持有權杖，不額外打 API 驗證。
 */
router.beforeEach((to) => {
  const auth = useAuthStore()

  if (to.meta.requiresAuth && !auth.isAuthenticated) {
    return { name: 'login', query: { redirect: to.fullPath } }
  }
  if (to.meta.guestOnly && auth.isAuthenticated) {
    return { name: 'home' }
  }
  return true
})

router.afterEach((to) => {
  document.title = to.meta.title ? `${to.meta.title}｜${APP_TITLE}` : APP_TITLE
})

export default router
