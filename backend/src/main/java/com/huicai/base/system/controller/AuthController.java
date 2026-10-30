package com.huicai.base.system.controller;

import com.huicai.agency.tenant.entity.EnterpriseEntity;
import com.huicai.agency.tenant.mapper.AgencyEnterpriseMapper;
import com.huicai.agency.tenant.mapper.EnterpriseMapper;
import com.huicai.agency.tenant.vo.EnterpriseSimpleVO;
import com.huicai.agency.user.entity.AgencyUserEntity;
import com.huicai.agency.user.mapper.AgencyUserEnterpriseMapper;
import com.huicai.agency.user.mapper.AgencyUserMapper;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.huicai.common.context.EnterpriseContextHolder;
import com.huicai.common.exception.BusinessException;
import com.huicai.common.response.R;
import com.huicai.config.security.JwtProvider;
import com.huicai.base.system.entity.MenuEntity;
import com.huicai.base.system.entity.RoleEntity;
import com.huicai.base.system.entity.UserEntity;
import com.huicai.base.system.mapper.MenuMapper;
import com.huicai.base.system.mapper.RoleMenuMapper;
import com.huicai.base.system.mapper.UserMapper;
import com.huicai.base.system.mapper.UserRoleMapper;
import com.huicai.base.system.service.MenuService;
import com.huicai.base.system.service.LoginBruteForceGuard;
import jakarta.servlet.http.HttpServletRequest;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/v1/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AuthenticationManager authenticationManager;
    private final JwtProvider jwtProvider;
    private final UserMapper userMapper;
    private final UserRoleMapper userRoleMapper;
    private final RoleMenuMapper roleMenuMapper;
    private final MenuMapper menuMapper;
    private final MenuService menuService;
    private final AgencyEnterpriseMapper agencyEnterpriseMapper;
    private final EnterpriseMapper enterpriseMapper;
    private final AgencyUserMapper agencyUserMapper;
    private final AgencyUserEnterpriseMapper agencyUserEnterpriseMapper;
    private final LoginBruteForceGuard bruteForceGuard;

    /**
     * SPEC-P124：用 refresh token 换新的 access token。
     *
     * <p><b>此前为何没有它</b>：登录一直会签发 refresh token（有效期 7 天），
     * 但<b>没有任何端点能兑换它</b>，前端 401 只清 localStorage 直接登出 ——
     * 即 refresh 机制「签发了却从未被使用」。这正是「缩短 access token 有效期」
     * 迟迟不能落地的前置障碍。
     *
     * <p><b>企业上下文必须延续</b>：refresh token 携带与 access token 相同的 claim
     * （含 {@code enterpriseId}），换证后代理会计仍停留在切换后的客户，
     * 不会因 token 过期被无声弹回客户列表。
     *
     * <p><b>失败一律 401</b>：refresh 无效/过期时不得降级放行。
     */
    @PostMapping("/refresh")
    public R<TokenResponse> refresh(@RequestBody RefreshRequest request) {
        String refreshToken = request.getRefreshToken();
        if (refreshToken == null || !jwtProvider.validateRefreshToken(refreshToken)) {
            throw BusinessException.unauthorized("登录已过期，请重新登录");
        }

        String username = jwtProvider.getUsernameFromToken(refreshToken);
        // 重新核对账号状态：refresh token 有效期内账号可能已被停用/锁定
        UserEntity user = userMapper.selectByUsername(username);
        if (user == null || !Integer.valueOf(0).equals(user.getDeleted())
                || !"ACTIVE".equals(user.getStatus())) {
            throw BusinessException.unauthorized("账号不可用，请重新登录");
        }

        List<Long> roleIds = userRoleMapper.getRoleIdsByUserId(user.getId());
        String accessToken = jwtProvider.generateAccessToken(username, user.getId(),
                roleIds.stream().map(String::valueOf).collect(Collectors.toList()),
                jwtProvider.getEnterpriseIdFromToken(refreshToken),
                user.getAgencyId(),
                user.getUserType() != null ? user.getUserType() : "ENTERPRISE",
                user.getAgencyRole());

        return R.ok(new TokenResponse(accessToken, "Bearer"));
    }

    @PostMapping("/login")
    public R<LoginResponse> login(@RequestBody LoginRequest request, HttpServletRequest httpRequest) {
        String ip = getClientIp(httpRequest);
        String username = request.getUsername();

        // R2: 暴力破解防护 —— 先【只读】检查锁定状态，未锁才允许走认证
        // 🔴 此处原调 onLoginFailure（那是「记录失败」的写方法），每次尝试都先计一次失败、
        //    失败时再计一次 ⇒ 阈值 5 于第 3 次尝试即被打穿。改为 checkLocked 后不再写计数。
        try {
            bruteForceGuard.checkLocked(ip, username);
        } catch (BusinessException e) {
            return R.fail(e.getCode(), e.getMessage());
        }

        try {
            Authentication authentication = authenticationManager.authenticate(
                    new UsernamePasswordAuthenticationToken(username, request.getPassword()));

            // 登录成功：清除计数
            bruteForceGuard.onLoginSuccess(ip, username);
            SecurityContextHolder.getContext().setAuthentication(authentication);

            UserEntity user = userMapper.selectByUsername(username);

            // Get roles
            List<Long> roleIds = userRoleMapper.getRoleIdsByUserId(user.getId());

            // Get permissions
            List<String> permissions = menuService.getUserButtonPermissions(user.getId());

            // S-26: 多租户字段
            String userType = user.getUserType() != null ? user.getUserType() : "ENTERPRISE";
            Long enterpriseId = user.getEnterpriseId();
            Long agencyId = user.getAgencyId();
            String agencyRole = user.getAgencyRole();

            // 登录时获取企业列表
            List<EnterpriseSimpleVO> enterpriseList = new ArrayList<>();
            if ("SUPER_ADMIN".equals(userType)) {
                // 超级管理员看全部企业
                List<EnterpriseEntity> allEnterprises = enterpriseMapper.selectList(
                        new LambdaQueryWrapper<EnterpriseEntity>()
                                .eq(EnterpriseEntity::getDeleted, 0));
                for (EnterpriseEntity ent : allEnterprises) {
                    enterpriseList.add(new EnterpriseSimpleVO(
                            ent.getId(), ent.getEnterpriseName(),
                            ent.getTaxId(), ent.getStatus(), ent.getSeedDataDone()));
                }
            } else if ("AGENCY".equals(userType) && agencyId != null) {
                // V2.0: 按 agencyRole 过滤企业列表
                List<Long> enterpriseIds;
                if ("AGENCY_ADMIN".equals(agencyRole) || "REVIEWER".equals(agencyRole)) {
                    // 经理和审核员看代理公司下全部企业
                    enterpriseIds = agencyEnterpriseMapper.getEnterpriseIdsByAgencyId(agencyId);
                } else {
                    // 会计和助理只看分配给自己的企业
                    AgencyUserEntity agencyUser = agencyUserMapper.selectOne(
                            new LambdaQueryWrapper<AgencyUserEntity>()
                                    .eq(AgencyUserEntity::getUserId, user.getId())
                                    .eq(AgencyUserEntity::getDeleted, 0));
                    if (agencyUser != null) {
                        enterpriseIds = agencyUserEnterpriseMapper.getEnterpriseIdsByAgencyUserId(agencyUser.getId());
                    } else {
                        enterpriseIds = List.of();
                    }
                }
                for (Long eid : enterpriseIds) {
                    EnterpriseEntity ent = enterpriseMapper.selectById(eid);
                    if (ent != null) {
                        enterpriseList.add(new EnterpriseSimpleVO(
                                ent.getId(), ent.getEnterpriseName(),
                                ent.getTaxId(), ent.getStatus(), ent.getSeedDataDone()));
                    }
                }
            }

            // ENTERPRISE 用户（企业自有账号）：补上当前归属企业，让前端能取到企业名称
            if ("ENTERPRISE".equals(userType) && enterpriseList.isEmpty()) {
                EnterpriseEntity ent = enterpriseMapper.selectById(enterpriseId);
                if (ent != null) {
                    enterpriseList.add(new EnterpriseSimpleVO(
                            ent.getId(), ent.getEnterpriseName(),
                            ent.getTaxId(), ent.getStatus(), ent.getSeedDataDone()));
                }
            }

            // 设置企业上下文（SUPER_ADMIN 不设，跳过数据权限拦截）
            if (enterpriseId != null && !"SUPER_ADMIN".equals(userType)) {
                EnterpriseContextHolder.set(enterpriseId);
            }

            String accessToken = jwtProvider.generateAccessToken(username, user.getId(),
                    roleIds.stream().map(String::valueOf).collect(Collectors.toList()),
                    enterpriseId, agencyId, userType, agencyRole);
            String refreshToken = jwtProvider.generateRefreshToken(username, user.getId(),
                    roleIds.stream().map(String::valueOf).collect(Collectors.toList()),
                    enterpriseId, agencyId, userType, agencyRole);

            LoginResponse response = new LoginResponse();
            response.setToken(accessToken);
            response.setRefreshToken(refreshToken);
            response.setTokenType("Bearer");
            response.setUserType(userType);
            response.setHomeEnterpriseId(enterpriseId);
            response.setCurrentEnterpriseId(enterpriseId);
            response.setEnterpriseSelectionRequired(
                    "AGENCY".equals(userType) && enterpriseId == null);
            response.setAgencyId(agencyId);
            response.setAgencyRole(agencyRole);
            response.setEnterpriseList(enterpriseList);
            UserInfoResponse loginInfo = new UserInfoResponse(user.getId(), user.getUsername(),
                    user.getRealName(), user.getNickname(), user.getEmail(), user.getPhone(),
                    user.getAvatar(), user.getDeptId(), roleIds, permissions,
                    userType, agencyId, null, enterpriseId, false, agencyRole, enterpriseList);
            loginInfo.setHomeEnterpriseId(enterpriseId);
            loginInfo.setCurrentEnterpriseId(enterpriseId);
            loginInfo.setEnterpriseSelectionRequired(
                    "AGENCY".equals(userType) && enterpriseId == null);
            response.setUserInfo(loginInfo);

            return R.ok(response);
        } catch (BadCredentialsException e) {
            // 密码错误：记录失败计数，然后抛异常让 GlobalExceptionHandler 统一处理（返 400）
            bruteForceGuard.onLoginFailure(ip, username);
            throw e;
        }
    }

    /**
     * 获取客户端 IP（兼容代理场景）。
     */
    private String getClientIp(HttpServletRequest request) {
        String xff = request.getHeader("X-Forwarded-For");
        if (xff != null && !xff.isEmpty() && !"unknown".equalsIgnoreCase(xff)) {
            return xff.split(",")[0].trim();
        }
        String realIp = request.getHeader("X-Real-IP");
        if (realIp != null && !realIp.isEmpty() && !"unknown".equalsIgnoreCase(realIp)) {
            return realIp.trim();
        }
        return request.getRemoteAddr();
    }

    @GetMapping("/userinfo")
    public R<UserInfoResponse> getUserInfo() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        String username = authentication.getName();

        UserEntity user = userMapper.selectByUsername(username);
        if (user == null) {
            return R.fail("用户不存在");
        }

        List<Long> roleIds = userRoleMapper.getRoleIdsByUserId(user.getId());
        List<String> permissions = menuService.getUserButtonPermissions(user.getId());

        // S-26: 多租户字段
        String userType = user.getUserType() != null ? user.getUserType() : "ENTERPRISE";
        Long agencyId = user.getAgencyId();
        // 优先使用 EnterpriseContextHolder（由 X-Enterprise-Id header 设置）
        // 避免覆盖前端切换后的企业 ID
        Long enterpriseId = EnterpriseContextHolder.get();
        if (enterpriseId == null) {
            enterpriseId = user.getEnterpriseId();
        }
        String agencyRole = user.getAgencyRole();

        // AGENCY 用户获取绑定的企业列表
        List<EnterpriseSimpleVO> enterpriseList = new ArrayList<>();
        if ("SUPER_ADMIN".equals(userType)) {
            // 超级管理员看全部企业
            List<EnterpriseEntity> allEnterprises = enterpriseMapper.selectList(
                    new LambdaQueryWrapper<EnterpriseEntity>()
                            .eq(EnterpriseEntity::getDeleted, 0));
            for (EnterpriseEntity ent : allEnterprises) {
                enterpriseList.add(new EnterpriseSimpleVO(
                        ent.getId(), ent.getEnterpriseName(),
                        ent.getTaxId(), ent.getStatus(), ent.getSeedDataDone()));
            }
        } else if ("AGENCY".equals(userType) && agencyId != null) {
            // V2.0: 按 agencyRole 过滤企业列表
            List<Long> enterpriseIds;
            if ("AGENCY_ADMIN".equals(agencyRole) || "REVIEWER".equals(agencyRole)) {
                enterpriseIds = agencyEnterpriseMapper.getEnterpriseIdsByAgencyId(agencyId);
            } else {
                AgencyUserEntity agencyUser = agencyUserMapper.selectOne(
                        new LambdaQueryWrapper<AgencyUserEntity>()
                                .eq(AgencyUserEntity::getUserId, user.getId())
                                .eq(AgencyUserEntity::getDeleted, 0));
                if (agencyUser != null) {
                    enterpriseIds = agencyUserEnterpriseMapper.getEnterpriseIdsByAgencyUserId(agencyUser.getId());
                } else {
                    enterpriseIds = List.of();
                }
            }
            for (Long eid : enterpriseIds) {
                EnterpriseEntity ent = enterpriseMapper.selectById(eid);
                if (ent != null) {
                    enterpriseList.add(new EnterpriseSimpleVO(
                            ent.getId(), ent.getEnterpriseName(),
                            ent.getTaxId(), ent.getStatus(), ent.getSeedDataDone()));
                }
            }
        }

        UserInfoResponse userInfo = new UserInfoResponse(user.getId(), user.getUsername(),
                user.getRealName(), user.getNickname(), user.getEmail(), user.getPhone(),
                user.getAvatar(), user.getDeptId(), roleIds, permissions,
                userType, agencyId, null, enterpriseId, false, agencyRole, enterpriseList);

        // 切片4：此处 enterpriseId 取自**当前生效上下文**（过滤器已按 JWT/请求头解析），
        // 与「归属企业」是两个概念，必须分开给出，否则前端无从判断该信哪个。
        userInfo.setHomeEnterpriseId(user.getEnterpriseId());
        userInfo.setCurrentEnterpriseId(enterpriseId);
        userInfo.setEnterpriseSelectionRequired(
                "AGENCY".equals(userType) && enterpriseId == null);

        return R.ok(userInfo);
    }

    /** SPEC-P124：refresh 请求体 */
    @Data
    public static class RefreshRequest {
        private String refreshToken;
    }

    /** SPEC-P124：续期响应 */
    @Data
    @AllArgsConstructor
    public static class TokenResponse {
        private String token;
        private String tokenType;
    }

    @Data
    public static class LoginRequest {
        private String username;
        private String password;
    }

    @Data
    public static class LoginResponse {
        private String token;
        private String refreshToken;
        private String tokenType;
        private String userType;

        /**
         * SPEC-P124 切片4：<b>归属企业</b> —— 他属于哪家（{@code t_user.enterprise_id}）。
     * 代理会计的该值按设计为 NULL：他跨客户服务，不「归属」任何一家。
     */
        private Long homeEnterpriseId;

        /**
         * SPEC-P124 切片4：<b>当前工作企业</b> —— 他正在为哪家干活。
     * 与 {@code homeEnterpriseId} 是<b>两个概念</b>；此前二者共用 {@code enterpriseId}
     * 一个名字，任何误用都会造成「把归属当当前」的覆盖事故。
     */
        private Long currentEnterpriseId;

        /**
         * SPEC-P124 切片4：是否需要先选择当前客户（仅代理端可能为 true）。
     * 让<b>原因由后端给出</b>，前端据此渲染引导态，而非靠 localStorage 推断。
     */
        private Boolean enterpriseSelectionRequired;
        private Long agencyId;
        private String agencyRole;
        private List<EnterpriseSimpleVO> enterpriseList;
        private UserInfoResponse userInfo;
    }

    @Data
    @AllArgsConstructor
    public static class UserInfoResponse {
        private Long id;
        private String username;
        private String realName;
        private String nickname;
        private String email;
        private String phone;
        private String avatar;
        private Long deptId;
        private List<Long> roles;
        private List<String> permissions;
        // S-26: 多租户字段
        private String userType;
        private Long agencyId;

        /**
         * SPEC-P124 切片4：<b>归属企业</b> —— 他属于哪家（{@code t_user.enterprise_id}）。
     * 代理会计的该值按设计为 NULL：他跨客户服务，不「归属」任何一家。
     */
        private Long homeEnterpriseId;

        /**
         * SPEC-P124 切片4：<b>当前工作企业</b> —— 他正在为哪家干活。
     * 与 {@code homeEnterpriseId} 是<b>两个概念</b>；此前二者共用 {@code enterpriseId}
     * 一个名字，任何误用都会造成「把归属当当前」的覆盖事故。
     */
        private Long currentEnterpriseId;

        /**
         * SPEC-P124 切片4：是否需要先选择当前客户（仅代理端可能为 true）。
     * 让<b>原因由后端给出</b>，前端据此渲染引导态，而非靠 localStorage 推断。
     */
        private Boolean enterpriseSelectionRequired;
        private String agencyRole;
        private List<EnterpriseSimpleVO> enterpriseList;
    }
}
