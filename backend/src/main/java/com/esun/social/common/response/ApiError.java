package com.esun.social.common.response;

/**
 * 失敗回應中的錯誤內容。
 *
 * @param code    穩定的錯誤代碼，取自 {@link com.esun.social.common.exception.ErrorCode} 的名稱，供前端分支使用
 * @param message 給使用者看的說明文字，可能隨版本調整措辭，不應被程式判斷
 */
public record ApiError(String code, String message) {}
