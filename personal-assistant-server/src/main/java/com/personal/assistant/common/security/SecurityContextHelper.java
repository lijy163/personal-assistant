package com.personal.assistant.common.security;

import com.personal.assistant.common.exception.BusinessException;
import com.personal.assistant.common.exception.ErrorCode;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;

/**
 * 获取当前登录用户信息的辅助类。
 */
public final class SecurityContextHelper {

    private SecurityContextHelper() {
    }

    public static Long currentUserId() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !(authentication.getPrincipal() instanceof AuthUser authUser)) {
            throw new BusinessException(ErrorCode.UNAUTHORIZED);
        }
        return authUser.userId();
    }

    public static AuthUser currentUser() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !(authentication.getPrincipal() instanceof AuthUser authUser)) {
            throw new BusinessException(ErrorCode.UNAUTHORIZED);
        }
        return authUser;
    }
}
