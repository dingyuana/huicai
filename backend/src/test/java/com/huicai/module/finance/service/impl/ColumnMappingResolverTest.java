package com.huicai.module.finance.service.impl;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 列名智能映射纯单元测试 — 8 个场景全覆盖.
 * <p>
 * 模式: 不启动 Spring, 直接 new Resolver 测试纯业务逻辑.
 */
class ColumnMappingResolverTest {

    private final ColumnMappingResolver resolver = new ColumnMappingResolver();

    // ==================== 标准中文表头 ====================

    @Test
    void 标准中文表头全识别() {
        String[] headers = {"交易日期", "金额", "摘要", "对方账户"};
        ColumnMappingResolver.MappingResult r = resolver.resolve(headers);

        assertTrue(r.isValid());
        assertEquals(4, r.getFieldToColumnIndex().size());
        assertEquals(0, r.getFieldToColumnIndex().get(ColumnMappingResolver.Field.TX_DATE).intValue());
        assertEquals(1, r.getFieldToColumnIndex().get(ColumnMappingResolver.Field.AMOUNT).intValue());
        assertEquals(2, r.getFieldToColumnIndex().get(ColumnMappingResolver.Field.SUMMARY).intValue());
        assertEquals(3, r.getFieldToColumnIndex().get(ColumnMappingResolver.Field.COUNTER_ACCOUNT).intValue());
    }

    // ==================== 英文表头 ====================

    @Test
    void 英文表头全识别() {
        String[] headers = {"date", "amount", "summary", "counterparty"};
        ColumnMappingResolver.MappingResult r = resolver.resolve(headers);

        assertTrue(r.isValid());
        assertEquals(4, r.getFieldToColumnIndex().size());
        assertEquals(0, r.getFieldToColumnIndex().get(ColumnMappingResolver.Field.TX_DATE).intValue());
        assertEquals(1, r.getFieldToColumnIndex().get(ColumnMappingResolver.Field.AMOUNT).intValue());
        assertEquals(2, r.getFieldToColumnIndex().get(ColumnMappingResolver.Field.SUMMARY).intValue());
        assertEquals(3, r.getFieldToColumnIndex().get(ColumnMappingResolver.Field.COUNTER_ACCOUNT).intValue());
    }

    // ==================== 混搭表头 ====================

    @Test
    void 混搭表头全识别() {
        String[] headers = {"记账日期", "发生额", "附言"};
        ColumnMappingResolver.MappingResult r = resolver.resolve(headers);

        assertTrue(r.isValid());
        assertEquals(0, r.getFieldToColumnIndex().get(ColumnMappingResolver.Field.TX_DATE).intValue());
        assertEquals(1, r.getFieldToColumnIndex().get(ColumnMappingResolver.Field.AMOUNT).intValue());
        assertEquals(2, r.getFieldToColumnIndex().get(ColumnMappingResolver.Field.SUMMARY).intValue());
    }

    // ==================== 大小写不敏感 ====================

    @Test
    void 大小写不敏感() {
        String[] headers = {"DATE", "AMOUNT"};
        ColumnMappingResolver.MappingResult r = resolver.resolve(headers);

        assertTrue(r.isValid());
        assertEquals(0, r.getFieldToColumnIndex().get(ColumnMappingResolver.Field.TX_DATE).intValue());
        assertEquals(1, r.getFieldToColumnIndex().get(ColumnMappingResolver.Field.AMOUNT).intValue());
    }

    // ==================== 必含列缺失 ====================

    @Test
    void 必含列缺失_amount不存在() {
        String[] headers = {"交易日期", "摘要"};
        ColumnMappingResolver.MappingResult r = resolver.resolve(headers);

        assertFalse(r.isValid());
        assertNotNull(r.getFieldToColumnIndex().get(ColumnMappingResolver.Field.TX_DATE));
        assertNull(r.getFieldToColumnIndex().get(ColumnMappingResolver.Field.AMOUNT));
    }

    @Test
    void 必含列缺失_date不存在() {
        String[] headers = {"金额", "对方账户"};
        ColumnMappingResolver.MappingResult r = resolver.resolve(headers);

        assertFalse(r.isValid());
    }

    // ==================== 完全空表头 ====================

    @Test
    void 完全空表头() {
        String[] headers = {};
        ColumnMappingResolver.MappingResult r = resolver.resolve(headers);

        assertFalse(r.isValid());
        assertTrue(r.getFieldToColumnIndex().isEmpty());
    }

    @Test
    void null表头() {
        ColumnMappingResolver.MappingResult r = resolver.resolve(null);

        assertFalse(r.isValid());
        assertTrue(r.getFieldToColumnIndex().isEmpty());
    }

    // ==================== 列顺序无关 ====================

    @Test
    void 列顺序无关() {
        String[] headers = {"摘要", "金额", "交易日期"};
        ColumnMappingResolver.MappingResult r = resolver.resolve(headers);

        assertTrue(r.isValid());
        assertEquals(2, r.getFieldToColumnIndex().get(ColumnMappingResolver.Field.TX_DATE).intValue());
        assertEquals(1, r.getFieldToColumnIndex().get(ColumnMappingResolver.Field.AMOUNT).intValue());
        assertEquals(0, r.getFieldToColumnIndex().get(ColumnMappingResolver.Field.SUMMARY).intValue());
    }

    // ==================== 未知列不报错 ====================

    @Test
    void 未知列不报错() {
        String[] headers = {"交易日期", "金额", "无关列"};
        ColumnMappingResolver.MappingResult r = resolver.resolve(headers);

        assertTrue(r.isValid());
        assertEquals(0, r.getFieldToColumnIndex().get(ColumnMappingResolver.Field.TX_DATE).intValue());
        assertEquals(1, r.getFieldToColumnIndex().get(ColumnMappingResolver.Field.AMOUNT).intValue());
        // 其它字段应为 null
        assertNull(r.getFieldToColumnIndex().get(ColumnMappingResolver.Field.SUMMARY));
        assertNull(r.getFieldToColumnIndex().get(ColumnMappingResolver.Field.TX_TYPE));
        assertNull(r.getFieldToColumnIndex().get(ColumnMappingResolver.Field.COUNTER_ACCOUNT));
        assertNull(r.getFieldToColumnIndex().get(ColumnMappingResolver.Field.EXTERNAL_NO));
    }

    // ==================== 精确匹配优先于 contains ====================

    @Test
    void 精确匹配优先于contains() {
        // "date" 精确匹配 TX_DATE 的别名 "date" (contains 也能匹配, 但精确先命中)
        String[] headers = {"date", "value", "memo"};
        ColumnMappingResolver.MappingResult r = resolver.resolve(headers);

        assertTrue(r.isValid());
        assertEquals(0, r.getFieldToColumnIndex().get(ColumnMappingResolver.Field.TX_DATE).intValue());
        assertEquals(1, r.getFieldToColumnIndex().get(ColumnMappingResolver.Field.AMOUNT).intValue());
        assertEquals(2, r.getFieldToColumnIndex().get(ColumnMappingResolver.Field.SUMMARY).intValue());
    }

    // ==================== 全字段表头 ====================

    @Test
    void 全字段表头() {
        String[] headers = {"交易日期", "交易类型", "金额", "对方账户", "摘要", "交易流水号"};
        ColumnMappingResolver.MappingResult r = resolver.resolve(headers);

        assertTrue(r.isValid());
        assertEquals(6, r.getFieldToColumnIndex().size());
        assertEquals(0, r.getFieldToColumnIndex().get(ColumnMappingResolver.Field.TX_DATE).intValue());
        assertEquals(1, r.getFieldToColumnIndex().get(ColumnMappingResolver.Field.TX_TYPE).intValue());
        assertEquals(2, r.getFieldToColumnIndex().get(ColumnMappingResolver.Field.AMOUNT).intValue());
        assertEquals(3, r.getFieldToColumnIndex().get(ColumnMappingResolver.Field.COUNTER_ACCOUNT).intValue());
        assertEquals(4, r.getFieldToColumnIndex().get(ColumnMappingResolver.Field.SUMMARY).intValue());
        assertEquals(5, r.getFieldToColumnIndex().get(ColumnMappingResolver.Field.EXTERNAL_NO).intValue());
    }
}