package com.huicai.common.util;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * TemplateEngine 单元测试.
 */
class TemplateEngineTest {

    @Test
    void renderSummary_替换摘要变量() {
        TemplateContext ctx = new TemplateContext().setSummary("报销差旅费");
        assertEquals("报销差旅费", TemplateEngine.renderSummary("{{summary}}", ctx));
    }

    @Test
    void renderSummary_替换客户名称() {
        TemplateContext ctx = new TemplateContext().setCustomerName("北京华联");
        assertEquals("收北京华联货款", TemplateEngine.renderSummary("收{客户名称}货款", ctx));
    }

    @Test
    void renderSummary_替换供应商名称() {
        TemplateContext ctx = new TemplateContext().setVendorName("华为技术");
        assertEquals("付华为技术采购款", TemplateEngine.renderSummary("付{供应商名称}采购款", ctx));
    }

    @Test
    void renderSummary_替换员工姓名() {
        TemplateContext ctx = new TemplateContext().setEmployeeName("张三");
        assertEquals("报销张三差旅费", TemplateEngine.renderSummary("报销{员工姓名}差旅费", ctx));
    }

    @Test
    void renderSummary_替换月份() {
        TemplateContext ctx = new TemplateContext().setPeriod("202606");
        assertEquals("计提202606职工薪酬", TemplateEngine.renderSummary("计提{月份}职工薪酬", ctx));
    }

    @Test
    void renderSummary_替换年度() {
        TemplateContext ctx = new TemplateContext().setPeriod("202606");
        assertEquals("2026年度损益结转", TemplateEngine.renderSummary("{年度}年度损益结转", ctx));
    }

    @Test
    void renderSummary_无变量文本() {
        TemplateContext ctx = new TemplateContext();
        assertEquals("银行手续费", TemplateEngine.renderSummary("银行手续费", ctx));
    }

    @Test
    void renderSummary_空模板返回摘要() {
        TemplateContext ctx = new TemplateContext().setSummary("测试摘要");
        assertEquals("测试摘要", TemplateEngine.renderSummary("", ctx));
    }

    @Test
    void renderSummary_空模板空摘要() {
        assertEquals("", TemplateEngine.renderSummary(null, new TemplateContext()));
    }

    @Test
    void renderSummary_金额变量替换() {
        TemplateContext ctx = new TemplateContext().setAmount(new BigDecimal("1000.00")).setSummary("手续费");
        assertEquals("银行手续费: 1000.00", TemplateEngine.renderSummary("银行手续费: {{amount}}", ctx));
    }

    // ====== renderAmount ======

    @Test
    void renderAmount_取amount变量() {
        TemplateContext ctx = new TemplateContext().setAmount(new BigDecimal("1000.00"));
        assertEquals(0, new BigDecimal("1000.00").compareTo(TemplateEngine.renderAmount("{{amount}}", ctx)));
    }

    @Test
    void renderAmount_纯数字() {
        assertEquals(0, BigDecimal.ZERO.compareTo(TemplateEngine.renderAmount("", new TemplateContext())));
        assertEquals(0, new BigDecimal("500.00").compareTo(TemplateEngine.renderAmount("500", new TemplateContext())));
    }

    @Test
    void renderAmount_四则运算() {
        TemplateContext ctx = new TemplateContext()
                .setAmount(new BigDecimal("1000"))
                .setTaxAmount(new BigDecimal("130"));
        BigDecimal result = TemplateEngine.renderAmount("{{amount}}-{{taxAmount}}", ctx);
        assertEquals(0, new BigDecimal("870.00").compareTo(result));
    }
}