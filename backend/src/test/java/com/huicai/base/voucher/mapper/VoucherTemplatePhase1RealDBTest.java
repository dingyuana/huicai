package com.huicai.base.voucher.mapper;

import com.huicai.base.voucher.entity.VoucherTemplateEntity;
import com.huicai.common.test.AbstractMapperTest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import static org.junit.jupiter.api.Assertions.*;

/**
 * P110 Phase 1 真库测试：凭证模板能够插入成功，且 round-trip 不丢列。
 *
 * <p>修复前的实力缺陷：{@code VoucherTemplateEntity} 没声明 {@code template_code} 与
 * {@code entries}（DB 两列 NOT NULL），MP 生成的 INSERT 永不带这两列 ⇒ 插入必挂。
 * P109 的实测（REQ-2026-138）也坐实了同类的「表设计与 Entity 完全脱节」。
 */
@DisplayName("P110 Phase 1 凭证模板写入回路")
class VoucherTemplatePhase1RealDBTest extends AbstractMapperTest {

    @Autowired
    private VoucherTemplateMapper voucherTemplateMapper;

    private VoucherTemplateEntity requiredFields() {
        VoucherTemplateEntity e = new VoucherTemplateEntity();
        e.setTemplateCode("TPL_P110_PH1");
        e.setName("P110 模板");
        e.setBusinessType("PAYMENT");
        e.setVoucherTypeCode("FK");
        e.setSummary("P110 模板摘要");
        e.setEntries("[{\"dr\":\"6602\",\"cr\":\"1002\",\"amountExpr\":\"amount\"}]");
        e.setEnterpriseId(DEFAULT_ENTERPRISE_ID);
        e.setIsActive(true);
        return e;
    }

    @Test
    @DisplayName("补齐 template_code/entries 后 INSERT 成功，且回读三列不丢失")
    void insert_fullColumns_roundTrips() {
        VoucherTemplateEntity t = requiredFields();
        int rows = voucherTemplateMapper.insert(t);
        assertEquals(1, rows, "insert 未生效（正是修复前的必挂点）");

        VoucherTemplateEntity back = voucherTemplateMapper.selectById(t.getId());
        assertNotNull(back, "回读不到");
        assertEquals("TPL_P110_PH1", back.getTemplateCode(), "template_code 回读不一致");
        assertEquals("FK", back.getVoucherTypeCode(), "voucher_type_code 回读不一致");
        assertTrue(back.getEntries() != null && back.getEntries().contains("dr"),
                "entries(jsonb) 应能经 JsonbTypeHandler 写回再读回，实际 = " + back.getEntries());
        assertEquals("PAYMENT", back.getBusinessType(), "doc_type 回读不一致");

        // 清理：测试事务会回滚，但原生 psql 的结果不在事务内——收尾保证实库无残留
        jdbcTemplate.update("DELETE FROM t_voucher_template WHERE template_code = ?", "TPL_P110_PH1");
    }

    @Test
    @DisplayName("负向：遗漏逗号 entries 或 template_code ⇒ 被 DB NOT NULL 拦截")
    void insert_missingKerColumns_failsFast() {
        VoucherTemplateEntity e = new VoucherTemplateEntity(); // 全字段默认缺失
        e.setName("缺字段");
        e.setBusinessType("PAYMENT");
        e.setIsActive(true);
        assertThrows(Exception.class, () -> voucherTemplateMapper.insert(e),
                "缺失 template_code/entries 必须被 DB 拒绝而不是允许插入");
    }
}
