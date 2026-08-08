<script setup lang="ts">
import { Plus, X } from '@lucide/vue'
import { useQuery } from '@tanstack/vue-query'
import { computed, ref, useId } from 'vue'

import { fetchPopularTags } from '@/api/resources/tags'
import AppInput from '@/components/ui/AppInput.vue'
import FormField from '@/components/ui/FormField.vue'
import { tagKeys } from '@/queries/queryKeys'
import { TAG_SEPARATORS, normaliseTag, validateTag } from '@/utils/validation/tag'

/**
 * 標籤輸入框。
 *
 * <p>標籤與內文是兩種不同的東西：內文是說給人看的句子，標籤是拿來分類與檢索的資料。
 * 過去把兩者塞在同一個欄位裡（內文寫 `#登山`，再由後端解析出來），
 * 結果是同一段文字在畫面上出現兩次——內文一次、標籤列一次。分開之後就不會了。
 *
 * <p>chip 顯示的是正規化後的形式：打 `Vue3` 得到的是 `vue3`。正規化本來就會發生
 * （否則 `Vue` 與 `vue` 會變成兩個標籤），讓它立刻可見，比送出後才悄悄改掉要誠實。
 */
const tags = defineModel<string[]>({ default: () => [] })

const fieldId = useId()
const buffer = ref('')
const errorMessage = ref<string | null>(null)

// 與 PopularTags.vue 共用同一組 query key 與 staleTime：
// 同一頁若側欄也開著熱門標籤，兩者只會發出一次請求
const { data: popular } = useQuery({
  queryKey: tagKeys.popular(),
  queryFn: () => fetchPopularTags(12),
  staleTime: 5 * 60_000,
})

// 已經選過的不再建議；最多列 8 個，再多就從輔助變成干擾
const suggestions = computed(
  () => popular.value?.filter((tag) => !tags.value.includes(tag.name)).slice(0, 8) ?? [],
)

/**
 * 把一段文字併進既有的清單，回傳結果；被擋下時寫入 errorMessage 並原樣退回。
 *
 * <p>刻意回傳新清單而不直接寫 `tags.value`：`defineModel` 在父層綁了 v-model 時，
 * 寫入只會發出事件，值要等父層把 prop 傳回來才更新。同一輪同步流程中連續寫入
 * 會一直讀到最初的舊值——貼上三個標籤最後只會剩下一個。累積完再一次寫入就沒有這個時間差。
 */
function withTag(list: string[], candidate: string): string[] {
  const tag = normaliseTag(candidate)
  // 空字串不帶意圖；重複則是意圖已經達成——兩者都不是錯誤
  if (!tag || list.includes(tag)) {
    return list
  }
  const problem = validateTag(tag, list)
  if (problem) {
    errorMessage.value = problem
    return list
  }
  // 成功新增就清掉錯誤：上一句說的是前一次輸入的問題，留著會變成對著一顆
  // 剛加進去的合法標籤指指點點。點熱門標籤快選走的也是這條路
  errorMessage.value = null
  return [...list, tag]
}

function add(candidate: string): void {
  tags.value = withTag(tags.value, candidate)
}

/**
 * 送出輸入框裡的內容。先依分隔符切開再逐一處理，
 * 貼上「登山, 露營 攝影」才會得到三顆標籤，而不是一段驗證失敗的文字。
 */
function commit(): void {
  // 進入時就先清一次：整段都是重複標籤時走不到 withTag 的成功分支，舊訊息得在這裡消失；
  // 底下用 errorMessage 判斷這一輪有沒有失敗，也不能讀到上一輪的殘留
  errorMessage.value = null
  let next = tags.value
  for (const part of buffer.value.split(TAG_SEPARATORS).filter(Boolean)) {
    next = withTag(next, part)
    // 被擋下時保留原文，使用者可以直接修改而不必重打；
    // 前面已經成功的幾顆仍然留著，不必連帶重打
    if (errorMessage.value) {
      tags.value = next
      return
    }
  }
  tags.value = next
  buffer.value = ''
}

function remove(tag: string): void {
  tags.value = tags.value.filter((existing) => existing !== tag)
  errorMessage.value = null
}

function handleKeydown(event: KeyboardEvent): void {
  // 組字中的按鍵屬於輸入法，不是使用者對這個欄位下的指令。
  // 注音與拼音都用空白選字、用 Enter 確認字詞，這裡若不先讓路，
  // preventDefault() 會把選字整個吃掉——標籤幾乎都是用輸入法打的中文，這條路一定會走到。
  // Firefox 在組字期間把 key 報成 'Process'，keyCode 229 則是各家瀏覽器共通的舊訊號，一併認。
  if (event.isComposing || event.key === 'Process' || event.keyCode === 229) {
    return
  }
  // 逗號與空白在這裡攔截而非交給 Vue 的按鍵修飾符：
  // `,` 與 `，` 都不是合法的修飾符名稱，寫成字串比對才涵蓋得到全形輸入
  if (['Enter', ',', '，', '、', ' '].includes(event.key)) {
    event.preventDefault()
    commit()
    return
  }
  // 還在打字時 Backspace 就該刪字元，不能連前一顆標籤一起吃掉
  if (event.key === 'Backspace' && !buffer.value) {
    const last = tags.value.at(-1)
    if (last) {
      remove(last)
    }
  }
}

/**
 * 清掉還沒送出的輸入與錯誤訊息。
 *
 * <p>由 PostForm 在它自己的 reset() 中呼叫：這兩者是元件內部的狀態，父層把 v-model 的
 * 標籤陣列清成空的並不會連帶清到它們，而這個元件也沒有被 re-key，不會重建。
 * 少了這一步，發文成功之後打到一半的字與紅色的錯誤訊息會原封不動地留給下一篇發文。
 */
function reset(): void {
  buffer.value = ''
  errorMessage.value = null
}

defineExpose({ reset })
</script>

<template>
  <FormField
    :id="fieldId"
    v-slot="{ describedBy, invalid }"
    label="標籤"
    optional
    :error="errorMessage"
    hint="以 Enter、逗號或空白分隔；不必自己輸入 #"
  >
    <ul
      v-if="tags.length"
      class="flex flex-wrap gap-1.5"
    >
      <li
        v-for="tag in tags"
        :key="tag"
        class="inline-flex items-center gap-1 rounded-full bg-muted px-2.5 py-0.5 text-xs font-medium text-muted-foreground"
      >
        #{{ tag }}
        <button
          type="button"
          :aria-label="`移除標籤 ${tag}`"
          class="rounded-full transition-colors hover:text-destructive"
          @click="remove(tag)"
        >
          <X
            class="size-3"
            aria-hidden="true"
          />
        </button>
      </li>
    </ul>

    <AppInput
      :id="fieldId"
      v-model="buffer"
      :invalid="invalid"
      :aria-describedby="describedBy"
      placeholder="例如：登山"
      @keydown="handleKeydown"
      @blur="commit"
    />

    <!-- 熱門標籤是輔助而非必要資訊，沒有資料時整區不佔版面 -->
    <ul
      v-if="suggestions.length"
      class="flex flex-wrap gap-1.5"
    >
      <li
        v-for="tag in suggestions"
        :key="tag.name"
      >
        <button
          type="button"
          :aria-label="`加入標籤 ${tag.name}`"
          class="inline-flex items-center gap-1 rounded-full border border-dashed border-input px-2.5 py-0.5 text-xs text-muted-foreground transition-colors hover:border-primary hover:text-primary"
          @click="add(tag.name)"
        >
          <Plus
            class="size-3"
            aria-hidden="true"
          />
          {{ tag.name }}
        </button>
      </li>
    </ul>
  </FormField>
</template>
