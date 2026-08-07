<script setup lang="ts">
import { ref } from 'vue'
import { useMessage, type FormInst, type FormRules } from 'naive-ui'
import { useRoute, useRouter } from 'vue-router'

import { login } from '@/api/resources/auth'
import { ApiClientError } from '@/api/client/http'
import { useAuthStore } from '@/stores/auth'
import { toFormRule } from '@/utils/validation/form-rule'

const auth = useAuthStore()
const route = useRoute()
const router = useRouter()
const message = useMessage()

const formRef = ref<FormInst | null>(null)
const submitting = ref(false)
const model = ref({ phoneNumber: '', password: '' })

/**
 * 登入表單的規則刻意比註冊寬鬆——只檢查有沒有填。
 *
 * 若在這裡擋掉格式不符的輸入，回饋就會與「格式正確但帳密錯誤」不同，
 * 反而讓人能從前端反應推測哪些手機號碼存在。後端的 LoginRequest 同樣只用 @NotBlank。
 */
const rules: FormRules = {
  phoneNumber: toFormRule((value) => (value.trim() ? null : '請填寫手機號碼')),
  password: toFormRule((value) => (value ? null : '請填寫密碼')),
}

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
  try {
    await formRef.value?.validate()
  } catch {
    return
  }

  submitting.value = true
  try {
    const result = await login({
      phoneNumber: model.value.phoneNumber.trim(),
      password: model.value.password,
    })
    auth.signIn(result)
    message.success(`歡迎回來，${result.user.userName}`)
    await router.replace(redirectTarget())
  } catch (error) {
    message.error(error instanceof ApiClientError ? error.message : '登入失敗，請稍後再試')
  } finally {
    submitting.value = false
  }
}
</script>

<template>
  <div class="auth-page">
    <n-card
      title="登入"
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
          label="密碼"
          path="password"
        >
          <n-input
            v-model:value="model.password"
            type="password"
            show-password-on="click"
            placeholder="請輸入密碼"
            :input-props="{ autocomplete: 'current-password' }"
            @keyup.enter="handleSubmit"
          />
        </n-form-item>

        <n-button
          type="primary"
          block
          attr-type="submit"
          :loading="submitting"
        >
          登入
        </n-button>
      </n-form>

      <template #footer>
        <span class="auth-page__hint">
          還沒有帳號？
          <RouterLink :to="{ name: 'register' }">立即註冊</RouterLink>
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
