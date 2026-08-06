import { describe, expect, it } from 'vitest'
import { mount } from '@vue/test-utils'

import App from './App.vue'

describe('App', () => {
  it('渲染應用程式名稱', () => {
    const wrapper = mount(App)

    expect(wrapper.text()).toContain('Message Me')
  })
})
