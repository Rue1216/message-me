package com.esun.social.presentation.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * 新增留言的請求。
 *
 * @param content 留言內容，會先經 HTML 清洗再入庫
 */
public record CommentRequest(
        @NotBlank(message = "請填寫留言內容") @Size(max = 1000, message = "留言內容不可超過 1000 字") String content) {}
