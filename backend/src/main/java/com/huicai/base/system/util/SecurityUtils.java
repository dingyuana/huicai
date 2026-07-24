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
        return getLoginUser().getUserId();
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

    /**
     * 获取当前企业ID
     */
    public static Long getCurrentEnterpriseId() {
        return getLoginUser().getEnterpriseId();
    }

    /**
     * 获取当前用户类型
     */
    public static String getCurrentUserType() {
        return getLoginUser().getUserType();
    }

    /**
     * 获取当前代理公司ID
     */
    public static Long getCurrentAgencyId() {
        return getLoginUser().getAgencyId();
    }

    /**
     * 获取当前代理内角色
     */
    public static String getCurrentAgencyRole() {
        return getLoginUser().getAgencyRole();
    }

    private static LoginUser getLoginUser() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !authentication.isAuthenticated()) {
            throw BusinessException.unauthorized("未登录");
        }
        Object principal = authentication.getPrincipal();
        if (principal instanceof LoginUser) {
            return (LoginUser) principal;
        }
        throw BusinessException.unauthorized("无法获取用户信息");
    }
}
