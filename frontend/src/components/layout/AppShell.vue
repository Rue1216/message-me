<script setup lang="ts">
import { Home, LogOut, Search, User, Settings } from "@lucide/vue";
import {
  DropdownMenuContent,
  DropdownMenuItem,
  DropdownMenuPortal,
  DropdownMenuRoot,
  DropdownMenuTrigger,
} from "reka-ui";
import { ref, watch } from "vue";
import { RouterLink, RouterView, useRoute, useRouter } from "vue-router";

import PopularTags from "@/components/tag/PopularTags.vue";
import ThemeToggle from "@/components/layout/ThemeToggle.vue";
import UserAvatar from "@/components/user/UserAvatar.vue";
import AppButton from "@/components/ui/AppButton.vue";
import AppInput from "@/components/ui/AppInput.vue";
import { useToast } from "@/composables/useToast";
import { useAuthStore } from "@/stores/auth";

/**
 * 全站版面。
 *
 * <p>桌機為雙欄（主內容 + 熱門標籤側欄），手機收成單欄並在底部提供導覽列——
 * 拇指構不到頂端，把主要動作放在下方是行動裝置上的常識。
 *
 * <p>頁首含 skip link：鍵盤使用者不必每頁都先 Tab 過整條導覽列才能到達內容。
 */
const auth = useAuthStore();
const router = useRouter();
const route = useRoute();
const toast = useToast();

const keyword = ref(typeof route.query.q === "string" ? route.query.q : "");

// 從搜尋頁離開時清空輸入框，避免在別的頁面看到殘留的關鍵字
watch(
  () => route.fullPath,
  () => {
    if (route.name !== "search") {
      keyword.value = "";
    }
  },
);

function submitSearch(): void {
  const trimmed = keyword.value.trim();
  if (trimmed) {
    void router.push({ name: "search", query: { q: trimmed } });
  }
}

function signOut(): void {
  auth.signOut();
  toast.success("已登出");
  void router.push({ name: "home" });
}
</script>

<template>
  <a
    href="#main-content"
    class="sr-only focus:not-sr-only focus:absolute focus:left-4 focus:top-4 focus:z-50 focus:rounded-md focus:bg-card focus:px-4 focus:py-2 focus:shadow-lg"
  >
    跳到主要內容
  </a>

  <header
    class="sticky top-0 z-40 border-b border-border bg-background/95 backdrop-blur"
  >
    <div class="mx-auto flex h-14 max-w-5xl items-center gap-3 px-4">
      <RouterLink
        :to="{ name: 'home' }"
        class="shrink-0 text-lg font-bold tracking-tight"
      >
        Message Me
      </RouterLink>

      <form
        class="ml-auto hidden max-w-xs flex-1 sm:block"
        role="search"
        @submit.prevent="submitSearch"
      >
        <label for="global-search" class="sr-only">搜尋發文</label>
        <div class="relative">
          <Search
            class="pointer-events-none absolute left-2.5 top-1/2 size-4 -translate-y-1/2 text-muted-foreground"
            aria-hidden="true"
          />
          <AppInput
            id="global-search"
            v-model="keyword"
            type="search"
            placeholder="搜尋發文或標籤…"
            class="h-9 pl-8"
          />
        </div>
      </form>

      <nav class="ml-auto flex items-center gap-1 sm:ml-0">
        <ThemeToggle />

        <DropdownMenuRoot v-if="auth.isAuthenticated">
          <DropdownMenuTrigger
            class="flex items-center gap-2 rounded-md px-2 py-1 hover:bg-muted"
            aria-label="開啟使用者選單"
          >
            <UserAvatar
              :name="auth.user?.userName ?? ''"
              :image="auth.user?.coverImage ?? null"
              size="sm"
            />
            <span class="hidden max-w-32 truncate text-sm sm:inline">{{
              auth.user?.userName
            }}</span>
          </DropdownMenuTrigger>
          <DropdownMenuPortal>
            <DropdownMenuContent
              class="z-50 min-w-44 rounded-md border border-border bg-card p-1 shadow-lg"
              :side-offset="6"
              align="end"
            >
              <DropdownMenuItem
                class="flex cursor-pointer items-center gap-2 rounded px-2 py-1.5 text-sm outline-none data-highlighted:bg-muted"
                @select="router.push({ name: 'profile' })"
              >
                <User class="size-4" aria-hidden="true" />
                個人檔案
              </DropdownMenuItem>
              <DropdownMenuItem
                class="flex cursor-pointer items-center gap-2 rounded px-2 py-1.5 text-sm outline-none data-highlighted:bg-muted"
                @select="router.push({ name: 'account-settings' })"
              >
                <Settings class="size-4" aria-hidden="true" />
                帳號設定
              </DropdownMenuItem>
              <div class="my-1 h-px bg-border" />
              <DropdownMenuItem
                class="flex cursor-pointer items-center gap-2 rounded px-2 py-1.5 text-sm text-destructive outline-none data-highlighted:bg-muted"
                @select="signOut"
              >
                <LogOut class="size-4" aria-hidden="true" />
                登出
              </DropdownMenuItem>
            </DropdownMenuContent>
          </DropdownMenuPortal>
        </DropdownMenuRoot>

        <template v-else>
          <AppButton
            variant="ghost"
            size="sm"
            @click="router.push({ name: 'login' })"
          >
            登入
          </AppButton>
          <AppButton size="sm" @click="router.push({ name: 'register' })">
            註冊
          </AppButton>
        </template>
      </nav>
    </div>
  </header>

  <div class="mx-auto flex max-w-5xl gap-6 px-4 pb-24 pt-5 sm:pb-12">
    <main id="main-content" class="min-w-0 flex-1">
      <RouterView />
    </main>

    <!-- 側欄只在桌機出現：手機的螢幕寬度該全部留給內容 -->
    <aside class="hidden w-64 shrink-0 lg:block">
      <PopularTags />
    </aside>
  </div>

  <!-- 手機底部導覽：主要動作放在拇指構得到的位置 -->
  <nav
    class="fixed inset-x-0 bottom-0 z-40 border-t border-border bg-background sm:hidden"
    aria-label="主要導覽"
  >
    <div class="flex h-16 items-center justify-around">
      <RouterLink
        :to="{ name: 'home' }"
        class="flex flex-col items-center gap-0.5 px-4 py-2 text-xs text-muted-foreground"
        active-class="text-primary"
      >
        <Home class="size-5" aria-hidden="true" />
        動態牆
      </RouterLink>
      <RouterLink
        :to="{ name: 'search' }"
        class="flex flex-col items-center gap-0.5 px-4 py-2 text-xs text-muted-foreground"
        active-class="text-primary"
      >
        <Search class="size-5" aria-hidden="true" />
        搜尋
      </RouterLink>
      <RouterLink
        v-if="auth.isAuthenticated"
        :to="{ name: 'profile' }"
        class="flex flex-col items-center gap-0.5 px-4 py-2 text-xs text-muted-foreground"
        active-class="text-primary"
      >
        <User class="size-5" aria-hidden="true" />
        我的
      </RouterLink>
      <RouterLink
        v-else
        :to="{ name: 'login' }"
        class="flex flex-col items-center gap-0.5 px-4 py-2 text-xs text-muted-foreground"
        active-class="text-primary"
      >
        <User class="size-5" aria-hidden="true" />
        登入
      </RouterLink>
    </div>
  </nav>
</template>
