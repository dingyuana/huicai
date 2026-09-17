package com.huicai.sme.arap.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.huicai.base.business.entity.ArapSettlementEntity;
import com.huicai.base.business.entity.BusinessDocEntity;
import com.huicai.base.business.mapper.ArapSettlementMapper;
import com.huicai.base.business.mapper.BusinessDocMapper;
import com.huicai.base.masterdata.entity.CustomerEntity;
import com.huicai.base.masterdata.entity.VendorEntity;
import com.huicai.base.masterdata.mapper.CustomerMapper;
import com.huicai.base.masterdata.mapper.VendorMapper;
import com.huicai.base.system.mapper.PeriodMapper;
import com.huicai.common.exception.BusinessException;
import com.huicai.sme.arap.constant.ArapStatus;
import com.huicai.sme.arap.constant.BusinessDocStatus;
import com.huicai.sme.arap.service.ArapBalanceReportService.ArapBalancePartyVO;
import com.huicai.sme.arap.service.ArapBalanceReportService.ArapBalanceSummaryVO;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * 应收应付余额汇总服务单元测试（P75）
 * <p>
 * BDD 场景映射：
 * <ul>
 *   <li>场景 1 会计恒等式 → balanceIdentity_holds</li>
 *   <li>场景 2 已结清单据不计入期末余额 → fullyReconciled_excludedFromClosing</li>
 *   <li>场景 3 跨期核销归属 → crossPeriodSettlement_currentSettledCounted</li>
 *   <li>场景 4 反核销不污染 → reversedAndRedFlush_excludedFromSettled</li>
 *   <li>场景 5 期间必填守卫 → periodRequired_throws400</li>
 *   <li>场景 6 数据权限隔离（service 不手工注入 enterprise_id，依赖拦截器）→ wrapper_noEnterpriseFilter</li>
 * </ul>
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("应收应付余额汇总服务单元测试")
class ArapBalanceReportServiceImplTest {

    @Mock private BusinessDocMapper businessDocMapper;
    @Mock private ArapSettlementMapper settlementMapper;
    @Mock private PeriodMapper periodMapper;
    @Mock private CustomerMapper customerMapper;
    @Mock private VendorMapper vendorMapper;

    @InjectMocks
    private ArapBalanceReportServiceImpl service;

    // ===== helpers =====

    private BusinessDocEntity doc(Long id, String docType, String period, String status,
                                  String amount, String unsettled, Long customerId, Long supplierId) {
        BusinessDocEntity e = new BusinessDocEntity();
        e.setId(id);
        e.setDocType(docType);
        e.setPeriod(period);
        e.setStatus(status);
        e.setAmount(new BigDecimal(amount));
        e.setUnsettledAmount(new BigDecimal(unsettled));
        e.setCustomerId(customerId);
        e.setSupplierId(supplierId);
        return e;
    }

    private ArapSettlementEntity settlement(Long id, String period, String status, String partyType,
                                            Long partyId, String totalAmount) {
        ArapSettlementEntity e = new ArapSettlementEntity();
        e.setId(id);
        e.setPeriod(period);
        e.setStatus(status);
        e.setPartyType(partyType);
        e.setPartyId(partyId);
        e.setTotalAmount(new BigDecimal(totalAmount));
        return e;
    }

    private void assertMoney(BigDecimal expected, BigDecimal actual) {
        assertEquals(0, expected.compareTo(actual),
                () -> "expected " + expected + " but got " + actual);
    }

    private void mockPeriodExists() {
        when(periodMapper.selectCount(any())).thenReturn(1L);
    }

    // ===== 场景 1：会计恒等式成立 =====

    @Test
    @DisplayName("场景1_会计恒等式成立（opening+current−settled==closing，合计==Σclosing）")
    void balanceIdentity_holds() {
        mockPeriodExists();
        // 应收单：202609 发生 50000，期末未核销 30000（本期已核销 20000）
        when(businessDocMapper.selectList(any())).thenReturn(
                List.of(doc(1L, "INVOICE_OUT", "202609", BusinessDocStatus.APPROVED,
                        "50000", "30000", 1001L, null)),
                List.of()); // 应付侧无单据
        when(settlementMapper.selectList(any())).thenReturn(
                List.of(settlement(1L, "202609", ArapStatus.CONFIRMED, "CUSTOMER", 1001L, "20000")),
                List.of()); // 应付侧无核销
        CustomerEntity c = new CustomerEntity();
        c.setId(1001L);
        c.setName("客户A");
        when(customerMapper.selectBatchIds(any())).thenReturn(List.of(c));

        ArapBalanceSummaryVO vo = service.getBalanceSummary("202609", null, null);

        assertTrue(vo.consistent(), "恒等式应成立 → consistent=true");
        assertEquals(1, vo.receivables().size());
        ArapBalancePartyVO row = vo.receivables().get(0);
        assertEquals(1001L, row.partyId());
        assertEquals("客户A", row.partyName());
        // opening = closing − current + settled = 30000 − 50000 + 20000 = 0
        assertMoney(BigDecimal.ZERO, row.openingUnsettled());
        assertMoney(new BigDecimal("50000"), row.currentAmount());
        assertMoney(new BigDecimal("20000"), row.currentSettled());
        assertMoney(new BigDecimal("30000"), row.closingUnsettled());
        // 恒等式：opening + current − settled == closing
        assertMoney(row.closingUnsettled(),
                row.openingUnsettled().add(row.currentAmount()).subtract(row.currentSettled()));
        // 合计 == Σ closingUnsettled
        assertMoney(new BigDecimal("30000"), vo.receivableTotal());
        assertMoney(BigDecimal.ZERO, vo.payableTotal());
        assertTrue(vo.payables().isEmpty());
        assertEquals("202609", vo.period());
    }

    // ===== 场景 2：已结清单据不计入期末余额 =====

    @Test
    @DisplayName("场景2_已结清单据不计入closing（但计入本期应收current）")
    void fullyReconciled_excludedFromClosing() {
        mockPeriodExists();
        // doc1 已完全核销（unsettled=0）→ 不计入 closing，但计入 current
        // doc2 部分核销 → closing=5000
        when(businessDocMapper.selectList(any())).thenReturn(
                List.of(
                        doc(1L, "INVOICE_OUT", "202609", BusinessDocStatus.FULLY_RECONCILED,
                                "10000", "0", 1001L, null),
                        doc(2L, "INVOICE_OUT", "202609", BusinessDocStatus.PARTIALLY_RECONCILED,
                                "5000", "5000", 1001L, null)),
                List.of());
        when(settlementMapper.selectList(any())).thenReturn(
                List.of(settlement(1L, "202609", ArapStatus.CONFIRMED, "CUSTOMER", 1001L, "10000")),
                List.of());
        CustomerEntity c = new CustomerEntity();
        c.setId(1001L);
        c.setName("客户A");
        when(customerMapper.selectBatchIds(any())).thenReturn(List.of(c));

        ArapBalanceSummaryVO vo = service.getBalanceSummary("202609", null, null);

        ArapBalancePartyVO row = vo.receivables().get(0);
        assertMoney(new BigDecimal("5000"), row.closingUnsettled());   // doc1 的 10000 被排除
        assertMoney(new BigDecimal("15000"), row.currentAmount());     // 但计入本期应收
        assertMoney(BigDecimal.ZERO, row.openingUnsettled());          // 5000 − 15000 + 10000
        assertTrue(vo.consistent());
    }

    // ===== 场景 3：跨期核销归属正确 =====

    @Test
    @DisplayName("场景3_跨期核销计入本期实收（202608 单 202609 核销 20000）")
    void crossPeriodSettlement_currentSettledCounted() {
        mockPeriodExists();
        // 202608 应收单 50000，202609 核销 20000 → 期末未核销 30000
        when(businessDocMapper.selectList(any())).thenReturn(
                List.of(doc(1L, "INVOICE_OUT", "202608", BusinessDocStatus.VOUCHERED,
                        "50000", "30000", 1001L, null)),
                List.of());
        when(settlementMapper.selectList(any())).thenReturn(
                List.of(settlement(1L, "202609", ArapStatus.CONFIRMED, "CUSTOMER", 1001L, "20000")),
                List.of());
        CustomerEntity c = new CustomerEntity();
        c.setId(1001L);
        c.setName("客户A");
        when(customerMapper.selectBatchIds(any())).thenReturn(List.of(c));

        ArapBalanceSummaryVO vo = service.getBalanceSummary("202609", null, null);

        ArapBalancePartyVO row = vo.receivables().get(0);
        assertMoney(new BigDecimal("20000"), row.currentSettled());    // 计入本期实收
        assertMoney(new BigDecimal("0"), row.currentAmount());         // 本期无新发生
        assertMoney(new BigDecimal("30000"), row.closingUnsettled());  // 期末余额已减少 20000
        assertMoney(new BigDecimal("50000"), row.openingUnsettled());  // 期初 50000
        assertTrue(vo.consistent());
    }

    // ===== 场景 4：反核销不污染视图 =====

    @Test
    @DisplayName("场景4_REVERSED与红字负额对冲单均不计入本期实收（防双计）")
    void reversedAndRedFlush_excludedFromSettled() {
        mockPeriodExists();
        when(businessDocMapper.selectList(any())).thenReturn(
                List.of(doc(1L, "INVOICE_OUT", "202609", BusinessDocStatus.APPROVED,
                        "20000", "20000", 1001L, null)),
                List.of());
        // 原核销单 REVERSED（排除）；红字对冲单 totalAmount=-20000 CONFIRMED（排除）；正常单 20000（计入）
        when(settlementMapper.selectList(any())).thenReturn(
                List.of(
                        settlement(1L, "202609", ArapStatus.REVERSED, "CUSTOMER", 1001L, "20000"),
                        settlement(2L, "202609", ArapStatus.CONFIRMED, "CUSTOMER", 1001L, "-20000"),
                        settlement(3L, "202609", ArapStatus.CONFIRMED, "CUSTOMER", 1001L, "20000")),
                List.of());
        CustomerEntity c = new CustomerEntity();
        c.setId(1001L);
        c.setName("客户A");
        when(customerMapper.selectBatchIds(any())).thenReturn(List.of(c));

        ArapBalanceSummaryVO vo = service.getBalanceSummary("202609", null, null);

        ArapBalancePartyVO row = vo.receivables().get(0);
        assertMoney(new BigDecimal("20000"), row.currentSettled());    // 仅正常单计入，无双计
        assertTrue(vo.consistent());
    }

    // ===== 场景 5：期间必填守卫 =====

    @Test
    @DisplayName("场景5_period缺失或非6位数字 → BusinessException 400")
    void periodRequired_throws400() {
        BusinessException nullPeriod = assertThrows(BusinessException.class,
                () -> service.getBalanceSummary(null, null, null));
        assertEquals(400, nullPeriod.getCode());
        assertTrue(nullPeriod.getMessage().contains("期间"), "错误信息应提示期间必填: " + nullPeriod.getMessage());

        BusinessException badFormat = assertThrows(BusinessException.class,
                () -> service.getBalanceSummary("2026-09", null, null));
        assertEquals(400, badFormat.getCode());
    }

    // ===== 场景 6：数据权限隔离（服务不手工注入 enterprise_id，依赖拦截器） =====

    @Test
    @DisplayName("场景6_查询wrapper不含enterprise_id硬编码（数据权限由拦截器注入）")
    void wrapper_noEnterpriseFilter() {
        mockPeriodExists();
        when(businessDocMapper.selectList(any())).thenReturn(
                List.of(doc(1L, "INVOICE_OUT", "202609", BusinessDocStatus.APPROVED,
                        "50000", "30000", 1001L, null)),
                List.of());
        when(settlementMapper.selectList(any())).thenReturn(List.of(), List.of());
        CustomerEntity c = new CustomerEntity();
        c.setId(1001L);
        c.setName("客户A");
        when(customerMapper.selectBatchIds(any())).thenReturn(List.of(c));

        service.getBalanceSummary("202609", 1001L, null);

        ArgumentCaptor<LambdaQueryWrapper<BusinessDocEntity>> captor =
                ArgumentCaptor.forClass(LambdaQueryWrapper.class);
        verify(businessDocMapper, atLeastOnce()).selectList(captor.capture());
        // 应收/应付各一次查询都被捕获，遍历全部断言（getValue() 只返回最后一次）
        List<String> sqls = captor.getAllValues().stream()
                .map(LambdaQueryWrapper::getSqlSegment)
                .toList();
        assertTrue(sqls.stream().noneMatch(s -> s.contains("enterprise_id")),
                "service 不应手工注入 enterprise_id: " + sqls);
        assertTrue(sqls.stream().anyMatch(s -> s.contains("doc_type")),
                "应包含 doc_type 条件: " + sqls);
        assertTrue(sqls.stream().anyMatch(s -> s.contains("customer_id")),
                "customerId 过滤应落进 SQL: " + sqls);
    }

    // ===== 应付侧对称 =====

    @Test
    @DisplayName("应付侧_供应商维度聚合与名称")
    void payableSide_aggregatesAndNames() {
        mockPeriodExists();
        when(businessDocMapper.selectList(any())).thenReturn(
                List.of(), // 应收侧无单据
                List.of(doc(1L, "INVOICE_IN", "202609", BusinessDocStatus.VOUCHERED,
                        "80000", "60000", null, 2002L)));
        when(settlementMapper.selectList(any())).thenReturn(
                List.of(),
                List.of(settlement(1L, "202609", ArapStatus.CONFIRMED, "VENDOR", 2002L, "20000")));
        VendorEntity v = new VendorEntity();
        v.setId(2002L);
        v.setName("供应商B");
        when(vendorMapper.selectBatchIds(any())).thenReturn(List.of(v));

        ArapBalanceSummaryVO vo = service.getBalanceSummary("202609", null, null);

        assertTrue(vo.receivables().isEmpty());
        assertMoney(BigDecimal.ZERO, vo.receivableTotal());
        assertEquals(1, vo.payables().size());
        ArapBalancePartyVO row = vo.payables().get(0);
        assertEquals(2002L, row.partyId());
        assertEquals("供应商B", row.partyName());
        assertMoney(new BigDecimal("60000"), row.closingUnsettled());
        assertMoney(new BigDecimal("80000"), row.currentAmount());
        assertMoney(new BigDecimal("20000"), row.currentSettled());
        assertMoney(BigDecimal.ZERO, row.openingUnsettled());
        assertMoney(new BigDecimal("60000"), vo.payableTotal());
        assertTrue(vo.consistent());
    }
}