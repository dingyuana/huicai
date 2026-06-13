package com.huicai.module.finance.util;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * 对手方名正则识别 — 10 个纯单元测试, 移植自 Go 版 extractCounterpartyName.
 */
class CounterpartyExtractorTest {

    // ==================== 4 级匹配 ====================

    @Test
    void testExtract_税务局() {
        assertEquals("国家税务总局山东税务局",
                CounterpartyExtractor.extract("向国家税务总局山东税务局缴税"));
    }

    @Test
    void testExtract_社保局() {
        assertEquals("济南市社保局",
                CounterpartyExtractor.extract("支付济南市社保局5月社保"));
    }

    @Test
    void testExtract_有限公司() {
        assertEquals("山东恺拓蔚兰医疗科技有限公司",
                CounterpartyExtractor.extract("收到山东恺拓蔚兰医疗科技有限公司货款"));
    }

    @Test
    void testExtract_股份公司() {
        assertEquals("中国建筑股份有限公司",
                CounterpartyExtractor.extract("中国建筑股份有限公司付款"));
    }

    @Test
    void testExtract_短公司() {
        assertEquals("万达公司",
                CounterpartyExtractor.extract("向万达公司付款"));
    }

    @Test
    void testExtract_银行() {
        assertEquals("工商银行",
                CounterpartyExtractor.extract("工商银行手续费"));
    }

    // ==================== 边界 & 防御 ====================

    @Test
    void testRejects_BankCode() {
        // 防"10086"被当对手方
        assertEquals("", CounterpartyExtractor.extract("10086"));
    }

    @Test
    void testEmpty_NullInput() {
        assertEquals("", CounterpartyExtractor.extract(null));
    }

    // ==================== 优先级 (防贪婪匹配错) ====================

    @Test
    void testPriority_税务局_优先于公司() {
        // 字符串里同时含"税务局"和"公司", 税务局须先命中
        assertEquals("国家税务总局北京市税务局",
                CounterpartyExtractor.extract("国家税务总局北京市税务局"));
    }

    @Test
    void testPriority_长匹配优先() {
        // 含"集团"和"集团公司"两层, 贪婪匹配取最长的"集团有限公司"
        assertEquals("中国石油化工集团有限公司",
                CounterpartyExtractor.extract("中国石油化工集团有限公司"));
    }
}
