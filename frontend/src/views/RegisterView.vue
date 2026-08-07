<script setup lang="ts">
import { ref } from 'vue'
import { useMessage, type FormInst, type FormRules } from 'naive-ui'
import { useRouter } from 'vue-router'

import { login, register } from '@/api/auth'
import { ApiClientError } from '@/api/http'
import { useAuthStore } from '@/stores/auth'
import {
  PASSWORD_MAX_LENGTH,
  USER_NAME_MAX_LENGTH,
  toFormRule,
  validateEmail,
  validatePassword,
  validatePhoneNumber,
  validateUserName,
} from '@/utils/validation'

const auth = useAuthStore()
const router = useRouter()
const message = useMessage()

const formRef = ref<FormInst | null>(null)
const submitting = ref(false)
const model = ref({
  phoneNumber: '',
  userName: '',
  password: '',
  confirmPassword: '',
  email: '',
})

const rules: FormRules = {
  phoneNumber: toFormRule(validatePhoneNumber),
  userName: toFormRule(validateUserName),
  password: toFormRule(validatePassword),
  confirmPassword: toFormRule((value) => {
    if (!value) {
      return '請再次輸入密碼'
    }
    return value === model.value.password ? null : '兩次輸入的密碼不一致'
  }),
  email: toFormRule(validateEmail),
}

async function handleSubmit(): Promise<void> {
  try {
    await formRef.value?.validate()
  } catch {
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
    message.success(`註冊成功，歡迎加入，${result.user.userName}`)
    await router.replace({ name: 'home' })
  } catch (error) {
    message.error(error instanceof ApiClientError ? error.message : '註冊失敗，請稍後再試')
  } finally {
    submitting.value = false
  }
}
</script>

<template>
  <div class="auth-page">
    <n-card
      title="註冊"
      :bordered="false"
    >
      <n-form
        ref="formRef"
        :model="model"
        :rules="rules"
        label-placement="top"
        :show-require-mark="false"
        @submit.prevent="handleSubmit"
      >
        <n-form-item
          label="手機號碼"
          path="phoneNumber"
        >
          <n-input
            v-model:value="model.phoneNumber"
            placeholder="09xxxxxxxx"
            :input-props="{ autocomplete: 'username', inputmode: 'numeric' }"
          />
        </n-form-item>

        <n-form-item
          label="使用者名稱"
          path="userName"
        >
          <n-input
            v-model:value="model.userName"
            :maxlength="USER_NAME_MAX_LENGTH"
            show-count
            placeholder="顯示在動態牆上的名稱"
          />
        </n-form-item>

        <n-form-item
          label="密碼"
          path="password"
        >
          <n-input
            v-model:value="model.password"
            type="password"
            show-password-on="click"
            :maxlength="PASSWORD_MAX_LENGTH"
            placeholder="至少 8 個字元"
            :input-props="{ autocomplete: 'new-password' }"
          />
        </n-form-item>

        <n-form-item
          label="確認密碼"
          path="confirmPassword"
        >
          <n-input
            v-model:value="model.confirmPassword"
            type="password"
            show-password-on="click"
            :maxlength="PASSWORD_MAX_LENGTH"
            placeholder="再次輸入密碼"
            :input-props="{ autocomplete: 'new-password' }"
          />
        </n-form-item>

        <n-form-item
          label="電子郵件（選填）"
          path="email"
        >
          <n-input
            v-model:value="model.email"
            placeholder="you@example.com"
            :input-props="{ autocomplete: 'email', type: 'email' }"
          />
        </n-form-item>

        <n-button
          type="primary"
          block
          attr-type="submit"
          :loading="submitting"
        >
          建立帳號
        </n-button>
      </n-form>

      <template #footer>
        <span class="auth-page__hint">
          已經有帳號了？
          <RouterLink :to="{ name: 'login' }">前往登入</RouterLink>
        </span>
      </template>
    </n-card>
  </div>
</template>

<style scoped>
.auth-page {
  margin: 2rem auto 0;
  max-width: 24rem;
}

.auth-page__hint {
  color: var(--n-text-color-3);
  font-size: 0.875rem;
}
</style>
