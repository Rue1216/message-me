import { useMutation, useQueryClient } from '@tanstack/vue-query'

import { updateCurrentUser } from '@/api/resources/users'
import { commentKeys, postKeys, userKeys } from '@/queries/queryKeys'
import { useAuthStore } from '@/stores/auth'
import type { UpdateProfilePayload } from '@/types/api'

/**
 * 個人檔案的更新。
 *
 * <p>收在這裡而不是寫在 view 裡，是因為「送出 PUT」只是這件事的一半：
 * 後端把作者的名稱與頭像**複製**進它回傳的每一則發文與留言（見 `Author` 型別），
 * 因此個人檔案一改，快取裡所有那些副本就同時過期了。
 * 少了這一步的症狀是「換了頭像，回到動態牆看到的還是舊的」——
 * 而且會拖到 staleTime 過去、下一次重新取得才自己好，看起來像時有時無的怪毛病。
 *
 * <p>採失效而非就地改寫快取：作者快照散落在動態牆、搜尋、標籤頁、發文詳情與各則留言中，
 * 逐一走訪兩種不同的分頁結構去改寫，程式碼的份量與出錯的機會都遠大於重新取得一次。
 * 失效只會讓「畫面上正在看的」查詢立刻重抓，不在畫面上的等下次掛載，成本是可控的。
 */
export function useProfileMutations() {
  const queryClient = useQueryClient()
  const auth = useAuthStore()

  const update = useMutation({
    mutationFn: (payload: UpdateProfilePayload) => updateCurrentUser(payload),

    onSuccess: (updated) => {
      auth.setUser(updated)
      // 權威結果直接寫回，本人的檔案不必為了自己剛做的修改再往返一次
      queryClient.setQueryData(userKeys.me(), updated)

      // 內嵌作者快照的三處：發文（列表與詳情）、留言、自己的公開個人頁
      void queryClient.invalidateQueries({ queryKey: postKeys.all })
      void queryClient.invalidateQueries({ queryKey: commentKeys.all })
      void queryClient.invalidateQueries({ queryKey: userKeys.detail(updated.userId) })
    },
  })

  return { update }
}
