package com.huicai.module.system.controller;

import com.huicai.common.response.R;
import com.huicai.config.security.JwtProvider;
import com.huicai.module.system.entity.MenuEntity;
import com.huicai.module.system.entity.RoleEntity;
import com.huicai.module.system.entity.UserEntity;
import com.huicai.module.system.mapper.MenuMapper;
import com.huicai.module.system.mapper.RoleMenuMapper;
import com.huicai.module.system.mapper.UserMapper;
import com.huicai.module.system.mapper.UserRoleMapper;
import com.huicai.module.system.service.MenuService;
import jakarta.servlet.http.HttpServletRequest;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.bind.annotation.*;

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

    @PostMapping("/login")
    public R<LoginResponse> login(@RequestBody LoginRequest request) {
        Authentication authentication = authenticationManager.authenticate(
                new UsernamePasswordAuthenticationToken(request.getUsername(), request.getPassword()));

        SecurityContextHolder.getContext().setAuthentication(authentication);

        String username = request.getUsername();
        UserEntity user = userMapper.selectByUsername(username);

        // Get roles
        List<Long> roleIds = userRoleMapper.getRoleIdsByUserId(user.getId());

        // Get permissions
        List<String> permissions = menuService.getUserButtonPermissions(user.getId());

        String accessToken = jwtProvider.generateAccessToken(username, user.getId(),
                roleIds.stream().map(String::valueOf).collect(Collectors.toList()));
        String refreshToken = jwtProvider.generateRefreshToken(username);

        LoginResponse response = new LoginResponse();
        response.setToken(accessToken);
        response.setRefreshToken(refreshToken);
        response.setTokenType("Bearer");
        response.setUserInfo(new UserInfoResponse(user.getId(), user.getUsername(),
                user.getRealName(), user.getNickname(), user.getEmail(), user.getPhone(),
                user.getAvatar(), user.getDeptId(), roleIds, permissions));

        return R.ok(response);
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

        UserInfoResponse userInfo = new UserInfoResponse(user.getId(), user.getUsername(),
                user.getRealName(), user.getNickname(), user.getEmail(), user.getPhone(),
                user.getAvatar(), user.getDeptId(), roleIds, permissions);

        return R.ok(userInfo);
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
    }
}
