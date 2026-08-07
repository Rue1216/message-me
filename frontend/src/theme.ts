import type { GlobalThemeOverrides } from 'naive-ui'

/**
 * Naive UI 主題微調。
 *
 * 只覆寫主色與圓角，其餘沿用預設淺色主題——調得越少，元件庫改版時要重新對齊的地方越少。
 */
export const themeOverrides: GlobalThemeOverrides = {
  common: {
    primaryColor: '#3f6ad8',
    primaryColorHover: '#5b83e6',
    primaryColorPressed: '#31559f',
    primaryColorSuppl: '#5b83e6',
    borderRadius: '8px',
    bodyColor: '#f4f6fb',
  },
}
