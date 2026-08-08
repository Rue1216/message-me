<script setup lang="ts">
import { useRouter } from 'vue-router'

import EmptyState from '@/components/common/EmptyState.vue'
import AppButton from '@/components/ui/AppButton.vue'

const router = useRouter()
</script>

<template>
  <div class="mt-8">
    <h1 class="sr-only">
      找不到頁面
    </h1>
    <EmptyState
      title="找不到頁面"
      description="這個網址不存在，或內容已經被刪除了。"
    >
      <template #action>
        <AppButton @click="router.push({ name: 'home' })">
          回到動態牆
        </AppButton>
      </template>
    </EmptyState>
  </div>
</template>
