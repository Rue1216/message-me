<script setup lang="ts">
import { onMounted, ref } from 'vue'
import { useMessage, type FormInst, type FormRules } from 'naive-ui'

import { ApiClientError } from '@/api/client/http'
import { fetchCurrentUser, updateCurrentUser } from '@/api/resources/users'
import ImageUploader from '@/components/common/ImageUploader.vue'
import UserAvatar from '@/components/user/UserAvatar.vue'
import { useAuthStore } from '@/stores/auth'
import { formatDateTime } from '@/utils/format/datetime'
import { toFormRule } from '@/utils/validation/form-rule'
import {
  BIOGRAPHY_MAX_LENGTH,
  USER_NAME_MAX_LENGTH,
  validateBiography,
  validateEmail,
  validateUserName,
} from '@/utils/validation/user'

/**
 * 個人檔案編輯。
 *
 * 手機號碼是登入帳號，變更它需要另一套驗證流程（例如簡訊驗證），
 * 不該混在個人檔案編輯裡，因此只顯示不可修改。
 */

const auth = useAuthStore()
const message = useMessage()

const formRef = ref<FormInst | null>(null)
const loading = ref(false)
const submitting = ref(false)
const phoneNumber = ref(auth.user?.phoneNumber ?? '')
const joinedAt = ref(auth.user?.createdAt ?? '')

const model = ref({
  userName: auth.user?.userName ?? '',
  email: auth.user?.email ?? '',
  biography: auth.user?.biography ?? '',
  coverImage: auth.user?.coverImage ?? null,
})

const rules: FormRules = {
  userName: toFormRule(validateUserName),
  email: toFormRule(validateEmail),
  biography: toFormRule(validateBiography),
}

/**
 * 進頁面時重新抓一次個人檔案。
 *
 * store 裡的資料來自登入當下，可能已經過時（例如在另一個分頁改過）；
 * 編輯表單若以舊資料為底，送出時會把別處的變更蓋掉。
 */
onMounted(async () => {
  loading.value = true
  try {
    const user = await fetchCurrentUser()
    auth.setUser(user)
    phoneNumber.value = user.phoneNumber
    joinedAt.value = user.createdAt
    model.value = {
      userName: user.userName,
      email: user.email ?? '',
      biography: user.biography ?? '',
      coverImage: user.coverImage ?? null,
    }
  } catch (error) {
    message.error(error instanceof ApiClientError ? error.message : '載入個人檔案失敗')
  } finally {
    loading.value = false
  }
})

async function handleSubmit(): Promise<void> {
  try {
    await formRef.value?.validate()
  } catch {
    return
  }

  submitting.value = true
  try {
    // PUT 為全欄位取代語意：選填欄位留空時送 null，代表清空而不是維持原值
    const updated = await updateCurrentUser({
      userName: model.value.userName.trim(),
      email: model.value.email.trim() || null,
      biography: model.value.biography.trim() || null,
      coverImage: model.value.coverImage,
    })
    auth.setUser(updated)
    message.success('個人檔案已更新')
  } catch (error) {
    message.error(error instanceof ApiClientError ? error.message : '更新失敗，請稍後再試')
  } finally {
    submitting.value = false
  }
}
</script>

<template>
  <n-spin :show="loading">
    <n-card
      title="個人檔案"
      :bordered="false"
    >
      <div class="profile__identity">
        <UserAvatar
          :name="model.userName"
          :image="model.coverImage"
          :size="56"
        />
        <div>
          <p class="profile__phone">
            {{ phoneNumber }}
          </p>
          <p
            v-if="joinedAt"
            class="profile__joined"
          >
            加入於 {{ formatDateTime(joinedAt) }}
          </p>
        </div>
      </div>

      <n-divider />

      <n-form
        ref="formRef"
        :model="model"
        :rules="rules"
        label-placement="top"
        :show-require-mark="false"
        @submit.prevent="handleSubmit"
      >
        <n-form-item
          label="使用者名稱"
          path="userName"
        >
          <n-input
            v-model:value="model.userName"
            :maxlength="USER_NAME_MAX_LENGTH"
            show-count
          />
        </n-form-item>

        <n-form-item
          label="電子郵件（選填）"
          path="email"
        >
          <n-input
            v-model:value="model.email"
            placeholder="you@example.com"
          />
        </n-form-item>

        <n-form-item
          label="自我介紹（選填）"
          path="biography"
        >
          <n-input
            v-model:value="model.biography"
            type="textarea"
            :autosize="{ minRows: 3, maxRows: 8 }"
            :maxlength="BIOGRAPHY_MAX_LENGTH"
            show-count
            placeholder="介紹一下你自己…"
          />
        </n-form-item>

        <n-form-item label="頭像圖片（選填）">
          <ImageUploader
            v-model="model.coverImage"
            placeholder="上傳頭像"
          />
        </n-form-item>

        <n-button
          type="primary"
          attr-type="submit"
          :loading="submitting"
        >
          儲存變更
        </n-button>
      </n-form>
    </n-card>
  </n-spin>
</template>

<style scoped>
.profile__identity {
  align-items: center;
  display: flex;
  gap: 1rem;
}

.profile__phone {
  font-weight: 600;
  margin: 0;
}

.profile__joined {
  color: var(--n-text-color-3);
  font-size: 0.8125rem;
  margin: 0.125rem 0 0;
}
</style>
