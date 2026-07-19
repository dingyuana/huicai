package com.huicai.base.system.util;

import com.huicai.config.security.LoginUser;
import com.huicai.common.exception.BusinessException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;

/**
 * 安全工具类 - 获取当前登录用户信息
 */
public class SecurityUtils {

    /**
     * 获取当前登录用户ID
     */
    public static Long getCurrentUserId() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !authentication.isAuthenticated()) {
            throw BusinessException.unauthorized("未登录");
        }

        Object principal = authentication.getPrincipal();
        if (principal instanceof LoginUser) {
            return ((LoginUser) principal).getUserId();
        }

        throw BusinessException.unauthorized("无法获取用户信息");
    }

    /**
     * 获取当前登录用户名
     */
    public static String getCurrentUsername() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !authentication.isAuthenticated()) {
            throw BusinessException.unauthorized("未登录");
        }
        return authentication.getName();
    }
}
