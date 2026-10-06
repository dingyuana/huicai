package com.huicai.sme.tax.mapper;

import com.huicai.common.test.AbstractMapperTest;
import com.huicai.sme.tax.entity.AccountMappingRuleEntity;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import static org.junit.jupiter.api.Assertions.*;

/**
 * P110 Phase 2：AccountMappingRuleEntity 真库回路。
 * 修复前的旧 Entity 只有 7 个 exist=false 野字段，全部映射到真实 DB 没有的列，
 * 导致没有人能通过它读写 t_account_mapping_rule 的数据；修复后 insert -> select 全字段回读。
 */
@DisplayName("P110 Phase 2 账目映射规则: insert round-trip")
class AccountMappingRulePhase2RealDBTest extends AbstractMapperTest {

    @Autowired
    private AccountMappingRuleMapper accountMappingRuleMapper;

    @Test
    @DisplayName("补齐真实业务字段后 INSERT 成功, selectById 全列回读一致")
    void insert_selectRoundTrip() {
        AccountMappingRuleEntity e = new AccountMappingRuleEntity();
        e.setRuleCode("RULE-SALE-001");
        e.setRuleName("销售应收默认科目");
        e.setSourceType("INVOICE_OUT");
        e.setMatchPattern("销售*");
        e.setIsActive(true);
        e.setEnterpriseId(DEFAULT_ENTERPRISE_ID);

        int n = accountMappingRuleMapper.insert(e);
        assertEquals(1, n, "insert 需通过（旧 Entity NOT NULL 列 rule_code/rule_name/source_type 都未映射）");

        AccountMappingRuleEntity back = accountMappingRuleMapper.selectById(e.getId());
        assertNotNull(back);
        assertEquals("RULE-SALE-001", back.getRuleCode());
        assertEquals("销售应收默认科目", back.getRuleName());
        assertEquals("INVOICE_OUT", back.getSourceType());
        assertTrue(Boolean.TRUE.equals(back.getIsActive()));

        // 清理：测试事务会回滚，但存在 SUPER用户 connection 直发却用超时？→ 直接 deleteById
        accountMappingRuleMapper.deleteById(e.getId());
    }
}
