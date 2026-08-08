<script setup lang="ts">
import { ref } from 'vue'
import { useRouter } from 'vue-router'

import { ApiClientError } from '@/api/client/http'
import { changePassword, deleteAccount } from '@/api/resources/users'
import AppButton from '@/components/ui/AppButton.vue'
import AppCard from '@/components/ui/AppCard.vue'
import AppInput from '@/components/ui/AppInput.vue'
import ConfirmDialog from '@/components/ui/ConfirmDialog.vue'
import FormField from '@/components/ui/FormField.vue'
import { useFormValidation } from '@/composables/useFormValidation'
import { useToast } from '@/composables/useToast'
import { useAuthStore } from '@/stores/auth'
import { PASSWORD_MAX_LENGTH, PASSWORD_MIN_LENGTH, validatePassword } from '@/utils/validation/user'

/**
 * 帳號設定：修改密碼與刪除帳號。
 *
 * <p>兩者都要求輸入目前的密碼。權杖可能外洩，若只憑它就能改密碼或刪帳號，
 * 攻擊者可以直接把帳號接管過去；要求密碼讓「持有權杖」與「知道密碼」成為兩道獨立的關卡。
 *
 * <p>刪除帳號與修改密碼刻意放在同一頁但明顯分開的兩張卡片中，
 * 後者用紅色邊框標示為危險區域——不可逆的操作應該看起來就不一樣。
 */

const auth = useAuthStore()
const router = useRouter()
const toast = useToast()

// ---- 修改密碼 ----
const passwordModel = ref({ currentPassword: '', newPassword: '', confirmPassword: '' })
const changingPassword = ref(false)

const passwordForm = useFormValidation(passwordModel, {
  currentPassword: (value) => (value ? null : '請填寫目前的密碼'),
  newPassword: validatePassword,
  confirmPassword: (value) => {
    if (!value) {
      return '請再次輸入新密碼'
    }
    return value === passwordModel.value.newPassword ? null : '兩次輸入的密碼不一致'
  },
})

async function handleChangePassword(): Promise<void> {
  if (!passwordForm.validateAll()) {
    return
  }

  changingPassword.value = true
  try {
    await changePassword({
      currentPassword: passwordModel.value.currentPassword,
      newPassword: passwordModel.value.newPassword,
    })
    passwordModel.value = { currentPassword: '', newPassword: '', confirmPassword: '' }
    passwordForm.reset()
    toast.success('密碼已更新')
  } catch (error) {
    toast.error(error instanceof ApiClientError ? error.message : '更新失敗，請稍後再試')
  } finally {
    changingPassword.value = false
  }
}

// ---- 刪除帳號 ----
const deleteModel = ref({ password: '' })
const deleting = ref(false)
const showDeleteConfirm = ref(false)

const deleteForm = useFormValidation(deleteModel, {
  password: (value) => (value ? null : '請輸入密碼以確認身分'),
})

function requestDelete(): void {
  if (deleteForm.validateAll()) {
    showDeleteConfirm.value = true
  }
}

async function performDelete(): Promise<void> {
  deleting.value = true
  try {
    await deleteAccount({ password: deleteModel.value.password })
    auth.signOut()
    toast.success('帳號已刪除')
    await router.replace({ name: 'home' })
  } catch (error) {
    toast.error(error instanceof ApiClientError ? error.message : '刪除失敗，請稍後再試')
  } finally {
    deleting.value = false
  }
}
</script>

<template>
  <div class="flex flex-col gap-6">
    <h1 class="text-xl font-bold">
      帳號設定
    </h1>

    <AppCard
      as="section"
      class="p-5"
      aria-labelledby="change-password-heading"
    >
      <h2
        id="change-password-heading"
        class="mb-4 font-semibold"
      >
        修改密碼
      </h2>

      <form
        class="flex flex-col gap-4"
        @submit.prevent="handleChangePassword"
      >
        <FormField
          id="current-password"
          v-slot="{ describedBy, invalid }"
          label="目前的密碼"
          :error="passwordForm.errors.currentPassword"
        >
          <AppInput
            id="current-password"
            v-model="passwordModel.currentPassword"
            type="password"
            autocomplete="current-password"
            :invalid="invalid"
            :aria-describedby="describedBy"
            @blur="passwordForm.validateOnBlur('currentPassword')"
            @input="passwordForm.revalidate('currentPassword')"
          />
        </FormField>

        <FormField
          id="new-password"
          v-slot="{ describedBy, invalid }"
          label="新密碼"
          :error="passwordForm.errors.newPassword"
          :hint="`${PASSWORD_MIN_LENGTH} 至 ${PASSWORD_MAX_LENGTH} 個字元`"
        >
          <AppInput
            id="new-password"
            v-model="passwordModel.newPassword"
            type="password"
            autocomplete="new-password"
            :invalid="invalid"
            :aria-describedby="describedBy"
            @blur="passwordForm.validateOnBlur('newPassword')"
            @input="passwordForm.revalidate('newPassword'); passwordForm.revalidate('confirmPassword')"
          />
        </FormField>

        <FormField
          id="confirm-new-password"
          v-slot="{ describedBy, invalid }"
          label="確認新密碼"
          :error="passwordForm.errors.confirmPassword"
        >
          <AppInput
            id="confirm-new-password"
            v-model="passwordModel.confirmPassword"
            type="password"
            autocomplete="new-password"
            :invalid="invalid"
            :aria-describedby="describedBy"
            @blur="passwordForm.validateOnBlur('confirmPassword')"
            @input="passwordForm.revalidate('confirmPassword')"
          />
        </FormField>

        <div class="flex justify-end">
          <AppButton
            type="submit"
            :loading="changingPassword"
          >
            更新密碼
          </AppButton>
        </div>
      </form>

      <p class="mt-4 border-t border-border pt-4 text-xs text-muted-foreground">
        本系統使用無狀態的存取權杖，改密碼不會讓其他已登入的裝置立即登出——
        它們會在權杖原本的有效期結束後自然失效。
      </p>
    </AppCard>

    <AppCard
      as="section"
      class="border-destructive/40 p-5"
      aria-labelledby="delete-account-heading"
    >
      <h2
        id="delete-account-heading"
        class="mb-2 font-semibold text-destructive"
      >
        刪除帳號
      </h2>
      <p class="mb-4 text-sm text-muted-foreground">
        刪除後你將無法再登入，個人資料會被清除，手機號碼則會釋出可供重新註冊。
        <strong class="text-foreground">你過去的發文與留言會保留在原本的討論串中</strong>，
        作者顯示為「已刪除的使用者」——這樣才不會在別人的對話裡留下缺口。
        此操作無法復原。
      </p>

      <form
        class="flex flex-col gap-4"
        @submit.prevent="requestDelete"
      >
        <FormField
          id="delete-password"
          v-slot="{ describedBy, invalid }"
          label="請輸入密碼以確認身分"
          :error="deleteForm.errors.password"
        >
          <AppInput
            id="delete-password"
            v-model="deleteModel.password"
            type="password"
            autocomplete="current-password"
            :invalid="invalid"
            :aria-describedby="describedBy"
            @blur="deleteForm.validateOnBlur('password')"
            @input="deleteForm.revalidate('password')"
          />
        </FormField>

        <div class="flex justify-end">
          <AppButton
            type="submit"
            variant="destructive"
            :loading="deleting"
          >
            刪除我的帳號
          </AppButton>
        </div>
      </form>
    </AppCard>

    <ConfirmDialog
      v-model:open="showDeleteConfirm"
      title="確定要刪除帳號嗎？"
      description="這個操作無法復原。你將立即登出，且無法再以這個帳號登入。"
      confirm-label="確定刪除"
      @confirm="performDelete"
    />
  </div>
</template>
