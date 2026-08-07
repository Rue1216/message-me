<script setup lang="ts">
import { useQuery } from '@tanstack/vue-query'
import { computed, ref, watch } from 'vue'
import { RouterLink } from 'vue-router'

import { ApiClientError } from '@/api/client/http'
import { fetchCurrentUser, updateCurrentUser } from '@/api/resources/users'
import ImageUploader from '@/components/common/ImageUploader.vue'
import ActivityTimeline from '@/components/user/ActivityTimeline.vue'
import UserAvatar from '@/components/user/UserAvatar.vue'
import AppButton from '@/components/ui/AppButton.vue'
import AppCard from '@/components/ui/AppCard.vue'
import AppInput from '@/components/ui/AppInput.vue'
import AppTextarea from '@/components/ui/AppTextarea.vue'
import FormField from '@/components/ui/FormField.vue'
import { useFormValidation } from '@/composables/useFormValidation'
import { useToast } from '@/composables/useToast'
import { userKeys } from '@/queries/queryKeys'
import { useAuthStore } from '@/stores/auth'
import { formatDateTime } from '@/utils/format/datetime'
import {
  BIOGRAPHY_MAX_LENGTH,
  USER_NAME_MAX_LENGTH,
  validateBiography,
  validateEmail,
  validateUserName,
} from '@/utils/validation/user'

/**
 * 本人的個人檔案：編輯表單 + 自己的合併動態。
 *
 * <p>手機號碼是登入帳號，變更它需要另一套驗證流程（例如簡訊驗證），
 * 不該混在個人檔案編輯裡，因此只顯示不可修改。
 */

const auth = useAuthStore()
const toast = useToast()

/**
 * 進頁面時重新抓一次個人檔案。
 *
 * store 裡的資料來自登入當下，可能已經過時（例如在另一個分頁改過）；
 * 編輯表單若以舊資料為底，送出時會把別處的變更蓋掉。
 */
const query = useQuery({
  queryKey: userKeys.me(),
  queryFn: fetchCurrentUser,
})

const submitting = ref(false)
const model = ref({
  userName: '',
  email: '',
  biography: '',
})
const coverImage = ref<string | null>(null)

// 資料到達後才填入表單；immediate 讓快取命中的情形也會執行
watch(
  query.data,
  (user) => {
    if (!user) {
      return
    }
    auth.setUser(user)
    model.value = {
      userName: user.userName,
      email: user.email ?? '',
      biography: user.biography ?? '',
    }
    coverImage.value = user.coverImage ?? null
  },
  { immediate: true },
)

const { errors, validateOnBlur, revalidate, validateAll } = useFormValidation(model, {
  userName: validateUserName,
  email: validateEmail,
  biography: validateBiography,
})

const biographyRemaining = computed(() => BIOGRAPHY_MAX_LENGTH - model.value.biography.length)

async function handleSubmit(): Promise<void> {
  if (!validateAll()) {
    return
  }

  submitting.value = true
  try {
    // PUT 為全欄位取代語意：選填欄位留空時送 null，代表清空而不是維持原值
    const updated = await updateCurrentUser({
      userName: model.value.userName.trim(),
      email: model.value.email.trim() || null,
      biography: model.value.biography.trim() || null,
      coverImage: coverImage.value,
    })
    auth.setUser(updated)
    toast.success('個人檔案已更新')
  } catch (error) {
    toast.error(error instanceof ApiClientError ? error.message : '更新失敗，請稍後再試')
  } finally {
    submitting.value = false
  }
}
</script>

<template>
  <div class="flex flex-col gap-6">
    <AppCard class="p-5">
      <div class="mb-5 flex items-center justify-between gap-4">
        <div class="flex items-center gap-4">
          <UserAvatar
            :name="model.userName"
            :image="coverImage"
            size="lg"
          />
          <div>
            <h1 class="font-semibold">
              {{ query.data.value?.phoneNumber ?? '' }}
            </h1>
            <p
              v-if="query.data.value"
              class="text-xs text-muted-foreground"
            >
              加入於 {{ formatDateTime(query.data.value.createdAt) }}
            </p>
          </div>
        </div>

        <RouterLink
          :to="{ name: 'account-settings' }"
          class="text-sm text-primary hover:underline"
        >
          帳號設定
        </RouterLink>
      </div>

      <form
        class="flex flex-col gap-4 border-t border-border pt-5"
        @submit.prevent="handleSubmit"
      >
        <FormField
          id="profile-name"
          v-slot="{ describedBy, invalid }"
          label="使用者名稱"
          :error="errors.userName"
          :hint="`最多 ${USER_NAME_MAX_LENGTH} 字`"
        >
          <AppInput
            id="profile-name"
            v-model="model.userName"
            :invalid="invalid"
            :aria-describedby="describedBy"
            @blur="validateOnBlur('userName')"
            @input="revalidate('userName')"
          />
        </FormField>

        <FormField
          id="profile-email"
          v-slot="{ describedBy, invalid }"
          label="電子郵件"
          optional
          :error="errors.email"
        >
          <AppInput
            id="profile-email"
            v-model="model.email"
            type="email"
            placeholder="you@example.com"
            :invalid="invalid"
            :aria-describedby="describedBy"
            @blur="validateOnBlur('email')"
            @input="revalidate('email')"
          />
        </FormField>

        <FormField
          id="profile-bio"
          v-slot="{ describedBy, invalid }"
          label="自我介紹"
          optional
          :error="errors.biography"
          :hint="`還可以輸入 ${biographyRemaining} 字`"
        >
          <AppTextarea
            id="profile-bio"
            v-model="model.biography"
            placeholder="介紹一下你自己…"
            :invalid="invalid"
            :aria-describedby="describedBy"
            @blur="validateOnBlur('biography')"
            @input="revalidate('biography')"
          />
        </FormField>

        <div class="flex flex-col gap-1.5">
          <span class="text-sm font-medium">頭像圖片<span class="font-normal text-muted-foreground">（選填）</span></span>
          <ImageUploader
            v-model="coverImage"
            placeholder="上傳頭像"
          />
        </div>

        <div class="flex justify-end">
          <AppButton
            type="submit"
            :loading="submitting"
          >
            儲存變更
          </AppButton>
        </div>
      </form>
    </AppCard>

    <ActivityTimeline
      v-if="auth.currentUserId !== null"
      :user-id="auth.currentUserId"
      empty-title="你還沒有任何動態"
    />
  </div>
</template>
