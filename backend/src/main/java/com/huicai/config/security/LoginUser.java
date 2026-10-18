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
    private final String agencyRole;

    /**
     * 由 {@code UserDetailsServiceImpl} 调用的构造器。
     *
     * <p>🔴 <b>2026-10-10 修正</b>：原实现把 {@code agencyId} <b>硬编码为 null</b>
     * （{@code this(userEntity, authorities, userEntity.getEnterpriseId(), null, ...)}），
     * 而 {@code t_user.agency_id} 是<b>真实存在且有值</b>的列（实测 6 行全部非空），
     * {@code UserEntity} 也<b>有</b>对应字段 —— 即：这里本可以取到，却硬写 null。
     *
     * <p><b>后果</b>：本构造器是<b>生产环境唯一的调用点</b>，故
     * {@code SecurityUtils.getCurrentAgencyId()} <b>恒返回 null</b>。
     * 实测波及 <b>8 处</b>调用（代理看板 / 服务进度 / 企业切换 / 进项导入 /
     * 税务 / 制证 / 期末结账 / 本次新增的派工归属校验），
     * 它们拿到的 {@code agencyId} 全是 null ⇒ 按 agency 维度的隔离与过滤<b>静默失效</b>。
     *
     * <p><b>为什么此前无人发现</b>：JWT 里<b>确实带</b> {@code agencyId} claim
     * （{@code JwtProvider.generateAccessToken} 有写），且
     * {@code JwtAuthenticationFilter} 也<b>确实读了</b>（{@code getAgencyIdFromToken}），
     * 但读出来之后<b>从未用于重建 LoginUser</b> ⇒ 存在「看起来有、实际恒 null」的假接线。
     * 与 AGENTS §4.5 第 27 条「有注解不等于会生效」同型：
     * <b>数据取到了，但没有交给消费方</b>。
     *
     * <p><b>修法</b>：从 {@code UserEntity} 取真实值。
     * 非代理用户（{@code t_user.agency_id} 为 NULL）仍为 null，与原行为一致，
     * 故对 ENTERPRISE 类用户无回归。
     */
    public LoginUser(UserEntity userEntity, List<SimpleGrantedAuthority> authorities) {
        this(userEntity, authorities, userEntity.getEnterpriseId(), userEntity.getAgencyId(),
                userEntity.getUserType() != null ? userEntity.getUserType() : "ENTERPRISE",
                userEntity.getAgencyRole());
    }

    public LoginUser(UserEntity userEntity, List<SimpleGrantedAuthority> authorities,
                     Long enterpriseId, Long agencyId, String userType) {
        this(userEntity, authorities, enterpriseId, agencyId, userType, userEntity.getAgencyRole());
    }

    public LoginUser(UserEntity userEntity, List<SimpleGrantedAuthority> authorities,
                     Long enterpriseId, Long agencyId, String userType, String agencyRole) {
        super(userEntity.getUsername(), userEntity.getPassword(), authorities);
        this.userId = userEntity.getId();
        this.enterpriseId = enterpriseId;
        this.agencyId = agencyId;
        this.userType = userType;
        this.agencyRole = agencyRole;
    }
}
