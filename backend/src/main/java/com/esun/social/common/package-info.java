/**
 * 共用層 —— 三層都會用到的橫切關注點。
 *
 * <p>包含統一回應格式（{@code response}）、錯誤模型與全域例外處理（{@code exception}）、
 * 設定類（{@code config}）、安全機制（{@code security}）與工具（{@code util}）。
 *
 * <p>本層不得反向依賴 presentation / business / data 任何一層，
 * 以確保它可以被三層同時引用而不產生循環。
 */
package com.esun.social.common;
