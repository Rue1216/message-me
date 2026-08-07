package com.esun.social.business.model;

/**
 * 登入成功後發給前端的存取權杖。
 *
 * @param accessToken      JWT
 * @param expiresInSeconds 有效秒數，讓前端能在到期前主動導回登入
 * @param user             權杖對應的使用者
 */
public record AuthToken(String accessToken, long expiresInSeconds, User user) {}
