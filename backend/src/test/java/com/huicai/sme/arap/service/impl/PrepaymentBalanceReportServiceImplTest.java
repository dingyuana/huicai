package com.huicai.sme.arap.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.huicai.base.business.entity.ArapSettlementEntity;
import com.huicai.base.business.mapper.ArapSettlementMapper;
import com.huicai.base.masterdata.entity.CustomerEntity;
import com.huicai.base.masterdata.entity.VendorEntity;
import com.huicai.base.masterdata.mapper.CustomerMapper;
import com.huicai.base.masterdata.mapper.VendorMapper;
import com.huicai.base.system.mapper.PeriodMapper;
import com.huicai.common.exception.BusinessException;
import com.huicai.sme.arap.constant.ArapStatus;
import com.huicai.sme.arap.entity.PrepaymentEntity;
import com.huicai.sme.arap.mapper.PrepaymentMapper;
import com.huicai.sme.arap.service.PrepaymentBalanceReportService.PrepaymentBalancePartyVO;
import com.huicai.sme.arap.service.PrepaymentBalanceReportService.PrepaymentBalanceSummaryVO;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * 预收预付余额汇总服务单元测试（P78）
 * <p>
 * BDD 场景映射：
 * <ul>
 *   <li>场景 1 会计恒等式 → balanceIdentity_holds</li>
 *   <li>场景 2 已冲销不计期末但计入 currentReversed → reversed_excludedFromClosing</li>
 *   <li>场景 3 期初推导 → openingDerivation</li>
 *   <li>场景 4 期间必填守卫 → periodRequired_throws400</li>
 *   <li>场景 5 数据权限隔离（service 不手工注入 enterprise_id，依赖拦截器）→ wrapper_noEnterpriseFilter</li>
 *   <li>场景 6 预收/预付对称不混入 → preReceiptSide_noVendorData</li>
 *   <li>附加：前缀隔离防与 P75 JS/FS 双计 → settlementPrefixIsolation</li>
 * </ul>
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("预收预付余额汇总服务单元测试")
class PrepaymentBalanceReportServiceImplTest {

    @Mock private PrepaymentMapper prepaymentMapper;
    @Mock private ArapSettlementMapper settlementMapper;
    @Mock private PeriodMapper periodMapper;
    @Mock private CustomerMapper customerMapper;
    @Mock private VendorMapper vendorMapper;

    @InjectMocks
    private PrepaymentBalanceReportServiceImpl service;

    /** 单跑本测试类时 MyBatis-Plus lambda 元数据未随 Spring 上下文初始化，需手动补（对齐 BusinessDocServiceImplTest 既有做法） */
    @BeforeAll
    @SuppressWarnings({"unchecked", "rawtypes"})
    static void initLambdaCache() {
        com.baomidou.mybatisplus.core.metadata.TableInfoHelper.initTableInfo(
                new org.apache.ibatis.builder.MapperBuilderAssistant(
                        new com.baomidou.mybatisplus.core.MybatisConfiguration(), ""), PrepaymentEntity.class);
        com.baomidou.mybatisplus.core.metadata.TableInfoHelper.initTableInfo(
                new org.apache.ibatis.builder.MapperBuilderAssistant(
                        new com.baomidou.mybatisplus.core.MybatisConfiguration(), ""), ArapSettlementEntity.class);
    }

    // ===== helpers =====

    private PrepaymentEntity prepay(Long id, Long customerId, Long vendorId, String status,
                                    String amount, String unsettled, String period, LocalDate txDate) {
        PrepaymentEntity e = new PrepaymentEntity();
        e.setId(id);
        e.setCustomerId(customerId);
        e.setVendorId(vendorId);
        e.setStatus(status);
        e.setAmount(new BigDecimal(amount));
        e.setUnsettledAmount(new BigDecimal(unsettled));
        e.setPeriod(period);
        e.setTxDate(txDate);
        return e;
    }

    private ArapSettlementEntity settle(Long id, String no, String period, String status,
                                        String partyType, Long partyId, String totalAmount) {
        ArapSettlementEntity e = new ArapSettlementEntity();
        e.setId(id);
        e.setSettlementNo(no);
        e.setPeriod(period);
        e.setStatus(status);
        e.setPartyType(partyType);
        e.setPartyId(partyId);
        e.setTotalAmount(new BigDecimal(totalAmount));
        return e;
    }

    private CustomerEntity cust(Long id, String name) {
        CustomerEntity c = new CustomerEntity();
        c.setId(id);
        c.setName(name);
        return c;
    }

    private VendorEntity vend(Long id, String name) {
        VendorEntity v = new VendorEntity();
        v.setId(id);
        v.setName(name);
        return v;
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
    @DisplayName("场景1_会计恒等式成立（opening+created−applied−reversed==closing，合计==Σclosing）")
    void balanceIdentity_holds() {
        mockPeriodExists();
        // 预收客户 1001：当期新增预收单 30000（CONFIRMED，已抵扣 20000 剩余未结 10000）
        when(prepaymentMapper.selectList(any())).thenReturn(
                List.of(prepay(1L, 1001L, null, ArapStatus.APPLIED, "30000", "10000", "202609", null)));
        // 预收冲应收结算单 YS-（当期 20000）
        when(settlementMapper.selectList(any())).thenReturn(
                List.of(settle(1L, "YS-202609-AAAA", "202609", ArapStatus.CONFIRMED, "CUSTOMER", 1001L, "20000")));
        when(customerMapper.selectBatchIds(any())).thenReturn(List.of(cust(1001L, "客户A")));

        PrepaymentBalanceSummaryVO vo = service.getBalanceSummary("202609", "PRE_RECEIPT", null);

        assertTrue(vo.consistent(), "恒等式应成立 → consistent=true");
        assertEquals(1, vo.preReceipts().size());
        PrepaymentBalancePartyVO row = vo.preReceipts().get(0);
        assertEquals(1001L, row.partyId());
        assertEquals("客户A", row.partyName());
        // closing = unsettled = 10000；created = 30000；applied = 20000；reversed = 0
        assertMoney(new BigDecimal("10000"), row.closingUnsettled());
        assertMoney(new BigDecimal("30000"), row.currentCreated());
        assertMoney(new BigDecimal("20000"), row.currentApplied());
        assertMoney(BigDecimal.ZERO, row.currentReversed());
        // opening = closing − created + applied + reversed = 10000 − 30000 + 20000 + 0 = 0
        assertMoney(BigDecimal.ZERO, row.openingUnsettled());
        // 恒等式：opening + created − applied − reversed == closing
        assertMoney(row.closingUnsettled(),
                row.openingUnsettled().add(row.currentCreated())
                        .subtract(row.currentApplied()).subtract(row.currentReversed()));
        // 合计 == Σ closing
        assertMoney(new BigDecimal("10000"), vo.preReceiptTotal());
        assertTrue(vo.prePayments().isEmpty());
        assertEquals("PRE_RECEIPT", vo.partyType());
    }

    // ===== 场景 2：已冲销不计期末，计入 currentReversed =====

    @Test
    @DisplayName("场景2_REVERSED不计closing（但计入currentReversed）")
    void reversed_excludedFromClosing() {
        mockPeriodExists();
        // 客户 1001：一张 CONFIRMED 未结 5000 + 一张 REVERSED 5000
        when(prepaymentMapper.selectList(any())).thenReturn(
                List.of(
                        prepay(1L, 1001L, null, ArapStatus.CONFIRMED, "5000", "5000", "202609", null),
                        prepay(2L, 1001L, null, ArapStatus.REVERSED, "5000", "0", "202609", null)));
        when(settlementMapper.selectList(any())).thenReturn(List.of());
        when(customerMapper.selectBatchIds(any())).thenReturn(List.of(cust(1001L, "客户A")));

        PrepaymentBalanceSummaryVO vo = service.getBalanceSummary("202609", "PRE_RECEIPT", null);

        PrepaymentBalancePartyVO row = vo.preReceipts().get(0);
        // closing 只含 CONFIRMED 的 5000，REVERSED 的 5000 不计
        assertMoney(new BigDecimal("5000"), row.closingUnsettled());
        // currentCreated 含两张 = 10000；currentReversed = 5000
        assertMoney(new BigDecimal("10000"), row.currentCreated());
        assertMoney(new BigDecimal("5000"), row.currentReversed());
        // opening = 5000 − 10000 + 0 + 5000 = 0
        assertMoney(BigDecimal.ZERO, row.openingUnsettled());
        assertTrue(vo.consistent());
    }

    // ===== 场景 3：期初推导 =====

    @Test
    @DisplayName("场景3_期初推导（opening = closing − created + applied + reversed）")
    void openingDerivation() {
        mockPeriodExists();
        // 客户 1001：预收单跨期——202608 单 10000 未结 6000 + 202609 当期抵扣 2000
        when(prepaymentMapper.selectList(any())).thenReturn(
                List.of(prepay(1L, 1001L, null, ArapStatus.CONFIRMED, "10000", "6000", "202608", null)));
        when(settlementMapper.selectList(any())).thenReturn(
                List.of(settle(1L, "YS-202609-BBB", "202609", ArapStatus.CONFIRMED, "CUSTOMER", 1001L, "2000")));
        when(customerMapper.selectBatchIds(any())).thenReturn(List.of(cust(1001L, "客户A")));

        PrepaymentBalanceSummaryVO vo = service.getBalanceSummary("202609", "PRE_RECEIPT", null);

        PrepaymentBalancePartyVO row = vo.preReceipts().get(0);
        assertMoney(new BigDecimal("6000"), row.closingUnsettled());   // 期末未结
        assertMoney(BigDecimal.ZERO, row.currentCreated());             // 202608 单非本期新增
        assertMoney(new BigDecimal("2000"), row.currentApplied());      // 本期抵扣
        // opening = 6000 − 0 + 2000 + 0 = 8000
        assertMoney(new BigDecimal("8000"), row.openingUnsettled());
        assertTrue(vo.consistent());
    }

    // ===== 场景 4：期间必填守卫 =====

    @Test
    @DisplayName("场景4_period缺失或非6位数字 → BusinessException 400")
    void periodRequired_throws400() {
        BusinessException nullPeriod = assertThrows(BusinessException.class,
                () -> service.getBalanceSummary(null, null, null));
        assertEquals(400, nullPeriod.getCode());
        assertTrue(nullPeriod.getMessage().contains("期间"));

        BusinessException badFormat = assertThrows(BusinessException.class,
                () -> service.getBalanceSummary("2026-09", null, null));
        assertEquals(400, badFormat.getCode());

        // party_type 非法
        BusinessException badParty = assertThrows(BusinessException.class,
                () -> service.getBalanceSummary("202609", "FOO", null));
        assertEquals(400, badParty.getCode());
        assertTrue(badParty.getMessage().contains("party_type"));
    }

    // ===== 场景 5：数据权限隔离（服务不手工注入 enterprise_id） =====

    @Test
    @DisplayName("场景5_查询wrapper不含enterprise_id硬编码（数据权限由拦截器注入）")
    void wrapper_noEnterpriseFilter() {
        mockPeriodExists();
        when(prepaymentMapper.selectList(any())).thenReturn(List.of());
        when(settlementMapper.selectList(any())).thenReturn(List.of());

        service.getBalanceSummary("202609", "PRE_RECEIPT", 1001L);

        ArgumentCaptor<LambdaQueryWrapper<PrepaymentEntity>> captor =
                ArgumentCaptor.forClass(LambdaQueryWrapper.class);
        verify(prepaymentMapper, atLeastOnce()).selectList(captor.capture());
        String sql = captor.getValue().getSqlSegment();
        assertTrue(sql.contains("customer_id"), "应含 customer_id 条件: " + sql);
        // partyId 被 MyBatis-Plus 参数化为 #{...MPGENVAL}，断言条件列 + 参数占位（非 enterprise_id 硬编码）
        assertTrue(sql.contains("= #{") || sql.contains("=#{"), "partyId 过滤应落进 SQL（参数化占位）: " + sql);
        assertFalse(sql.contains("enterprise_id"),
                "service 不应手工注入 enterprise_id: " + sql);
    }

    // ===== 场景 6：预收/预付对称不混入 =====

    @Test
    @DisplayName("场景6_预收侧结果不含供应商预付数据（按customerId分组）")
    void preReceiptSide_noVendorData() {
        mockPeriodExists();
        // 预收单带 customerId，无 vendorId；预付款不应混入
        when(prepaymentMapper.selectList(any())).thenReturn(
                List.of(prepay(1L, 1001L, null, ArapStatus.CONFIRMED, "8000", "8000", "202609", null)));
        when(settlementMapper.selectList(any())).thenReturn(List.of());
        when(customerMapper.selectBatchIds(any())).thenReturn(List.of(cust(1001L, "客户A")));

        PrepaymentBalanceSummaryVO vo = service.getBalanceSummary("202609", "PRE_RECEIPT", null);

        // 负向断言：捕获预收查询，确认按 customer_id 过滤（非 vendor_id）
        ArgumentCaptor<LambdaQueryWrapper<PrepaymentEntity>> captor =
                ArgumentCaptor.forClass(LambdaQueryWrapper.class);
        verify(prepaymentMapper, atLeastOnce()).selectList(captor.capture());
        String sql = captor.getValue().getSqlSegment();
        assertTrue(sql.contains("customer_id"), "预收侧应按 customer_id 过滤: " + sql);
        assertFalse(sql.contains("vendor_id"), "预收侧不应查 vendor_id: " + sql);
        assertEquals(1, vo.preReceipts().size());
        assertEquals(1001L, vo.preReceipts().get(0).partyId());
        assertTrue(vo.prePayments().isEmpty(), "指定 PRE_RECEIPT 时预付侧应为空");
    }

    // ===== 附加：结算单前缀隔离，防与 P75 JS/FS 双计 =====

    @Test
    @DisplayName("附加_结算单前缀YS/YF隔离（JS/FS普通核销不计入预收预付currentApplied）")
    void settlementPrefixIsolation() {
        mockPeriodExists();
        when(prepaymentMapper.selectList(any())).thenReturn(List.of());
        // 同时给普通核销 JS 与预付抵扣 YF 两张结算单，只有 YF 应计入
        when(settlementMapper.selectList(any())).thenReturn(List.of(
                settle(1L, "YS-202609-CCC", "202609", ArapStatus.CONFIRMED, "CUSTOMER", 1001L, "3000"),
                settle(2L, "JS-202609-DDD", "202609", ArapStatus.CONFIRMED, "CUSTOMER", 1001L, "7000")));
        when(customerMapper.selectBatchIds(any())).thenReturn(List.of(cust(1001L, "客户A")));

        PrepaymentBalanceSummaryVO vo = service.getBalanceSummary("202609", "PRE_RECEIPT", null);

        // 捕获结算查询，确认前缀条件存在
        ArgumentCaptor<LambdaQueryWrapper<ArapSettlementEntity>> cap =
                ArgumentCaptor.forClass(LambdaQueryWrapper.class);
        verify(settlementMapper, atLeastOnce()).selectList(cap.capture());
        String sql = cap.getValue().getSqlSegment();
        assertTrue(sql.contains("settlement_no"), "结算查询应含 settlement_no 前缀条件: " + sql);

        PrepaymentBalancePartyVO row = vo.preReceipts().get(0);
        // 只有 YS 的 3000 计入 currentApplied（JS 的 7000 被前缀隔离排除）
        assertMoney(new BigDecimal("3000"), row.currentApplied());
    }
}
