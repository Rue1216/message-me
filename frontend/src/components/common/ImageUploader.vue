<script setup lang="ts">
import { ref } from 'vue'
import { useMessage, type UploadCustomRequestOptions } from 'naive-ui'

import { ACCEPTED_IMAGE_TYPES, MAX_IMAGE_SIZE_BYTES, uploadImage } from '@/api/resources/files'
import { ApiClientError } from '@/api/client/http'

/**
 * 圖片上傳與預覽。
 *
 * <p>v-model 綁定的是後端回傳的相對路徑（例如 `/uploads/<UUID>.jpg`），不是檔案本身：
 * 上傳與使用分成兩步，使用者可以先看到結果再決定要不要送出，反悔也不必連帶處理已建立的發文。
 */
withDefaults(
  defineProps<{
    modelValue: string | null
    /** 未選擇圖片時按鈕上的文字。 */
    placeholder?: string
  }>(),
  { placeholder: '上傳圖片' },
)

const emit = defineEmits<{ 'update:modelValue': [string | null] }>()

const message = useMessage()
const uploading = ref(false)

/**
 * 接手 Naive UI 的上傳流程，改用專案的 Axios 實例送出。
 *
 * 這裡的大小與型別檢查只是為了少一次來回，真正的把關在後端
 * （內容嗅探、UUID 重新命名、落點驗證），繞過前端不會有任何好處。
 */
async function handleUpload({ file, onFinish, onError }: UploadCustomRequestOptions): Promise<void> {
  const raw = file.file
  if (!raw) {
    onError()
    return
  }
  if (raw.size > MAX_IMAGE_SIZE_BYTES) {
    message.error(`圖片大小不可超過 ${MAX_IMAGE_SIZE_BYTES / 1024 / 1024} MB`)
    onError()
    return
  }

  uploading.value = true
  try {
    const uploaded = await uploadImage(raw)
    emit('update:modelValue', uploaded.url)
    onFinish()
  } catch (error) {
    message.error(error instanceof ApiClientError ? error.message : '圖片上傳失敗，請稍後再試')
    onError()
  } finally {
    uploading.value = false
  }
}
</script>

<template>
  <div class="image-uploader">
    <n-image
      v-if="modelValue"
      :src="modelValue"
      :img-props="{ alt: '已選擇的圖片' }"
      class="image-uploader__preview"
      object-fit="cover"
    />

    <div class="image-uploader__actions">
      <n-upload
        :accept="ACCEPTED_IMAGE_TYPES"
        :show-file-list="false"
        :custom-request="handleUpload"
      >
        <n-button
          secondary
          size="small"
          :loading="uploading"
        >
          {{ modelValue ? '更換圖片' : placeholder }}
        </n-button>
      </n-upload>

      <n-button
        v-if="modelValue"
        quaternary
        size="small"
        type="error"
        @click="emit('update:modelValue', null)"
      >
        移除圖片
      </n-button>
    </div>
  </div>
</template>

<style scoped>
.image-uploader {
  display: flex;
  flex-direction: column;
  gap: 0.5rem;
}

.image-uploader__preview {
  border-radius: 8px;
  max-height: 16rem;
  overflow: hidden;
}

.image-uploader__actions {
  align-items: center;
  display: flex;
  gap: 0.5rem;
}
</style>
