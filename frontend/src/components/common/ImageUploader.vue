<script setup lang="ts">
import { ref } from 'vue'

import { ACCEPTED_IMAGE_TYPES, MAX_IMAGE_SIZE_BYTES, uploadImage } from '@/api/resources/files'
import { ApiClientError } from '@/api/client/http'
import AppButton from '@/components/ui/AppButton.vue'
import { useToast } from '@/composables/useToast'

/**
 * 圖片上傳與預覽。
 *
 * <p>v-model 綁定的是後端回傳的相對路徑（例如 `/uploads/<UUID>.jpg`），不是檔案本身：
 * 上傳與使用分成兩步，使用者可以先看到結果再決定要不要送出，反悔也不必連帶處理已建立的發文。
 *
 * <p>改版後不再依賴元件庫的上傳元件，直接用隱藏的 <input type="file"> ——
 * 上傳流程本來就由專案的 Axios 實例負責，中間那層轉接沒有帶來任何東西。
 */
withDefaults(defineProps<{ placeholder?: string }>(), { placeholder: '上傳圖片' })

const model = defineModel<string | null>({ default: null })

const toast = useToast()
const uploading = ref(false)
const fileInput = ref<HTMLInputElement | null>(null)

/**
 * 這裡的大小與型別檢查只是為了少一次來回，真正的把關在後端
 * （內容嗅探、UUID 重新命名、落點驗證），繞過前端不會有任何好處。
 */
async function handleFileChange(event: Event): Promise<void> {
  const input = event.target as HTMLInputElement
  const file = input.files?.[0]
  if (!file) {
    return
  }

  if (file.size > MAX_IMAGE_SIZE_BYTES) {
    toast.error(`圖片大小不可超過 ${MAX_IMAGE_SIZE_BYTES / 1024 / 1024} MB`)
    input.value = ''
    return
  }

  uploading.value = true
  try {
    const uploaded = await uploadImage(file)
    model.value = uploaded.url
  } catch (error) {
    toast.error(error instanceof ApiClientError ? error.message : '圖片上傳失敗，請稍後再試')
  } finally {
    uploading.value = false
    // 清空以便再次選擇同一個檔案時仍會觸發 change
    input.value = ''
  }
}
</script>

<template>
  <div class="flex flex-col gap-2">
    <div
      v-if="model"
      class="aspect-video max-h-64 overflow-hidden rounded-lg bg-muted"
    >
      <img
        :src="model"
        alt="已選擇的圖片預覽"
        class="size-full object-cover"
      >
    </div>

    <div class="flex items-center gap-2">
      <input
        ref="fileInput"
        type="file"
        :accept="ACCEPTED_IMAGE_TYPES"
        class="sr-only"
        @change="handleFileChange"
      >
      <AppButton
        variant="secondary"
        size="sm"
        :loading="uploading"
        @click="fileInput?.click()"
      >
        {{ model ? '更換圖片' : placeholder }}
      </AppButton>
      <AppButton
        v-if="model"
        variant="ghost"
        size="sm"
        class="text-destructive hover:bg-destructive/10"
        @click="model = null"
      >
        移除圖片
      </AppButton>
    </div>
  </div>
</template>
