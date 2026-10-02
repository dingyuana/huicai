package com.huicai.sme.tax.service.impl;

import com.huicai.base.business.entity.InputInvoiceEntity;
import com.huicai.base.business.mapper.InputInvoiceMapper;
import com.huicai.common.test.AbstractMapperTest;
import com.huicai.sme.tax.service.TaxService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.math.BigDecimal;
import java.time.LocalDate;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

/**
 * 进项发票创建真库测试（REQ-2026-129 / P105 门禁存量项）
 *
 * <p><b>缺陷</b>：{@code TaxServiceImpl#createInput()} 有<b>两处</b>条件兜底，
 * 而 {@code TaxController#createInput(@RequestBody InputInvoiceEntity)} 直收 Entity
 * （违反铁律 #13）⇒ 客户端可指定<b>两个</b>状态字段：
 * <ol>
 *   <li>{@code status} —— {@code chk_input_invoice_status} 允许
 *       {@code .../VOUCHERED/...}，故 POST {@code status=VOUCHERED} 可
 *       <b>一步跳过</b> {@code PENDING_CONFIRM→PENDING_REVIEW→CONFIRMED} 人工审核链（铁律 #1）。
 *       这与已修的 {@code createOutput}（销项）同构。</li>
 *   <li>{@code certificationStatus} —— 这是<b>另一个列</b>
 *       （{@code chk_cert_status} 允许 {@code UNCERTIFIED/CERTIFIED/INVALID/CANCELLED}），
 *       进项发票认证是<b>税务合规动作</b>，POST {@code CERTIFIED} 可一步跳过认证。</li>
 * </ol>
 * 两者都只在 null 时兜底，故均可被客户端指定。
 */
@DisplayName("P105 进项发票创建：status 与 certificationStatus 均不可由客户端指定")
class InputInvoiceCreateStatusRealDBTest extends AbstractMapperTest {

    @Autowired
    private TaxService taxService;

    @Autowired
    private InputInvoiceMapper inputInvoiceMapper;

    private InputInvoiceEntity draft() {
        InputInvoiceEntity e = new InputInvoiceEntity();
        e.setInvoiceNo("P105II" + (System.nanoTime() % 1000000));
        e.setInvoiceDate(LocalDate.now());
        e.setPeriod("209912");
        e.setAmount(new BigDecimal("100.00"));
        e.setTaxRate(new BigDecimal("13"));
        e.setTaxAmount(new BigDecimal("13.00"));
        e.setTotalAmount(new BigDecimal("113.00"));
        e.setInvoiceType("SPECIAL");
        e.setEnterpriseId(1L);
        return e;
    }

    @Test
    @DisplayName("不传任何状态时落到服务层默认值 PENDING_CONFIRM + UNCERTIFIED")
    void defaultStatusesComeFromService() {
        InputInvoiceEntity e = draft();
        e.setStatus(null);
        e.setCertificationStatus(null);

        InputInvoiceEntity saved = taxService.createInput(e);

        assertNotNull(saved.getId(), "未落库");
        InputInvoiceEntity db = inputInvoiceMapper.selectById(saved.getId());
        assertNotNull(db, "回读不到");
        assertEquals("PENDING_CONFIRM", db.getStatus(), "审核状态默认值应由服务层决定");
        assertEquals("UNCERTIFIED", db.getCertificationStatus(), "认证状态默认值应由服务层决定");
    }

    @Test
    @DisplayName("负向：客户端传 status=VOUCHERED 必须被忽略（铁律 #1 跳过审核链）")
    void clientSuppliedVoucheredIsIgnored() {
        InputInvoiceEntity e = draft();
        e.setStatus("VOUCHERED");

        InputInvoiceEntity saved = taxService.createInput(e);

        assertEquals("PENDING_CONFIRM", inputInvoiceMapper.selectById(saved.getId()).getStatus(),
                "客户端传入的 status 必须被忽略 —— 否则可一步创建「已制证」进项发票，"
                        + "绕过 PENDING_CONFIRM→PENDING_REVIEW→CONFIRMED 的审核链（铁律 #1）");
    }

    @Test
    @DisplayName("负向：客户端传 certificationStatus=CERTIFIED 必须被忽略（税务认证合规动作）")
    void clientSuppliedCertifiedIsIgnored() {
        InputInvoiceEntity e = draft();
        e.setCertificationStatus("CERTIFIED");

        InputInvoiceEntity saved = taxService.createInput(e);

        InputInvoiceEntity db = inputInvoiceMapper.selectById(saved.getId());
        assertNotNull(db, "回读不到");
        assertEquals("UNCERTIFIED", db.getCertificationStatus(),
                "certificationStatus 是独立于 status 的**另一列**（chk_cert_status 约束），"
                        + "同样不可由客户端指定 —— 认证是税务合规动作（铁律 #1）。实际=" + db.getCertificationStatus());
    }
}