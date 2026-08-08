<script setup lang="ts">
import { ref } from 'vue'
import { RouterLink, useRoute, useRouter } from 'vue-router'

import { login } from '@/api/resources/auth'
import { ApiClientError } from '@/api/client/http'
import AppButton from '@/components/ui/AppButton.vue'
import AppCard from '@/components/ui/AppCard.vue'
import AppInput from '@/components/ui/AppInput.vue'
import FormField from '@/components/ui/FormField.vue'
import { useFormValidation } from '@/composables/useFormValidation'
import { useToast } from '@/composables/useToast'
import { useAuthStore } from '@/stores/auth'

const auth = useAuthStore()
const route = useRoute()
const router = useRouter()
const toast = useToast()

const submitting = ref(false)
const model = ref({ phoneNumber: '', password: '' })

/**
 * 登入表單的規則刻意比註冊寬鬆——只檢查有沒有填。
 *
 * 若在這裡擋掉格式不符的輸入，回饋就會與「格式正確但帳密錯誤」不同，
 * 反而讓人能從前端反應推測哪些手機號碼存在。後端的 LoginRequest 同樣只用 @NotBlank。
 */
const { errors, validateOnBlur, revalidate, validateAll } = useFormValidation(model, {
  phoneNumber: (value) => (value.trim() ? null : '請填寫手機號碼'),
  password: (value) => (value ? null : '請填寫密碼'),
})

/** 登入後要去的地方：被導航守衛攔下時記在 query 的原始位置，否則回動態牆。 */
function redirectTarget(): string {
  const redirect = route.query.redirect
  // 只接受站內的絕對路徑，避免被塞入外部網址而變成開放轉址
  if (typeof redirect === 'string' && redirect.startsWith('/') && !redirect.startsWith('//')) {
    return redirect
  }
  return '/'
}

async function handleSubmit(): Promise<void> {
  if (!validateAll()) {
    return
  }

  submitting.value = true
  try {
    const result = await login({
      phoneNumber: model.value.phoneNumber.trim(),
      password: model.value.password,
    })
    auth.signIn(result)
    toast.success(`歡迎回來，${result.user.userName}`)
    await router.replace(redirectTarget())
  } catch (error) {
    toast.error(error instanceof ApiClientError ? error.message : '登入失敗，請稍後再試')
  } finally {
    submitting.value = false
  }
}
</script>

<template>
  <div class="mx-auto mt-8 max-w-sm">
    <AppCard class="p-6">
      <h1 class="mb-5 text-xl font-bold">
        登入
      </h1>

      <form
        class="flex flex-col gap-4"
        @submit.prevent="handleSubmit"
      >
        <FormField
          id="login-phone"
          v-slot="{ describedBy, invalid }"
          label="手機號碼"
          :error="errors.phoneNumber"
        >
          <AppInput
            id="login-phone"
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
          id="login-password"
          v-slot="{ describedBy, invalid }"
          label="密碼"
          :error="errors.password"
        >
          <AppInput
            id="login-password"
            v-model="model.password"
            type="password"
            placeholder="請輸入密碼"
            autocomplete="current-password"
            :invalid="invalid"
            :aria-describedby="describedBy"
            @blur="validateOnBlur('password')"
            @input="revalidate('password')"
          />
        </FormField>

        <AppButton
          type="submit"
          class="w-full"
          :loading="submitting"
        >
          登入
        </AppButton>
      </form>

      <p class="mt-5 border-t border-border pt-4 text-sm text-muted-foreground">
        還沒有帳號？
        <RouterLink
          :to="{ name: 'register' }"
          class="font-medium text-primary hover:underline"
        >
          立即註冊
        </RouterLink>
      </p>
    </AppCard>
  </div>
</template>
