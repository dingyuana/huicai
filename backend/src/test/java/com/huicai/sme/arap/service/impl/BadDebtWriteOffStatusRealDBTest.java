package com.huicai.sme.arap.service.impl;

import com.huicai.base.business.entity.BusinessDocEntity;
import com.huicai.base.business.mapper.BusinessDocMapper;
import com.huicai.common.test.AbstractMapperTest;
import com.huicai.sme.arap.service.BadDebtService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

/**
 * 坏账核销写单据状态真库测试（REQ-2026-136 / P105 契约工作带出的 P0）
 *
 * <p><b>缺陷背景</b>：{@code BadDebtServiceImpl.writeOff()} 在「未清金额恰好归零」时
 * 写 {@code t_business_doc.status = "SETTLED"}（取自 {@code ArapStatus.SETTLED}），
 * 但 {@code chk_doc_status} 的允许集里<b>没有 {@code SETTLED}</b> ⇒ 紧随其后的
 * {@code businessDocMapper.updateById(doc)} 必然抛 23514，接口 500。
 * 也就是说<b>「把剩余应收一次性全额核销」这条最常见的终局路径是坏的</b>，
 * 而部分核销（不归零）反而正常 —— 缺陷只在归零那一刻爆发，故长期潜伏。
 *
 * <p><b>为什么不是「补约束」而是「改常量」</b>：{@code t_business_doc} 早已有表达
 * 「全额核销」的合法态 {@code FULLY_RECONCILED}，且
 * {@code ArapSettlementServiceImpl:213-214} 对<b>完全相同</b>的条件用的就是它。
 * {@code SETTLED} 是 {@code ArapStatus}（核销单/预付款专用）串台过来的旧别名。
 * 若反过来给 CHECK 加 SETTLED，会造出「全额核销」的第二种合法拼写，
 * 下游所有 {@code status='FULLY_RECONCILED'} 的查询将<b>静默漏掉</b>坏账核销的单据
 * —— 比崩溃更难发现。故按 AGENTS §4.2 第 19 条的判据（是否为某状态机的合法中间态）：
 * 此处已有等价合法态 ⇒ 判定为<b>代码用错常量</b>，改代码而非改约束。
 */
@DisplayName("P105 坏账核销：全额核销不得写入非法状态 SETTLED")
class BadDebtWriteOffStatusRealDBTest extends AbstractMapperTest {

    @Autowired
    private BadDebtService badDebtService;

    @Autowired
    private BusinessDocMapper businessDocMapper;

    private Long createReceivableDoc(String amount) {
        return jdbcTemplate.queryForObject(
                "INSERT INTO t_business_doc (doc_no, doc_type, doc_date, period, status, "
                        + "amount, settled_amount, unsettled_amount, enterprise_id, deleted) "
                        + "VALUES (?, 'RECEIPT', CURRENT_DATE, '209912', 'APPROVED', "
                        + "?, 0, ?, 1, 0) RETURNING id",
                Long.class, "P105BD" + (System.nanoTime() % 1000000),
                new BigDecimal(amount), new BigDecimal(amount));
    }

    @Test
    @DisplayName("全额核销：未清归零后状态必须是合法的 FULLY_RECONCILED（原报 23514）")
    void fullWriteOffMustUseLegalStatus() {
        Long docId = createReceivableDoc("1000.00");

        badDebtService.writeOff(docId, "NOTE_RECEIVABLE", new BigDecimal("1000.00"), "P105 全额核销", 1L);

        BusinessDocEntity doc = businessDocMapper.selectById(docId);
        assertNotNull(doc, "单据不存在");
        assertEquals(0, BigDecimal.ZERO.compareTo(doc.getUnsettledAmount()),
                "未清金额未归零，实际=" + doc.getUnsettledAmount());
        assertEquals(0, new BigDecimal("1000.00").compareTo(doc.getSettledAmount()),
                "已清金额应为 1000.00，实际=" + doc.getSettledAmount());
        assertEquals("FULLY_RECONCILED", doc.getStatus(),
                "全额核销后状态必须是 chk_doc_status 允许的 FULLY_RECONCILED，实际=" + doc.getStatus());
    }

    @Test
    @DisplayName("部分核销：不归零时不得改动状态（负向断言：不该做的没做）")
    void partialWriteOffLeavesStatusUntouched() {
        Long docId = createReceivableDoc("1000.00");

        badDebtService.writeOff(docId, "NOTE_RECEIVABLE", new BigDecimal("400.00"), "P105 部分核销", 1L);

        BusinessDocEntity doc = businessDocMapper.selectById(docId);
        assertNotNull(doc);
        assertEquals(0, new BigDecimal("600.00").compareTo(doc.getUnsettledAmount()),
                "未清金额应为 600.00，实际=" + doc.getUnsettledAmount());
        assertEquals("APPROVED", doc.getStatus(),
                "未归零时不应改状态（现有语义），实际=" + doc.getStatus());
    }

    @Test
    @DisplayName("核销额大于未清额必须抛 BusinessException（铁律 #14，负向断言）")
    void overWriteOffIsRejected() {
        Long docId = createReceivableDoc("1000.00");

        Exception ex = org.junit.jupiter.api.Assertions.assertThrows(
                com.huicai.common.exception.BusinessException.class,
                () -> badDebtService.writeOff(docId, "NOTE_RECEIVABLE",
                        new BigDecimal("1500.00"), "超额核销", 1L));
        assertNotNull(ex.getMessage());

        BusinessDocEntity doc = businessDocMapper.selectById(docId);
        assertEquals(0, new BigDecimal("1000.00").compareTo(doc.getUnsettledAmount()),
                "被拒的核销不得改动金额");
    }
}