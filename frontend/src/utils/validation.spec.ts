import { describe, expect, it } from 'vitest'

import {
  validateBiography,
  validateCommentContent,
  validateEmail,
  validatePassword,
  validatePhoneNumber,
  validatePostContent,
  validateUserName,
} from '@/utils/validation'

describe('validatePhoneNumber', () => {
  it('接受 09 開頭的 10 位數字', () => {
    expect(validatePhoneNumber('0912345678')).toBeNull()
  })

  it.each(['', '  ', '0912345', '09123456789', '0812345678', '09abcdefgh'])(
    '拒絕不符格式的 %s',
    (value) => {
      expect(validatePhoneNumber(value)).not.toBeNull()
    },
  )
})

describe('validatePassword', () => {
  it('接受 8 至 100 字元', () => {
    expect(validatePassword('12345678')).toBeNull()
    expect(validatePassword('a'.repeat(100))).toBeNull()
  })

  it('拒絕過短或過長的密碼', () => {
    expect(validatePassword('1234567')).not.toBeNull()
    expect(validatePassword('a'.repeat(101))).not.toBeNull()
  })

  it('不修剪前後空白：那也是使用者刻意輸入的字元', () => {
    // 修剪後只剩 6 個字元，若實作有 trim 這一行就會失敗
    expect(validatePassword('123456  ')).toBeNull()
  })
})

describe('validateUserName', () => {
  it('接受 1 至 50 字', () => {
    expect(validateUserName('小明')).toBeNull()
  })

  it('拒絕空白或超過 50 字', () => {
    expect(validateUserName('   ')).not.toBeNull()
    expect(validateUserName('字'.repeat(51))).not.toBeNull()
  })
})

describe('validateEmail', () => {
  it('選填：留空視為通過', () => {
    expect(validateEmail('')).toBeNull()
    expect(validateEmail('   ')).toBeNull()
  })

  it('接受基本格式', () => {
    expect(validateEmail('user@example.com')).toBeNull()
  })

  it.each(['user', 'user@', '@example.com', 'user@example', 'a b@example.com'])(
    '拒絕格式不正確的 %s',
    (value) => {
      expect(validateEmail(value)).not.toBeNull()
    },
  )
})

describe('validateBiography', () => {
  it('選填且上限 500 字', () => {
    expect(validateBiography('')).toBeNull()
    expect(validateBiography('字'.repeat(500))).toBeNull()
    expect(validateBiography('字'.repeat(501))).not.toBeNull()
  })
})

describe('validatePostContent', () => {
  it('必填且上限 5000 字', () => {
    expect(validatePostContent('今天天氣不錯')).toBeNull()
    expect(validatePostContent('   ')).not.toBeNull()
    expect(validatePostContent('字'.repeat(5001))).not.toBeNull()
  })
})

describe('validateCommentContent', () => {
  it('必填且上限 1000 字', () => {
    expect(validateCommentContent('說得好')).toBeNull()
    expect(validateCommentContent('')).not.toBeNull()
    expect(validateCommentContent('字'.repeat(1001))).not.toBeNull()
  })
})
