import { toast } from 'vue-sonner'

/**
 * 全站的短暫提示。
 *
 * <p>取代原本 Naive UI 的 `useMessage`。差別在於它不需要 provider 注入，
 * 因此可以在任何地方呼叫——包含 composable、Query 的 onError 回呼、
 * 以及 Axios 攔截器這類不在元件樹裡的位置。
 *
 * <p>刻意包一層而不直接用 vue-sonner：全站只會用到成功與失敗兩種，
 * 收斂成這個介面可以避免各處出現風格不一的呼叫方式，
 * 日後要換掉底層套件時也只有這一個檔案需要改。
 */
export function useToast() {
  return {
    success(message: string): void {
      toast.success(message)
    },
    error(message: string): void {
      toast.error(message)
    },
  }
}
