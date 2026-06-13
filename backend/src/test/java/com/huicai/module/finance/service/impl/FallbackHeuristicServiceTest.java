package com.huicai.module.finance.service.impl;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 兜底启发式分类纯单元测试 — 10 级关键词全覆盖.
 * <p>
 * 模式: 不启动 Spring, 直接 new Service 测试纯业务逻辑.
 */
@ExtendWith(MockitoExtension.class)
class FallbackHeuristicServiceTest {

    private final FallbackHeuristicService service = new FallbackHeuristicService();

    // ==================== Level 1: bank_fee ====================

    @Test
    void bank_fee_账户管理费() {
        FallbackHeuristicService.Result r = service.classify("支付账户管理费", "out");
        assertNotNull(r);
        assertEquals("bank_fee", r.getClassification());
        assertEquals(1, r.getPriority());
        assertEquals("账户管理费", r.getMatchedKeyword());
    }

    @Test
    void bank_fee_手续费() {
        FallbackHeuristicService.Result r = service.classify("转账手续费", "out");
        assertEquals("bank_fee", r.getClassification());
        assertEquals("手续费", r.getMatchedKeyword());
    }

    @Test
    void bank_fee_方向不匹配走方向兜底() {
        FallbackHeuristicService.Result r = service.classify("账户管理费", "in");
        assertEquals("business_receipt", r.getClassification());
        assertEquals(10, r.getPriority());
    }

    // ==================== Level 2: interest_income ====================

    @Test
    void interest_income_存款结息() {
        FallbackHeuristicService.Result r = service.classify("存款结息", "in");
        assertEquals("interest_income", r.getClassification());
        assertEquals(2, r.getPriority());
        assertEquals("结息", r.getMatchedKeyword());
    }

    @Test
    void interest_income_方向不匹配走方向兜底() {
        FallbackHeuristicService.Result r = service.classify("存款利息收入", "out");
        assertEquals("business_payment", r.getClassification());
        assertEquals(10, r.getPriority());
    }

    // ==================== Level 3: tax_payment ====================

    @Test
    void tax_payment_缴税() {
        FallbackHeuristicService.Result r = service.classify("缴税", "out");
        assertEquals("tax_payment", r.getClassification());
        assertEquals(3, r.getPriority());
        assertEquals("税", r.getMatchedKeyword());
    }

    @Test
    void tax_payment_增值税_按优先级命中税() {
        // "缴增值税" 包含 "税" (level 3) 和 "增值税" (level 3)
        // 按关键词数组顺序, "税" 先命中
        FallbackHeuristicService.Result r = service.classify("缴增值税", "out");
        assertEquals("tax_payment", r.getClassification());
        assertEquals(3, r.getPriority());
        assertEquals("税", r.getMatchedKeyword());
    }

    @Test
    void tax_payment_印花税() {
        // "印花税" 同时含 "税" 和 "印花", 按关键词数组顺序 "税" 先命中
        FallbackHeuristicService.Result r = service.classify("印花税缴纳", "out");
        assertEquals("tax_payment", r.getClassification());
        assertEquals("税", r.getMatchedKeyword());
    }

    // ==================== Level 4: social_security ====================

    @Test
    void social_security_缴社保() {
        FallbackHeuristicService.Result r = service.classify("缴社保", "out");
        assertEquals("social_security", r.getClassification());
        assertEquals(4, r.getPriority());
        assertEquals("社保", r.getMatchedKeyword());
    }

    // ==================== Level 5: insurance_fee ====================

    @Test
    void insurance_fee_购买保险() {
        FallbackHeuristicService.Result r = service.classify("购买保险", "out");
        assertEquals("insurance_fee", r.getClassification());
        assertEquals(5, r.getPriority());
        assertEquals("保险", r.getMatchedKeyword());
    }

    @Test
    void insurance_fee_保费() {
        FallbackHeuristicService.Result r = service.classify("支付保费", "out");
        assertEquals("insurance_fee", r.getClassification());
        assertEquals("保费", r.getMatchedKeyword());
    }

    // ==================== Level 6: salary_payment ====================

    @Test
    void salary_payment_发放工资() {
        FallbackHeuristicService.Result r = service.classify("发放5月工资", "out");
        assertEquals("salary_payment", r.getClassification());
        assertEquals(6, r.getPriority());
        assertEquals("工资", r.getMatchedKeyword());
    }

    @Test
    void salary_payment_劳务费() {
        FallbackHeuristicService.Result r = service.classify("支付劳务费", "out");
        assertEquals("salary_payment", r.getClassification());
        assertEquals("劳务费", r.getMatchedKeyword());
    }

    // ==================== Level 7: business_receipt ====================

    @Test
    void business_receipt_客户回款() {
        FallbackHeuristicService.Result r = service.classify("客户回款", "in");
        assertEquals("business_receipt", r.getClassification());
        assertEquals(7, r.getPriority());
        assertEquals("回款", r.getMatchedKeyword());
    }

    @Test
    void business_receipt_收款() {
        FallbackHeuristicService.Result r = service.classify("销售货款收款", "in");
        assertEquals("business_receipt", r.getClassification());
        // "货款" 在 level 7 中第一个匹配
        assertEquals("货款", r.getMatchedKeyword());
    }

    // ==================== Level 8: business_payment ====================

    @Test
    void business_payment_货款() {
        FallbackHeuristicService.Result r = service.classify("支付货款", "out");
        assertEquals("business_payment", r.getClassification());
        assertEquals(8, r.getPriority());
        assertEquals("货款", r.getMatchedKeyword());
    }

    @Test
    void business_payment_供应商付款() {
        FallbackHeuristicService.Result r = service.classify("供应商付款", "out");
        assertEquals("business_payment", r.getClassification());
        assertTrue("供应商".equals(r.getMatchedKeyword()) || "付款".equals(r.getMatchedKeyword()));
    }

    // ==================== Level 9: internal_transfer ====================

    @Test
    void internal_transfer_集团内调拨_in() {
        FallbackHeuristicService.Result r = service.classify("集团内调拨", "in");
        assertEquals("internal_transfer", r.getClassification());
        assertEquals(9, r.getPriority());
        assertEquals("调拨", r.getMatchedKeyword());
    }

    @Test
    void internal_transfer_转账_out() {
        // internal_transfer 不限方向
        FallbackHeuristicService.Result r = service.classify("跨行转账", "out");
        assertEquals("internal_transfer", r.getClassification());
        assertEquals("转账", r.getMatchedKeyword());
    }

    // ==================== Level 10: 方向兜底 ====================

    @Test
    void 方向兜底_in_返回业务收款() {
        FallbackHeuristicService.Result r = service.classify("XXXXX", "in");
        assertEquals("business_receipt", r.getClassification());
        assertEquals(10, r.getPriority());
    }

    @Test
    void 方向兜底_out_返回业务付款() {
        FallbackHeuristicService.Result r = service.classify("XXXXX", "out");
        assertEquals("business_payment", r.getClassification());
        assertEquals(10, r.getPriority());
    }

    @Test
    void 方向兜底_方向过滤后落入() {
        // "工资" 属于 salary_payment(out), 但入参方向为 in → 不命中前 9 级 → 走方向兜底
        FallbackHeuristicService.Result r = service.classify("发放5月工资", "in");
        assertEquals("business_receipt", r.getClassification());
        assertEquals(10, r.getPriority());
    }

    @Test
    void 方向兜底_description为空() {
        FallbackHeuristicService.Result r = service.classify(null, "in");
        assertEquals("business_receipt", r.getClassification());
        assertEquals(10, r.getPriority());

        r = service.classify("", "out");
        assertEquals("business_payment", r.getClassification());
    }

    @Test
    void pending_方向也为空() {
        // 方向兜底都需要方向, 没方向就只能 pending
        FallbackHeuristicService.Result r = service.classify("XXXXX", null);
        assertEquals("pending", r.getClassification());
        assertEquals(10, r.getPriority());
    }

    // ==================== 优先级验证: 高优先级优先 ====================

    @Test
    void 优先级_银行手续费优先于内部转账() {
        // "转账手续费" 同时包含 "手续费" (level 1) 和 "转账" (level 9)
        // 应命中优先级更高的 level 1
        FallbackHeuristicService.Result r = service.classify("转账手续费", "out");
        assertEquals("bank_fee", r.getClassification());
        assertEquals(1, r.getPriority());
    }

    @Test
    void 优先级_税务优先于付款() {
        // "缴纳税款" 同时包含 "税" (level 3) 和 "付款" (level 8) 的拼音部分
        // 实际上只有 "税" 命中 level 3
        FallbackHeuristicService.Result r = service.classify("缴纳税款", "out");
        assertEquals("tax_payment", r.getClassification());
        assertEquals(3, r.getPriority());
    }
}
