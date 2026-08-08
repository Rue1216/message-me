import { onBeforeUnmount, watch, type Ref } from 'vue'

/**
 * 捲到底時自動載入下一頁。
 *
 * <p>以 IntersectionObserver 實作而非監聽 scroll 事件：後者每次捲動都會觸發，
 * 需要自行節流，而且得計算元素位置；前者由瀏覽器在合成執行緒上判斷，
 * 不會因為主執行緒忙碌而漏掉或延遲。
 *
 * <p>`rootMargin` 讓哨兵在真正進入畫面前 200px 就觸發，
 * 使用者通常在下一頁載入完成後才捲到底，感覺不到等待。
 *
 * <p>哨兵元素由呼叫端提供而非在這裡建立：模板參考屬於元件，
 * 由元件以 `useTemplateRef` 取得再傳進來，兩邊的責任比較清楚。
 *
 * @param sentinel 列表末端的哨兵元素
 */
export function useInfiniteScroll(
  sentinel: Ref<HTMLElement | null>,
  options: {
    hasMore: () => boolean
    isLoading: () => boolean
    onLoadMore: () => void
  },
): void {
  let observer: IntersectionObserver | null = null

  function disconnect(): void {
    observer?.disconnect()
    observer = null
  }

  watch(
    sentinel,
    (element) => {
      disconnect()
      if (!element) {
        return
      }

      observer = new IntersectionObserver(
        (entries) => {
          // 已在載入中就不重複觸發：哨兵可能在載入期間持續留在畫面內
          if (entries[0]?.isIntersecting && options.hasMore() && !options.isLoading()) {
            options.onLoadMore()
          }
        },
        { rootMargin: '200px' },
      )
      observer.observe(element)
    },
    { immediate: true },
  )

  onBeforeUnmount(disconnect)
}
