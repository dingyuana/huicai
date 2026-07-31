package com.huicai.api.rest;

import com.huicai.config.security.LoginUser;
import com.huicai.base.system.entity.UserEntity;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.List;

/**
 * 契约测试工具类 — 设置 Mock 安全上下文
 */
public class ContractTestSecurity {

    public static void setupSecurityContext() {
        UserEntity user = new UserEntity();
        user.setId(1L);
        user.setUsername("admin");
        user.setPassword("encoded");
        user.setUserType("AGENCY");
        user.setAgencyId(1L);
        user.setAgencyRole("AGENCY_ADMIN");
        user.setStatus("ACTIVE");
        user.setDeleted(0);

        LoginUser loginUser = new LoginUser(user, List.of(), null, 1L, "AGENCY", "AGENCY_ADMIN");
        UsernamePasswordAuthenticationToken auth =
                new UsernamePasswordAuthenticationToken(loginUser, null, loginUser.getAuthorities());
        SecurityContextHolder.getContext().setAuthentication(auth);
    }
}