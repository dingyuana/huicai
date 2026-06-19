package com.huicai.module.finance.service.impl;

import com.huicai.module.finance.entity.ClassificationRuleEntity;
import com.huicai.module.finance.mapper.ClassificationRuleMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.when;

/**
 * 兜底启发式分类纯单元测试 — 9 条系统兜底规则全覆寫.
 * <p>
 * 模式: Mock ClassificationRuleMapper, 返回 V44 迁移中的 9 条兜底规则.
 */
@ExtendWith(MockitoExtension.class)
class FallbackHeuristicServiceTest {

    @Mock
    private ClassificationRuleMapper ruleMapper;

    @InjectMocks
    private FallbackHeuristicService service;

    private List<ClassificationRuleEntity> systemRules;

    @BeforeEach
    void setUp() {
        // V44 中的 9 条系统兜底规则, 与 V44__add_fallback_rules_to_db.sql 一致
        systemRules = List.of(
                rule("银行手续费", "keyword_regex", "手续费|工本费|年费|账户管理费", "out", "bank_fee", 90),
                rule("利息收入", "keyword_regex", "利息|结息|存款利息", "in", "interest_income", 91),
                rule("税务缴费", "keyword_regex", "税|税务|缴税|税金|增值税|所得税|城建税|教育费附加|国库|金库|印花|国家金库", "out", "tax_payment", 92),
                rule("社保缴费", "keyword_regex", "社保|公积金|养老|医疗|失业|工伤|生育", "out", "social_security", 93),
                rule("保险费用", "keyword_regex", "保险|保费|投保", "out", "insurance_fee", 94),
                rule("工资发放", "keyword_regex", "工资|薪资|薪酬|劳务费|奖金|津贴", "out", "salary_payment", 95),
                rule("业务收款", "keyword_regex", "货款|收款|销售|回款|客户|应收|收入", "in", "business_receipt", 96),
                rule("业务付款", "keyword_regex", "货款|付款|采购|支付|供应商|应付|支出", "out", "business_payment", 97),
                rule("内部转账", "keyword_regex", "转账|转存|调拨|上划|下拨|内部", null, "internal_transfer", 98)
        );
        lenient().when(ruleMapper.selectList(any())).thenReturn(systemRules);
    }

    private static ClassificationRuleEntity rule(String name, String ruleType, String pattern,
                                                  String direction, String classification, int priority) {
        ClassificationRuleEntity r = new ClassificationRuleEntity();
        r.setName(name);
        r.setRuleType(ruleType);
        r.setPattern(pattern);
        r.setDirection(direction);
        r.setClassification(classification);
        r.setPriority(priority);
        r.setIsSystem(true);
        r.setIsActive(true);
        r.setDeleted(0);
        return r;
    }

    // ==================== Level 90: bank_fee ====================

    @Test
    void bank_fee_账户管理费() {
        FallbackHeuristicService.Result r = service.classify("支付账户管理费", "out");
        assertNotNull(r);
        assertEquals("bank_fee", r.getClassification());
        assertEquals(90, r.getPriority());
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

    // ==================== Level 91: interest_income ====================

    @Test
    void interest_income_存款结息() {
        FallbackHeuristicService.Result r = service.classify("存款结息", "in");
        assertEquals("interest_income", r.getClassification());
        assertEquals(91, r.getPriority());
        assertEquals("结息", r.getMatchedKeyword());
    }

    @Test
    void interest_income_方向不匹配走方向兜底() {
        FallbackHeuristicService.Result r = service.classify("存款利息收入", "out");
        assertEquals("business_payment", r.getClassification());
        assertEquals(10, r.getPriority());
    }

    // ==================== Level 92: tax_payment ====================

    @Test
    void tax_payment_缴税() {
        FallbackHeuristicService.Result r = service.classify("缴税", "out");
        assertEquals("tax_payment", r.getClassification());
        assertEquals(92, r.getPriority());
        assertEquals("税", r.getMatchedKeyword());
    }

    @Test
    void tax_payment_增值税_按优先级命中税() {
        FallbackHeuristicService.Result r = service.classify("缴增值税", "out");
        assertEquals("tax_payment", r.getClassification());
        assertEquals(92, r.getPriority());
        assertEquals("税", r.getMatchedKeyword());
    }

    @Test
    void tax_payment_印花税() {
        FallbackHeuristicService.Result r = service.classify("印花税缴纳", "out");
        assertEquals("tax_payment", r.getClassification());
        assertEquals("税", r.getMatchedKeyword());
    }

    // ==================== Level 93: social_security ====================

    @Test
    void social_security_缴社保() {
        FallbackHeuristicService.Result r = service.classify("缴社保", "out");
        assertEquals("social_security", r.getClassification());
        assertEquals(93, r.getPriority());
        assertEquals("社保", r.getMatchedKeyword());
    }

    // ==================== Level 94: insurance_fee ====================

    @Test
    void insurance_fee_购买保险() {
        FallbackHeuristicService.Result r = service.classify("购买保险", "out");
        assertEquals("insurance_fee", r.getClassification());
        assertEquals(94, r.getPriority());
        assertEquals("保险", r.getMatchedKeyword());
    }

    @Test
    void insurance_fee_保费() {
        FallbackHeuristicService.Result r = service.classify("支付保费", "out");
        assertEquals("insurance_fee", r.getClassification());
        assertEquals("保费", r.getMatchedKeyword());
    }

    // ==================== Level 95: salary_payment ====================

    @Test
    void salary_payment_发放工资() {
        FallbackHeuristicService.Result r = service.classify("发放5月工资", "out");
        assertEquals("salary_payment", r.getClassification());
        assertEquals(95, r.getPriority());
        assertEquals("工资", r.getMatchedKeyword());
    }

    @Test
    void salary_payment_劳务费() {
        FallbackHeuristicService.Result r = service.classify("支付劳务费", "out");
        assertEquals("salary_payment", r.getClassification());
        assertEquals("劳务费", r.getMatchedKeyword());
    }

    // ==================== Level 96: business_receipt ====================

    @Test
    void business_receipt_客户回款() {
        FallbackHeuristicService.Result r = service.classify("客户回款", "in");
        assertEquals("business_receipt", r.getClassification());
        assertEquals(96, r.getPriority());
        assertEquals("回款", r.getMatchedKeyword());
    }

    @Test
    void business_receipt_收款() {
        FallbackHeuristicService.Result r = service.classify("销售货款收款", "in");
        assertEquals("business_receipt", r.getClassification());
        assertEquals("货款", r.getMatchedKeyword());
    }

    // ==================== Level 97: business_payment ====================

    @Test
    void business_payment_货款() {
        FallbackHeuristicService.Result r = service.classify("支付货款", "out");
        assertEquals("business_payment", r.getClassification());
        assertEquals(97, r.getPriority());
        assertEquals("货款", r.getMatchedKeyword());
    }

    @Test
    void business_payment_供应商付款() {
        FallbackHeuristicService.Result r = service.classify("供应商付款", "out");
        assertEquals("business_payment", r.getClassification());
        assertTrue("供应商".equals(r.getMatchedKeyword()) || "付款".equals(r.getMatchedKeyword()));
    }

    // ==================== Level 98: internal_transfer ====================

    @Test
    void internal_transfer_集团内调拨_in() {
        FallbackHeuristicService.Result r = service.classify("集团内调拨", "in");
        assertEquals("internal_transfer", r.getClassification());
        assertEquals(98, r.getPriority());
        assertEquals("调拨", r.getMatchedKeyword());
    }

    @Test
    void internal_transfer_转账_out() {
        FallbackHeuristicService.Result r = service.classify("跨行转账", "out");
        assertEquals("internal_transfer", r.getClassification());
        assertEquals("转账", r.getMatchedKeyword());
    }

    // ==================== 方向兜底 (priority=10) ====================

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

    // ==================== 优先级验证: 高优先级(数字小)优先 ====================

    @Test
    void 优先级_银行手续费优先于内部转账() {
        // "转账手续费" 同时包含 "手续费" (level 90) 和 "转账" (level 98)
        // 应命中优先级更高的 level 90
        FallbackHeuristicService.Result r = service.classify("转账手续费", "out");
        assertEquals("bank_fee", r.getClassification());
        assertEquals(90, r.getPriority());
    }

    @Test
    void 优先级_税务优先于付款() {
        // "缴纳税款" 包含 "税" (level 92) 和 "付款" (level 97)
        FallbackHeuristicService.Result r = service.classify("缴纳税款", "out");
        assertEquals("tax_payment", r.getClassification());
        assertEquals(92, r.getPriority());
    }
}
