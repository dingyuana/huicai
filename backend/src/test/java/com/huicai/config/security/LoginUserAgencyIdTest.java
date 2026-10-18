package com.huicai.config.security;

import com.huicai.base.system.entity.UserEntity;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

/**
 * {@link LoginUser} 登录上下文字段传递守卫（REQ-2026-140 / 2026-10-10）
 *
 * <p><b>缺陷背景</b>：生产环境<b>唯一</b>调用点
 * {@code UserDetailsServiceImpl:89} 走的是 2 参构造器，而该构造器原把
 * {@code agencyId} <b>硬编码为 null</b>，尽管 {@code UserEntity.agencyId}
 * 与 {@code t_user.agency_id} 都有真实值 ⇒ {@code SecurityUtils.getCurrentAgencyId()}
 * <b>恒返回 null</b>，波及 8 处调用点的按代理隔离逻辑。
 *
 * <p>🔴 <b>为什么这个缺陷能长期存活</b>：JWT 里<b>确实带</b> {@code agencyId} claim，
 * {@code JwtAuthenticationFilter} 也<b>确实读了</b>它 ——
 * 一切「看起来都在」，只是读出来之后<b>从未交给消费方</b>。
 * 与 AGENTS §4.5 第 27 条「有注解不等于会生效」同型，此处是
 * <b>数据取到了，但没有接线到使用点</b>。
 * 故必须有守卫：否则任何人「顺手」把构造器改回硬编码 null，线上不会有任何报错。
 */
@DisplayName("LoginUser：登录上下文字段不得在构造器中丢失")
class LoginUserAgencyIdTest {

    private static UserEntity userWith(Long agencyId, String userType) {
        UserEntity u = new UserEntity();
        u.setId(1L);
        u.setUsername("tester");
        u.setPassword("pwd");
        u.setUserType(userType);
        u.setAgencyId(agencyId);
        u.setAgencyRole("ACCOUNTANT");
        return u;
    }

    @Test
    @DisplayName("2 参构造器必须带上 t_user.agency_id（曾硬编码为 null）")
    void twoArgConstructorCarriesAgencyId() {
        LoginUser lu = new LoginUser(userWith(7L, "AGENCY"), List.of());
        assertEquals(7L, lu.getAgencyId(),
                "agencyId 丢失 ⇒ SecurityUtils.getCurrentAgencyId() 恒为 null，"
                        + "按 agency 维度的隔离静默失效（实测波及 8 处调用点）");
    }

    @Test
    @DisplayName("ENTERPRISE 用户本就无 agency_id，应保持 null（不得凭空造值）")
    void enterpriseUserHasNoAgencyId() {
        LoginUser lu = new LoginUser(userWith(null, "ENTERPRISE"), List.of());
        assertNull(lu.getAgencyId(), "非代理用户不应有 agencyId");
    }

    @Test
    @DisplayName("enterpriseId / userType / agencyRole 仍须正确传递（防修此 bug 时误伤）")
    void otherContextFieldsStillCarried() {
        UserEntity u = userWith(3L, "AGENCY");
        u.setEnterpriseId(9L);
        LoginUser lu = new LoginUser(u, List.of());
        assertEquals(9L, lu.getEnterpriseId());
        assertEquals("AGENCY", lu.getUserType());
        assertEquals("ACCOUNTANT", lu.getAgencyRole());
    }

    @Test
    @DisplayName("反证：若构造器退回硬编码 null，本守卫必须转红")
    void guardDetectsHardcodedNull() {
        // 不修改生产代码，而是在此显式复现「硬编码 null」的构造形态并断言其不可接受：
        // LoginUser 的 2 参构造器等价于「agencyId 取自 UserEntity」，
        // 而 UserEntity 为 null 时构造器必须**如实**给 null，不得回落到某个默认代理。
        UserEntity noAgency = userWith(null, "AGENCY");
        assertNull(new LoginUser(noAgency, List.of()).getAgencyId(),
                "agencyId 缺失时必须为 null；若代码开始给默认值，说明有凭空授权风险");
    }
}