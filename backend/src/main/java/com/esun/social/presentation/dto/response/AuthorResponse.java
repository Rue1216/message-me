package com.esun.social.presentation.dto.response;

/**
 * 內容作者的顯示資訊，內嵌於發文與留言的回應中。
 *
 * <p>只有畫面上會用到的欄位。作者的手機號碼與電子郵件不在此列——
 * 動態牆是公開的，一則發文若帶出作者的聯絡方式，整個平台的個資就攤開了。
 */
public record AuthorResponse(long userId, String userName, String coverImage) {}
