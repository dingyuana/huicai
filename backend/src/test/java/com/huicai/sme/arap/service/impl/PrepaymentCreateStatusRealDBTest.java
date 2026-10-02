package com.huicai.sme.arap.service.impl;

import com.huicai.common.test.AbstractMapperTest;
import com.huicai.sme.arap.constant.ArapStatus;
import com.huicai.sme.arap.entity.PrepaymentEntity;
import com.huicai.sme.arap.mapper.PrepaymentMapper;
import com.huicai.sme.arap.service.PrepaymentService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.math.BigDecimal;
import java.time.LocalDate;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

/**
 * 预付款创建真库测试（REQ-2026-129 / P105 门禁 `check_entity_status_massassignment` 存量项）
 *
 * <p><b>缺陷</b>：{@code PrepaymentServiceImpl#create()} 原本
 * {@code if (status == null) setStatus(ArapStatus.DRAFT)}，
 * 而 {@code PrepaymentController#create} 直收 {@code @RequestBody PrepaymentEntity}
 * （违反铁律 #13）⇒ 客户端可指定任意 status。
 *
 * <p><b>比前 5 处更危险</b>：{@code t_prepayment} <b>根本没有 status CHECK 约束</b>
 * （实测 CHECK 数量为 0）⇒
 * ①DB 层<b>无任何兜底</b>，连写错值（如 {@code "APPLIED"} 甚至乱码）也不会被拦；
 * ②客户端可 POST {@code status=APPLIED} / {@code SETTLED} <b>一步跳过</b>
 * {@code confirm()}（DRAFT→CONFIRMED）这次人工确认（铁律 #1）。
 */
@DisplayName("P105 预付款创建：状态不可由客户端指定")
class PrepaymentCreateStatusRealDBTest extends AbstractMapperTest {

    @Autowired
    private PrepaymentService prepaymentService;

    @Autowired
    private PrepaymentMapper prepaymentMapper;

    private PrepaymentEntity draft() {
        PrepaymentEntity e = new PrepaymentEntity();
        e.setPeriod("209912");
        e.setTxDate(LocalDate.now());
        e.setAmount(new BigDecimal("1000.00"));
        e.setSummary("P105 预付款");
        e.setEnterpriseId(1L);
        return e;
    }

    @Test
    @DisplayName("不传 status 时落到服务层默认值 DRAFT")
    void defaultStatusComesFromService() {
        PrepaymentEntity e = draft();
        e.setStatus(null);

        PrepaymentEntity saved = prepaymentService.create(e);

        assertNotNull(saved.getId(), "未落库");
        assertEquals(ArapStatus.DRAFT, prepaymentMapper.selectById(saved.getId()).getStatus(),
                "默认值应由服务层决定");
    }

    @Test
    @DisplayName("负向：客户端传 status=APPLIED 必须被忽略（铁律 #1 跳过 confirm）")
    void clientSuppliedAppliedIsIgnored() {
        PrepaymentEntity e = draft();
        e.setStatus(ArapStatus.APPLIED);

        PrepaymentEntity saved = prepaymentService.create(e);

        PrepaymentEntity db = prepaymentMapper.selectById(saved.getId());
        assertNotNull(db, "回读不到");
        assertEquals(ArapStatus.DRAFT, db.getStatus(),
                "客户端传入的 status 必须被忽略 —— 否则可一步创建「已核销」的预付款，"
                        + "跳过 confirm()（DRAFT→CONFIRMED）这次人工确认（铁律 #1）。实际=" + db.getStatus());
    }

    @Test
    @DisplayName("负向：t_prepayment 无 CHECK，连乱码状态都能落库 —— 服务层必须兜住")
    void garbageStatusIsRejectedAtServiceLayer() {
        PrepaymentEntity e = draft();
        e.setStatus("__GARBAGE__");

        PrepaymentEntity saved = prepaymentService.create(e);

        assertEquals(ArapStatus.DRAFT, prepaymentMapper.selectById(saved.getId()).getStatus(),
                "t_prepayment 没有任何 CHECK 约束 ⇒ DB 层零兜底，"
                        + "非法状态只能靠服务层挡住");
    }
}