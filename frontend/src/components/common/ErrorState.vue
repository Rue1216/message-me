<script setup lang="ts">
import AppAlert from '@/components/ui/AppAlert.vue'
import AppButton from '@/components/ui/AppButton.vue'
import { ApiClientError } from '@/api/client/http'
import { computed } from 'vue'

/**
 * 載入失敗的統一呈現。
 *
 * <p>把 unknown 型別的錯誤翻成一句可讀的話，並提供重試——
 * 這兩件事原本散落在每個 view 的 catch 區塊裡，措辭還各自不同。
 */
const props = withDefaults(defineProps<{ error: unknown; fallback?: string }>(), {
  fallback: '載入失敗，請稍後再試',
})

defineEmits<{ retry: [] }>()

const message = computed(() =>
  props.error instanceof ApiClientError ? props.error.message : props.fallback,
)
</script>

<template>
  <AppAlert variant="error">
    <div class="flex flex-wrap items-center justify-between gap-3">
      <span>{{ message }}</span>
      <AppButton
        variant="outline"
        size="sm"
        @click="$emit('retry')"
      >
        重新載入
      </AppButton>
    </div>
  </AppAlert>
</template>
