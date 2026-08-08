<script setup lang="ts">
import { ref } from 'vue'
import { RouterLink, useRouter } from 'vue-router'

import { login, register } from '@/api/resources/auth'
import { ApiClientError } from '@/api/client/http'
import AppButton from '@/components/ui/AppButton.vue'
import AppCard from '@/components/ui/AppCard.vue'
import AppInput from '@/components/ui/AppInput.vue'
import FormField from '@/components/ui/FormField.vue'
import { useFormValidation } from '@/composables/useFormValidation'
import { useToast } from '@/composables/useToast'
import { useAuthStore } from '@/stores/auth'
import {
  PASSWORD_MAX_LENGTH,
  PASSWORD_MIN_LENGTH,
  validateEmail,
  validatePassword,
  validatePhoneNumber,
  validateUserName,
} from '@/utils/validation/user'

const auth = useAuthStore()
const router = useRouter()
const toast = useToast()

const submitting = ref(false)
const model = ref({
  phoneNumber: '',
  userName: '',
  password: '',
  confirmPassword: '',
  email: '',
})

const { errors, validateOnBlur, revalidate, validateAll } = useFormValidation(model, {
  phoneNumber: validatePhoneNumber,
  userName: validateUserName,
  password: validatePassword,
  confirmPassword: (value) => {
    if (!value) {
      return '請再次輸入密碼'
    }
    return value === model.value.password ? null : '兩次輸入的密碼不一致'
  },
  email: validateEmail,
})

async function handleSubmit(): Promise<void> {
  if (!validateAll()) {
    return
  }

  const phoneNumber = model.value.phoneNumber.trim()
  const password = model.value.password
  const email = model.value.email.trim()

  submitting.value = true
  try {
    await register({
      phoneNumber,
      userName: model.value.userName.trim(),
      password,
      // 選填欄位留空時送 null，與後端「沒給就是沒有」的語意一致
      email: email || null,
    })
    // 註冊端點刻意不發權杖（它只負責建立帳號），因此這裡緊接著走一次正式的登入流程，
    // 免去使用者才剛填完表單又要輸入一次帳密。
    const result = await login({ phoneNumber, password })
    auth.signIn(result)
    toast.success(`註冊成功，歡迎加入，${result.user.userName}`)
    await router.replace({ name: 'home' })
  } catch (error) {
    toast.error(error instanceof ApiClientError ? error.message : '註冊失敗，請稍後再試')
  } finally {
    submitting.value = false
  }
}
</script>

<template>
  <div class="mx-auto mt-8 max-w-sm">
    <AppCard class="p-6">
      <h1 class="mb-5 text-xl font-bold">
        註冊
      </h1>

      <form
        class="flex flex-col gap-4"
        @submit.prevent="handleSubmit"
      >
        <FormField
          id="register-phone"
          v-slot="{ describedBy, invalid }"
          label="手機號碼"
          :error="errors.phoneNumber"
          hint="這也是你的登入帳號"
        >
          <AppInput
            id="register-phone"
            v-model="model.phoneNumber"
            placeholder="09xxxxxxxx"
            autocomplete="username"
            inputmode="numeric"
            :invalid="invalid"
            :aria-describedby="describedBy"
            @blur="validateOnBlur('phoneNumber')"
            @input="revalidate('phoneNumber')"
          />
        </FormField>

        <FormField
          id="register-name"
          v-slot="{ describedBy, invalid }"
          label="使用者名稱"
          :error="errors.userName"
        >
          <AppInput
            id="register-name"
            v-model="model.userName"
            placeholder="想讓別人怎麼稱呼你？"
            autocomplete="nickname"
            :invalid="invalid"
            :aria-describedby="describedBy"
            @blur="validateOnBlur('userName')"
            @input="revalidate('userName')"
          />
        </FormField>

        <FormField
          id="register-password"
          v-slot="{ describedBy, invalid }"
          label="密碼"
          :error="errors.password"
          :hint="`${PASSWORD_MIN_LENGTH} 至 ${PASSWORD_MAX_LENGTH} 個字元`"
        >
          <AppInput
            id="register-password"
            v-model="model.password"
            type="password"
            autocomplete="new-password"
            :invalid="invalid"
            :aria-describedby="describedBy"
            @blur="validateOnBlur('password')"
            @input="revalidate('password'); revalidate('confirmPassword')"
          />
        </FormField>

        <FormField
          id="register-confirm"
          v-slot="{ describedBy, invalid }"
          label="確認密碼"
          :error="errors.confirmPassword"
        >
          <AppInput
            id="register-confirm"
            v-model="model.confirmPassword"
            type="password"
            autocomplete="new-password"
            :invalid="invalid"
            :aria-describedby="describedBy"
            @blur="validateOnBlur('confirmPassword')"
            @input="revalidate('confirmPassword')"
          />
        </FormField>

        <FormField
          id="register-email"
          v-slot="{ describedBy, invalid }"
          label="電子郵件"
          optional
          :error="errors.email"
        >
          <AppInput
            id="register-email"
            v-model="model.email"
            type="email"
            placeholder="you@example.com"
            autocomplete="email"
            :invalid="invalid"
            :aria-describedby="describedBy"
            @blur="validateOnBlur('email')"
            @input="revalidate('email')"
          />
        </FormField>

        <AppButton
          type="submit"
          class="w-full"
          :loading="submitting"
        >
          註冊
        </AppButton>
      </form>

      <p class="mt-5 border-t border-border pt-4 text-sm text-muted-foreground">
        已經有帳號了？
        <RouterLink
          :to="{ name: 'login' }"
          class="font-medium text-primary hover:underline"
        >
          前往登入
        </RouterLink>
      </p>
    </AppCard>
  </div>
</template>
