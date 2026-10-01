package com.huicai.base.system.mapper;

import com.huicai.base.system.entity.UserEntity;
import com.huicai.common.test.AbstractMapperTest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.DuplicateKeyException;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * UserMapper 真库补充（REQ-2026-131 / P104 第 3 批）
 *
 * <p>{@code t_user} 有三个 CHECK，是本项目约束集最容易踩坑的表
 * （AGENTS §4.2 第 14 条：大小写敏感且与 {@code chk_menu_type} 大写、
 * {@code chk_config_type} 小写恰好相反）：
 * <ul>
 *   <li>{@code chk_user_status}：ACTIVE / INACTIVE / LOCKED（<b>大写</b>，无 enabled）</li>
 *   <li>{@code chk_user_type}：SUPER_ADMIN / AGENCY / ENTERPRISE（<b>大写</b>，无 employee）</li>
 *   <li>{@code uq_username}：username 全局唯一</li>
 * </ul>
 * mock 版无法覆盖任何一条 —— 它连 CHECK 约束都不会触发。
 */
@DisplayName("P104 UserMapper 真库补充")
class UserMapperConstraintsRealDBTest extends AbstractMapperTest {

    @Autowired
    private UserMapper userMapper;

    private UserEntity newUser(String suffix) {
        UserEntity u = new UserEntity();
        u.setUsername("p104_" + suffix.toLowerCase() + "_" + System.nanoTime());
        u.setPassword("P104pwd");
        u.setRealName("P104用户-" + suffix);
        u.setStatus("ACTIVE");
        u.setUserType("ENTERPRISE");
        u.setDeleted(0);
        return u;
    }

    @Test
    @DisplayName("chk_user_status 拒绝 enabled（易错值：DB 只允许 ACTIVE/INACTIVE/LOCKED）")
    void enabledIsNotAValidStatus() {
        UserEntity u = newUser("STATUS");
        u.setStatus("enabled");
        assertThrows(DataIntegrityViolationException.class, () -> userMapper.insert(u),
                "status=enabled 被接受 ⇒ chk_user_status 允许集与预期不符");
    }

    @Test
    @DisplayName("chk_user_type 拒绝 employee（易错值：DB 只允许 SUPER_ADMIN/AGENCY/ENTERPRISE）")
    void employeeIsNotAValidUserType() {
        UserEntity u = newUser("TYPE");
        u.setUserType("employee");
        assertThrows(DataIntegrityViolationException.class, () -> userMapper.insert(u),
                "user_type=employee 被接受 ⇒ chk_user_type 允许集与预期不符");
    }

    @Test
    @DisplayName("记录真实允许集：ACTIVE + ENTERPRISE 组合可正常创建")
    void documentedStatusAndTypeAreAccepted() {
        UserEntity u = newUser("OK");
        assertEquals(1, userMapper.insert(u));
        assertNotNull(u.getId());
    }

    @Test
    @DisplayName("uq_username 唯一约束真实生效")
    void usernameIsUnique() {
        UserEntity first = newUser("DUPA");
        first.setUsername("p104_dup_" + System.nanoTime());
        userMapper.insert(first);

        UserEntity second = newUser("DUPB");
        second.setUsername(first.getUsername());
        assertThrows(DuplicateKeyException.class, () -> userMapper.insert(second),
                "重复 username 未被拒绝");
    }

    @Test
    @DisplayName("🔴 deleteById 是软删除（用户是审计主体，不可物理删除）")
    void deleteByIdIsSoftDelete() {
        UserEntity u = newUser("DEL");
        userMapper.insert(u);
        Long id = u.getId();

        assertEquals(1, userMapper.deleteById(id));
        Integer physical = jdbcTemplate.queryForObject(
                "SELECT count(*) FROM t_user WHERE id = ?", Integer.class, id);
        assertEquals(1, physical, "用户被物理删除 —— 违反逻辑删除铁律 #12");
        assertNull(userMapper.selectById(id));
    }

    @Test
    @DisplayName("反证：login 时读到的密码确实落库（mock 版无法验证字段映射）")
    void passwordIsPersistedForLogin() {
        UserEntity u = newUser("PWD");
        String pwd = "P104-secret-" + System.nanoTime();
        u.setPassword(pwd);
        userMapper.insert(u);

        String stored = jdbcTemplate.queryForObject(
                "SELECT password FROM t_user WHERE id = ?", String.class, u.getId());
        assertEquals(pwd, stored, "密码未原样落库 ⇒ 登录校验会失败");
    }
}