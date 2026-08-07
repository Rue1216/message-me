<script setup lang="ts">
import { useMessage, type DropdownOption } from 'naive-ui'
import { RouterLink, RouterView, useRouter } from 'vue-router'

import UserAvatar from '@/components/user/UserAvatar.vue'
import { useAuthStore } from '@/stores/auth'

/**
 * 全站版面：頁首導覽列 + 內容區。
 *
 * 導覽列右側依登入狀態切換：未登入顯示登入 / 註冊，已登入顯示頭像下拉選單。
 */

const auth = useAuthStore()
const router = useRouter()
const message = useMessage()

const userMenuOptions: DropdownOption[] = [
  { key: 'profile', label: '個人檔案' },
  { key: 'divider', type: 'divider' },
  { key: 'logout', label: '登出' },
]

function handleUserMenuSelect(key: string): void {
  if (key === 'profile') {
    void router.push({ name: 'profile' })
    return
  }
  if (key === 'logout') {
    auth.signOut()
    message.success('已登出')
    void router.push({ name: 'home' })
  }
}
</script>

<template>
  <n-layout position="absolute">
    <n-layout-header
      bordered
      class="app-header"
    >
      <div class="app-header__inner">
        <RouterLink
          :to="{ name: 'home' }"
          class="brand"
        >
          Message Me
        </RouterLink>

        <nav class="app-header__actions">
          <n-dropdown
            v-if="auth.isAuthenticated"
            trigger="click"
            :options="userMenuOptions"
            @select="handleUserMenuSelect"
          >
            <n-button quaternary>
              <span class="user-trigger">
                <UserAvatar
                  :name="auth.user?.userName ?? ''"
                  :image="auth.user?.coverImage ?? null"
                  :size="30"
                />
                <span class="user-trigger__name">{{ auth.user?.userName }}</span>
              </span>
            </n-button>
          </n-dropdown>

          <template v-else>
            <n-button
              quaternary
              @click="router.push({ name: 'login' })"
            >
              登入
            </n-button>
            <n-button
              type="primary"
              @click="router.push({ name: 'register' })"
            >
              註冊
            </n-button>
          </template>
        </nav>
      </div>
    </n-layout-header>

    <n-layout-content
      class="app-content"
      :native-scrollbar="false"
    >
      <div class="app-content__inner">
        <RouterView />
      </div>
    </n-layout-content>
  </n-layout>
</template>

<style scoped>
.app-header {
  height: 56px;
}

.app-header__inner {
  align-items: center;
  display: flex;
  height: 56px;
  justify-content: space-between;
  margin: 0 auto;
  max-width: var(--app-max-width);
  padding: 0 var(--app-gutter);
}

.brand {
  color: inherit;
  font-size: 1.15rem;
  font-weight: 700;
  letter-spacing: 0.01em;
  text-decoration: none;
}

.app-header__actions {
  align-items: center;
  display: flex;
  gap: 0.5rem;
}

.app-content {
  top: 56px;
}

.app-content__inner {
  margin: 0 auto;
  max-width: var(--app-max-width);
  padding: 1.25rem var(--app-gutter) 3rem;
}

.user-trigger {
  align-items: center;
  display: inline-flex;
  gap: 0.5rem;
}

.user-trigger__name {
  max-width: 8rem;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}
</style>
