package com.huicai.base.system.mapper;

import com.huicai.base.system.entity.VoucherTypeEntity;
import com.huicai.base.system.mapper.VoucherTypeMapper;
import com.huicai.common.test.AbstractMapperTest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DuplicateKeyException;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * VoucherTypeMapper 真库补充（REQ-2026-131 / P104 第 3 批）
 *
 * <p>{@code t_voucher_type} 的唯一约束是 {@code uq_voucher_type_code_ent (code, enterprise_id)}
 * —— <b>复合唯一</b>，即同企业内 code 唯一，跨企业可重复。
 * mock 版只验证「参数被接受」，无法发现复合唯一键的真实边界。
 */
@DisplayName("P104 VoucherTypeMapper 真库补充")
class VoucherTypeMapperConstraintsRealDBTest extends AbstractMapperTest {

    @Autowired
    private VoucherTypeMapper voucherTypeMapper;

    private VoucherTypeEntity newType(String code, Long enterpriseId) {
        VoucherTypeEntity t = new VoucherTypeEntity();
        t.setCode(code);
        t.setName("P104凭证类型-" + code);
        t.setSortOrder(1);
        t.setIsActive(true);
        t.setEnterpriseId(enterpriseId);
        t.setDeleted(0);
        return t;
    }

    @Test
    @DisplayName("同企业内重复 code 必须被拒绝（复合唯一键的第一半）")
    void codeIsUniqueWithinEnterprise() {
        String code = "VT" + (System.nanoTime() % 1000000000L);
        voucherTypeMapper.insert(newType(code, DEFAULT_ENTERPRISE_ID));

        assertThrows(DuplicateKeyException.class,
                () -> voucherTypeMapper.insert(newType(code, DEFAULT_ENTERPRISE_ID)),
                "同企业内重复 code 未被拒绝");
    }

    @Test
    @DisplayName("跨企业同 code 必须允许（复合唯一键的第二半）")
    void codeMayRepeatAcrossEnterprises() {
        String code = "VT" + (System.nanoTime() % 1000000000L);
        voucherTypeMapper.insert(newType(code, DEFAULT_ENTERPRISE_ID));
        // 需显式绕开上下文：insertFill 会强制覆盖 enterpriseId
        VoucherTypeEntity other = newType(code, 987654L);
        withoutEnterpriseContext(() -> voucherTypeMapper.insert(other));

        assertNotNull(other.getId(), "跨企业同 code 被拒 ⇒ 唯一键实现与复合定义不符");
        Integer rows = jdbcTemplate.queryForObject(
                "SELECT count(*) FROM t_voucher_type WHERE code = ?", Integer.class, code);
        assertEquals(2, rows, "两个企业各应有一行");
    }

    @Test
    @DisplayName("🔴 deleteById 是软删除（凭证类型被凭证引用，不可物理删除）")
    void deleteByIdIsSoftDelete() {
        VoucherTypeEntity t = newType("DEL" + (System.nanoTime() % 1000000000L), DEFAULT_ENTERPRISE_ID);
        voucherTypeMapper.insert(t);
        Long id = t.getId();

        assertEquals(1, voucherTypeMapper.deleteById(id));
        Integer physical = jdbcTemplate.queryForObject(
                "SELECT count(*) FROM t_voucher_type WHERE id = ?", Integer.class, id);
        assertEquals(1, physical, "行被物理删除 —— 违反逻辑删除铁律 #12");
        assertNull(voucherTypeMapper.selectById(id));
    }

    @Test
    @DisplayName("numbering_rule 有默认值，落库后可用于凭证号生成")
    void numberingRuleHasDefault() {
        VoucherTypeEntity t = newType("NR" + (System.nanoTime() % 1000000000L), DEFAULT_ENTERPRISE_ID);
        voucherTypeMapper.insert(t);

        String rule = jdbcTemplate.queryForObject(
                "SELECT numbering_rule FROM t_voucher_type WHERE id = ?", String.class, t.getId());
        assertNotNull(rule, "numbering_rule 为空 ⇒ 凭证号生成无规则可用");
    }
}