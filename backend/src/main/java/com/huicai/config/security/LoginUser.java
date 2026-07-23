package com.huicai.config.security;

import com.huicai.base.system.entity.UserEntity;
import lombok.Getter;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.User;

import java.util.List;

/**
 * 登录用户主体 - 扩展Spring Security User, 携带userId + 多租户字段
 */
@Getter
public class LoginUser extends User {

    private final Long userId;
    private final Long enterpriseId;
    private final Long agencyId;
    private final String userType;

    public LoginUser(UserEntity userEntity, List<SimpleGrantedAuthority> authorities) {
        this(userEntity, authorities, userEntity.getEnterpriseId(), null,
                userEntity.getUserType() != null ? userEntity.getUserType() : "ENTERPRISE");
    }

    public LoginUser(UserEntity userEntity, List<SimpleGrantedAuthority> authorities,
                     Long enterpriseId, Long agencyId, String userType) {
        super(userEntity.getUsername(), userEntity.getPassword(), authorities);
        this.userId = userEntity.getId();
        this.enterpriseId = enterpriseId;
        this.agencyId = agencyId;
        this.userType = userType;
    }
}
