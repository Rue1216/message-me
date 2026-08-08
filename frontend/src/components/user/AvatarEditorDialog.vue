<script setup lang="ts">
import { ref, watch } from 'vue'

import { ApiClientError } from '@/api/client/http'
import { ACCEPTED_IMAGE_TYPES, MAX_IMAGE_SIZE_BYTES, uploadImage } from '@/api/resources/files'
import UserAvatar from '@/components/user/UserAvatar.vue'
import AppButton from '@/components/ui/AppButton.vue'
import AppDialog from '@/components/ui/AppDialog.vue'
import { useToast } from '@/composables/useToast'

/**
 * 頭像編輯對話框。
 *
 * <p>與一般的圖片上傳欄位（`ImageUploader`）不同，這裡選檔後**不會立刻上傳**：
 * 預覽用的是 `URL.createObjectURL` 產生的本機網址，直到按下「儲存」才真的送出。
 * 使用者按「取消」的比例不低，先上傳只會在伺服器上留下一堆沒有人引用的孤兒檔案。
 *
 * <p>「移除頭像」同樣只改變暫存狀態而不立即生效——對話框裡的每個動作都可以靠取消反悔，
 * 唯一會寫進資料庫的操作是「儲存」。
 *
 * <p>持久化由呼叫端以 `onSave` 提供：這個元件只負責挑圖、預覽與上傳，
 * 不知道個人檔案是怎麼儲存的（`PUT /users/me` 需要的其他欄位也不該由它來湊）。
 */
const props = defineProps<{
  /** 使用者名稱，僅用於沒有圖片時的字母 fallback 與替代文字。 */
  name: string
  /** 目前已儲存的頭像路徑；沒有則為 null。 */
  image: string | null
  /** 儲存頭像。傳入 null 代表移除。丟出例外時對話框會維持開啟。 */
  onSave: (coverImage: string | null) => Promise<void>
}>()

const open = defineModel<boolean>('open', { required: true })

const toast = useToast()

const fileInput = ref<HTMLInputElement | null>(null)
const saving = ref(false)
/** 待上傳的檔案；為 null 表示使用者沒有選新圖（可能是原封不動，也可能是按了移除）。 */
const pickedFile = ref<File | null>(null)
/** 預覽用的網址：新選的檔案為 blob:，否則是目前已儲存的頭像，移除後為 null。 */
const preview = ref<string | null>(null)
/** 由本元件建立、需要自行釋放的 blob 網址。 */
const objectUrl = ref<string | null>(null)

/** blob 網址會一直佔著記憶體直到分頁關閉，換圖與關閉對話框時都必須釋放。 */
function releaseObjectUrl(): void {
  if (objectUrl.value) {
    URL.revokeObjectURL(objectUrl.value)
    objectUrl.value = null
  }
}

/** 回到「與伺服器上相同」的狀態。每次開啟都重來，上一次取消掉的選擇不該殘留。 */
function reset(): void {
  releaseObjectUrl()
  pickedFile.value = null
  preview.value = props.image
}

// immediate：對話框以 open=true 掛載時（例如深連結）也要先填入目前的頭像
watch(
  open,
  (isOpen) => {
    if (isOpen) {
      reset()
    } else {
      releaseObjectUrl()
    }
  },
  { immediate: true },
)

/**
 * 這裡的大小與型別檢查只是為了少一次來回，真正的把關在後端
 * （內容嗅探、UUID 重新命名、落點驗證），繞過前端不會有任何好處。
 */
function handleFileChange(event: Event): void {
  const input = event.target as HTMLInputElement
  const file = input.files?.[0]
  // 清空以便再次選擇同一個檔案時仍會觸發 change
  input.value = ''
  if (!file) {
    return
  }

  if (file.size > MAX_IMAGE_SIZE_BYTES) {
    toast.error(`圖片大小不可超過 ${MAX_IMAGE_SIZE_BYTES / 1024 / 1024} MB`)
    return
  }

  releaseObjectUrl()
  objectUrl.value = URL.createObjectURL(file)
  pickedFile.value = file
  preview.value = objectUrl.value
}

function removeAvatar(): void {
  releaseObjectUrl()
  pickedFile.value = null
  preview.value = null
}

/** 沒有任何變更時不需要送出請求，儲存鈕也因此保持停用。 */
function isUnchanged(): boolean {
  return pickedFile.value === null && preview.value === props.image
}

async function handleSave(): Promise<void> {
  if (isUnchanged()) {
    open.value = false
    return
  }

  saving.value = true
  try {
    // 選了新圖才上傳；只按移除的情況直接送 null
    const coverImage = pickedFile.value ? (await uploadImage(pickedFile.value)).url : null
    await props.onSave(coverImage)
    open.value = false
  } catch (error) {
    toast.error(error instanceof ApiClientError ? error.message : '頭像更新失敗，請稍後再試')
  } finally {
    saving.value = false
  }
}
</script>

<template>
  <AppDialog
    v-model:open="open"
    title="更換頭像"
    description="支援 JPEG、PNG 與 WebP，檔案大小上限 5 MB。"
  >
    <div class="flex flex-col items-center gap-4">
      <UserAvatar
        :name="name"
        :image="preview"
        size="xl"
      />

      <input
        ref="fileInput"
        type="file"
        :accept="ACCEPTED_IMAGE_TYPES"
        class="sr-only"
        @change="handleFileChange"
      >

      <div class="flex flex-wrap items-center justify-center gap-2">
        <AppButton
          variant="secondary"
          size="sm"
          :disabled="saving"
          @click="fileInput?.click()"
        >
          {{ preview ? '更換圖片' : '選擇圖片' }}
        </AppButton>
        <AppButton
          v-if="preview"
          variant="ghost"
          size="sm"
          class="text-destructive hover:bg-destructive/10"
          :disabled="saving"
          @click="removeAvatar"
        >
          移除頭像
        </AppButton>
      </div>

      <p
        v-if="!preview"
        class="text-center text-sm text-muted-foreground"
      >
        沒有頭像時，會顯示使用者名稱的第一個字。
      </p>
    </div>

    <template #footer>
      <AppButton
        variant="outline"
        :disabled="saving"
        @click="open = false"
      >
        取消
      </AppButton>
      <AppButton
        :loading="saving"
        :disabled="isUnchanged()"
        @click="handleSave"
      >
        儲存
      </AppButton>
    </template>
  </AppDialog>
</template>
