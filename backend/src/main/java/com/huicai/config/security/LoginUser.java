package com.huicai.config.security;

import com.huicai.module.system.entity.UserEntity;
import lombok.Getter;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.User;

import java.util.List;

/**
 * 登录用户主体 - 扩展Spring Security User, 携带userId
 */
@Getter
public class LoginUser extends User {

    private final Long userId;

    public LoginUser(UserEntity userEntity, List<SimpleGrantedAuthority> authorities) {
        super(userEntity.getUsername(), userEntity.getPassword(), authorities);
        this.userId = userEntity.getId();
    }
}
