package com.personal.assistant.common.security;

/**
 * Security 上下文中存储的已认证用户信息。
 */
public record AuthUser(Long userId, String username) {
}
