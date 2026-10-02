package com.huicai.sme.budget.service.impl;

import com.huicai.common.test.AbstractMapperTest;
import com.huicai.sme.budget.constant.BudgetStatus;
import com.huicai.sme.budget.entity.BudgetEntity;
import com.huicai.sme.budget.mapper.BudgetMapper;
import com.huicai.sme.budget.service.BudgetService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.math.BigDecimal;
import java.util.Collections;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

/**
 * 预算创建真库测试（REQ-2026-129 / P105 门禁存量项）
 *
 * <p><b>缺陷</b>：{@code BudgetServiceImpl#create()} 原本
 * {@code if (status == null) setStatus(BUDGET_DRAFT)}，而
 * {@code BudgetController#create(@RequestBody CreateRequest)} 内层仍是 {@code BudgetEntity}
 * —— 只包一层 request **不构成隔离**（与 AssetInventory 同型），其 status 可被客户端绑定。
 *
 * <p>{@code chk_budget_status} 允许
 * {@code DRAFT/SUBMITTED/APPROVED/ACTIVE/CLOSED/REJECTED/FROZEN}，
 * 故 POST {@code status=APPROVED} / {@code ACTIVE} 可**一步跳过**提交与审批
 * （铁律 #1：人是唯一审核主体）。
 */
@DisplayName("P105 预算创建：状态不可由客户端指定")
class BudgetCreateStatusRealDBTest extends AbstractMapperTest {

    @Autowired
    private BudgetService budgetService;

    @Autowired
    private BudgetMapper budgetMapper;

    private BudgetEntity draft() {
        BudgetEntity e = new BudgetEntity();
        e.setBudgetNo("P105BG" + (System.nanoTime() % 1000000));
        e.setBudgetName("P105预算");
        e.setPeriod("209912");
        e.setBudgetType("OVERALL");
        e.setTotalAmount(new BigDecimal("10000.00"));
        e.setEnterpriseId(1L);
        return e;
    }

    @Test
    @DisplayName("不传 status 时落到服务层默认值 DRAFT")
    void defaultStatusComesFromService() {
        BudgetEntity e = draft();
        e.setStatus(null);

        BudgetEntity saved = budgetService.create(e, Collections.emptyList());

        assertNotNull(saved.getId(), "未落库");
        assertEquals(BudgetStatus.BUDGET_DRAFT, budgetMapper.selectById(saved.getId()).getStatus(),
                "默认值应由服务层决定");
    }

    @Test
    @DisplayName("负向：客户端传 status=APPROVED 必须被忽略（铁律 #1）")
    void clientSuppliedApprovedIsIgnored() {
        BudgetEntity e = draft();
        e.setStatus(BudgetStatus.BUDGET_APPROVED);

        BudgetEntity saved = budgetService.create(e, Collections.emptyList());

        BudgetEntity db = budgetMapper.selectById(saved.getId());
        assertNotNull(db, "回读不到");
        assertEquals(BudgetStatus.BUDGET_DRAFT, db.getStatus(),
                "客户端传入的 status 必须被忽略 —— 否则可一步创建「已审批/已启用」的预算，"
                        + "跳过提交与审批（铁律 #1）。实际=" + db.getStatus());
    }

    @Test
    @DisplayName("负向：客户端传 status=ACTIVE 同样必须被忽略")
    void clientSuppliedActiveIsIgnored() {
        BudgetEntity e = draft();
        e.setStatus(BudgetStatus.BUDGET_ACTIVE);

        BudgetEntity saved = budgetService.create(e, Collections.emptyList());

        assertEquals(BudgetStatus.BUDGET_DRAFT, budgetMapper.selectById(saved.getId()).getStatus(),
                "「已启用」是更靠后的状态，更不允许由客户端直接指定");
    }
}