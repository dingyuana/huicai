package com.huicai.base.system.mapper;

import com.huicai.base.system.entity.SysConfigEntity;
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
 * SysConfigMapper 真库补充（REQ-2026-131 / P104 第 3 批）
 *
 * <p>{@code chk_config_type} 的允许集是<b>小写</b> {@code system/business/accounting}
 * —— 与 {@code chk_menu_type}（大写）、{@code chk_role_type}（大写）恰好相反。
 * 这类「同名不同大小写」的约束集若靠 mock 测试根本无法发现，
 * AGENTS §4.2 第 14 条已记录过由此导致的真实故障。
 */
@DisplayName("P104 SysConfigMapper 真库补充")
class SysConfigMapperConstraintsRealDBTest extends AbstractMapperTest {

    @Autowired
    private SysConfigMapper sysConfigMapper;

    private SysConfigEntity newConfig(String suffix) {
        SysConfigEntity c = new SysConfigEntity();
        c.setConfigKey("p104.key." + suffix + "." + System.nanoTime());
        c.setConfigValue("v");
        c.setConfigType("system");
        c.setIsActive(true);
        return c;
    }

    @Test
    @DisplayName("config_type 大写 SYSTEM 必须被 CHECK 拒绝（该约束用小写）")
    void uppercaseConfigTypeIsRejected() {
        SysConfigEntity c = newConfig("UPPER");
        c.setConfigType("SYSTEM");
        assertThrows(DataIntegrityViolationException.class,
                () -> sysConfigMapper.insert(c),
                "大写 SYSTEM 被接受 ⇒ chk_config_type 的允许集与预期不符");
    }

    @Test
    @DisplayName("config_type 小写 system 被接受（记录真实允许集）")
    void lowercaseConfigTypeIsAccepted() {
        SysConfigEntity c = newConfig("LOWER");
        assertEquals(1, sysConfigMapper.insert(c), "小写 system 应被接受");
        assertNotNull(c.getId());
    }

    @Test
    @DisplayName("uq_config_key 唯一约束真实生效")
    void configKeyIsUnique() {
        String key = "p104.key.dup." + System.nanoTime();
        SysConfigEntity first = newConfig("DUPA");
        first.setConfigKey(key);
        sysConfigMapper.insert(first);

        SysConfigEntity second = newConfig("DUPB");
        second.setConfigKey(key);
        assertThrows(DuplicateKeyException.class, () -> sysConfigMapper.insert(second),
                "重复 config_key 未被拒绝");
    }

    @Test
    @DisplayName("🔴 deleteById 是软删除")
    void deleteByIdIsSoftDelete() {
        SysConfigEntity c = newConfig("DEL");
        sysConfigMapper.insert(c);
        Long id = c.getId();

        assertEquals(1, sysConfigMapper.deleteById(id));
        Integer physical = jdbcTemplate.queryForObject(
                "SELECT count(*) FROM t_sys_config WHERE id = ?", Integer.class, id);
        assertEquals(1, physical, "行被物理删除 —— 违反逻辑删除铁律 #12");
        assertNull(sysConfigMapper.selectById(id));
    }
}