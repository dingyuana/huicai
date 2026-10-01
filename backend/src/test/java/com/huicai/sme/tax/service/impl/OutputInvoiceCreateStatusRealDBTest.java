package com.huicai.sme.tax.service.impl;

import com.huicai.common.test.AbstractMapperTest;
import com.huicai.base.business.entity.OutputInvoiceEntity;
import com.huicai.base.business.mapper.OutputInvoiceMapper;
import com.huicai.sme.tax.service.TaxService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.math.BigDecimal;
import java.time.LocalDate;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

/**
 * 销项发票创建的默认状态真库测试（REQ-2026-136 / P105 契约工作带出的 P0-3）
 *
 * <p><b>缺陷</b>：{@code TaxServiceImpl.createOutput()} 在 {@code status == null} 时写
 * {@code "DRAFT"}，而 {@code chk_output_invoice_status} 的允许集<b>不含 DRAFT</b> ⇒
 * 紧随的 {@code outputMapper.insert()} 必抛 23514。任何未显式传 status 的调用方直接 500。
 * 前端目前不调本端点（{@code tax.ts} 仅有 page/detail/delete/submit-review/confirm/reject），
 * 故该缺陷<b>长期潜伏</b>，只有手工/脚本调用才会暴露。
 *
 * <p><b>修法</b>：对齐同类 {@code createInput} 的约定 —— {@code InvoiceStatus.PENDING_CONFIRM}，
 * 并用常量而非字面量（本条缺陷的根因正是散落的 {@code "DRAFT"} 字面量）。
 */
@DisplayName("P105 销项发票创建：默认状态必须是 CHECK 允许值")
class OutputInvoiceCreateStatusRealDBTest extends AbstractMapperTest {

    @Autowired
    private TaxService taxService;

    @Autowired
    private OutputInvoiceMapper outputInvoiceMapper;

    private OutputInvoiceEntity draft() {
        OutputInvoiceEntity e = new OutputInvoiceEntity();
        e.setInvoiceNo("P105OI" + (System.nanoTime() % 1000000));
        e.setInvoiceDate(LocalDate.now());
        // amount 为不含税金额：createOutput 按 amount * taxRate / 100 反算税额
        e.setAmount(new BigDecimal("100.00"));
        e.setTaxAmount(new BigDecimal("13.00"));
        e.setTotalAmount(new BigDecimal("113.00"));
        e.setTaxRate(new BigDecimal("13"));
        e.setPeriod("209912");
        e.setInvoiceType("SPECIAL");
        return e;
    }

    @Test
    @DisplayName("不传 status 时默认 PENDING_CONFIRM（原报 23514，DRAFT 非法）")
    void defaultStatusIsLegal() {
        OutputInvoiceEntity e = draft();
        e.setStatus(null);

        OutputInvoiceEntity saved = taxService.createOutput(e);

        assertNotNull(saved.getId(), "未落库");
        OutputInvoiceEntity db = outputInvoiceMapper.selectById(saved.getId());
        assertNotNull(db, "回读不到");
        assertEquals("PENDING_CONFIRM", db.getStatus(),
                "默认状态必须是 chk_output_invoice_status 允许的值，实际=" + db.getStatus());
    }

    @Test
    @DisplayName("税率自动算出税额（保留原有行为，防修状态时改坏金额）")
    void taxAmountIsDerived() {
        OutputInvoiceEntity e = draft();
        e.setTaxAmount(null);

        OutputInvoiceEntity saved = taxService.createOutput(e);

        assertEquals(0, new BigDecimal("13.00").compareTo(saved.getTaxAmount()),
                "税额应按税率 13% 算出 13.00，实际=" + saved.getTaxAmount());
    }

    @Test
    @DisplayName("负向：客户端传 status=VOUCHERED 必须被忽略，强制 PENDING_CONFIRM（铁律 #1 人审）")
    void clientSuppliedStatusIsIgnored() {
        OutputInvoiceEntity e = draft();
        e.setStatus("VOUCHERED");   // 越权尝试：直接跳到「已制证」，绕过人工审核

        OutputInvoiceEntity saved = taxService.createOutput(e);

        OutputInvoiceEntity db = outputInvoiceMapper.selectById(saved.getId());
        assertNotNull(db, "回读不到");
        assertEquals("PENDING_CONFIRM", db.getStatus(),
                "客户端传入的 status 必须被忽略 —— 否则可直接创建「已制证」发票，"
                        + "绕过 PENDING_CONFIRM→PENDING_REVIEW→CONFIRMED 的人工审核链（铁律 #1）");
    }

    @Test
    @DisplayName("负向：金额为空必须抛 BusinessException（铁律 #14）")
    void missingAmountIsRejected() {
        OutputInvoiceEntity e = draft();
        e.setAmount(null);
        org.junit.jupiter.api.Assertions.assertThrows(
                com.huicai.common.exception.BusinessException.class,
                () -> taxService.createOutput(e));
    }
}