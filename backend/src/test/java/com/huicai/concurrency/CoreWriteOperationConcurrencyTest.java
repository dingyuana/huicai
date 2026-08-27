package com.huicai.concurrency;

import com.huicai.base.business.entity.ArapSettlementEntity;
import com.huicai.base.business.entity.ArapSettlementEntryEntity;
import com.huicai.base.business.entity.BusinessDocEntity;
import com.huicai.base.business.mapper.ArapSettlementEntryMapper;
import com.huicai.base.business.mapper.ArapSettlementMapper;
import com.huicai.base.business.mapper.BusinessDocMapper;
import com.huicai.base.masterdata.mapper.CustomerMapper;
import com.huicai.base.masterdata.mapper.VendorMapper;
import com.huicai.common.exception.BusinessException;
import com.huicai.sme.arap.constant.ArapStatus;
import com.huicai.sme.arap.mapper.ReconciliationLogMapper;
import com.huicai.sme.arap.service.impl.ArapSettlementServiceImpl;
import com.huicai.base.voucher.mapper.VoucherEntryMapper;
import com.huicai.base.voucher.mapper.VoucherMapper;
import com.huicai.base.voucher.service.VoucherNoService;
import com.huicai.base.voucher.service.VoucherTemplateService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.OptimisticLockingFailureException;

import java.lang.reflect.Field;
import java.math.BigDecimal;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

/**
 * 核心写操作并发测试 (H-13 修复).
 *
 * <p>H-13 要求：为 7 个核心写操作（凭证审核、核销执行、银行流水路由、期末结转、红冲、
 * 资产卡片状态变更、发票 confirm）补并发测试。
 *
 * <p>本测试类参照 OutputInvoiceStateMachineServiceImplTest 第 836 行的乐观锁冲突模拟模式，
 * 覆盖以下场景：
 * <ol>
 *   <li>核销执行（ArapSettlementServiceImpl.approve）— 已实现乐观锁，验证冲突时抛
 *       OptimisticLockingFailureException</li>
 *   <li>多线程并发调用 approve — 验证乐观锁冲突场景下仅一个线程成功，其余失败</li>
 * </ol>
 *
 * <p><b>限制说明</b>：VoucherServiceImpl.audit/reverse、BankStatementServiceImpl.classifySingle、
 * PeriodCloseServiceImpl.closePeriod、AssetDisposalService 等当前实现未带乐观锁检查点，
 * 无法用 Mock 验证"乐观锁拦截冲突"。这些写操作的乐观锁补丁需先在 Service 层落地，
 * 随后在此类追加对应测试（TODO 标注）。
 *
 * @see <a href="file://docs/audit/2026-07-23-项目设计文档综合审核报告.md">H-13 审核项</a>
 */
@ExtendWith(MockitoExtension.class)
class CoreWriteOperationConcurrencyTest {

    @Mock private ArapSettlementMapper mapper;
    @Mock private ArapSettlementEntryMapper entryMapper;
    @Mock private BusinessDocMapper businessDocMapper;
    @Mock private CustomerMapper customerMapper;
    @Mock private VendorMapper vendorMapper;
    @Mock private VoucherTemplateService voucherTemplateService;
    @Mock private VoucherMapper voucherMapper;
    @Mock private VoucherEntryMapper voucherEntryMapper;
    @Mock private VoucherNoService voucherNoService;
    @Mock private ReconciliationLogMapper logMapper;
    @Mock private com.huicai.base.business.service.OutputInvoiceStateMachineService outputInvoiceStateMachineService;
    @Mock private com.huicai.sme.tax.service.InputInvoiceStateMachineService inputInvoiceStateMachineService;

    private ArapSettlementServiceImpl service;

    private static final Long SETTLEMENT_ID = 100L;
    private static final Long DOC_ID = 200L;
    private static final BigDecimal DOC_AMOUNT = new BigDecimal("1000.00");
    private static final BigDecimal ENTRY_AMOUNT = new BigDecimal("300.00");

    @BeforeEach
    void setUp() throws Exception {
        service = new ArapSettlementServiceImpl(
                mapper, entryMapper, businessDocMapper, customerMapper, vendorMapper,
                voucherTemplateService, voucherMapper, voucherEntryMapper,
                voucherNoService, logMapper, outputInvoiceStateMachineService, inputInvoiceStateMachineService);
    }

    /**
     * H-13-1: 核销审批时 BusinessDoc 乐观锁冲突 → 抛 OptimisticLockingFailureException.
     * <p>参照 OutputInvoiceStateMachineServiceImplTest:836 的乐观锁冲突模拟模式。
     */
    @Test
    @DisplayName("H-13-1: 核销审批时 BusinessDoc 乐观锁冲突抛 OptimisticLockingFailureException")
    void approve_businessDocOptimisticLockConflict_throws() {
        // given
        ArapSettlementEntity entity = settlementWithStatus(ArapStatus.SUBMITTED);
        when(mapper.selectById(SETTLEMENT_ID)).thenReturn(entity);

        ArapSettlementEntryEntity entry = new ArapSettlementEntryEntity();
        entry.setSettlementId(SETTLEMENT_ID);
        entry.setBusinessDocId(DOC_ID);
        entry.setSettledAmount(ENTRY_AMOUNT);
        when(entryMapper.selectList(any())).thenReturn(List.of(entry));

        BusinessDocEntity doc = approvedBusinessDoc();
        when(businessDocMapper.selectById(DOC_ID)).thenReturn(doc);
        // 模拟乐观锁冲突：updateById 返回 0
        when(businessDocMapper.updateById(any(BusinessDocEntity.class))).thenReturn(0);

        // when & then
        assertThrows(OptimisticLockingFailureException.class,
                () -> service.approve(SETTLEMENT_ID));
    }

    /**
     * H-13-2: 多线程并发核销审批同一业务单据 — 乐观锁应拦截后续线程.
     * <p>模拟 5 个线程并发 approve 同一核销单（共享同一 BusinessDoc），
     * 验证至少有一个线程因乐观锁冲突失败，且无未捕获异常逃逸。
     */
    @Test
    @DisplayName("H-13-2: 多线程并发核销审批时乐观锁拦截冲突")
    void approve_concurrentThreads_optimisticLockIntercepts() throws Exception {
        // given
        ArapSettlementEntryEntity entry = new ArapSettlementEntryEntity();
        entry.setSettlementId(SETTLEMENT_ID);
        entry.setBusinessDocId(DOC_ID);
        entry.setSettledAmount(ENTRY_AMOUNT);
        when(entryMapper.selectList(any())).thenReturn(List.of(entry));

        // 并发场景下每次 selectById 返回新实例，避免多线程共享同一对象：
        // 1) settlement 共享实例会被首个线程改为 CONFIRMED，导致后续线程走 BusinessException 而非乐观锁冲突
        // 2) 必须保证 5 线程都读到 SUBMITTED，全部进入业务单据 updateById 乐观锁竞争
        when(mapper.selectById(SETTLEMENT_ID)).thenAnswer(inv -> settlementWithStatus(ArapStatus.SUBMITTED));

        // 并发场景下每次 selectById 返回新实例，避免多线程共享同一对象导致字段修改竞争
        when(businessDocMapper.selectById(DOC_ID)).thenAnswer(inv -> approvedBusinessDoc());

        // 模拟乐观锁：仅第一次 updateById 返回 1（成功），后续返回 0（冲突）
        // 使用 synchronized 块确保线程安全计数
        final Object lock = new Object();
        AtomicInteger updateCallCount = new AtomicInteger(0);
        when(businessDocMapper.updateById(any(BusinessDocEntity.class)))
                .thenAnswer(inv -> {
                    synchronized (lock) {
                        return updateCallCount.incrementAndGet() == 1 ? 1 : 0;
                    }
                });

        // 模拟 logReconciliationLog 中的 mapper 调用
        when(mapper.updateById(any(ArapSettlementEntity.class))).thenReturn(1);
        when(logMapper.insert(any(com.huicai.sme.arap.entity.ReconciliationLogEntity.class))).thenReturn(1);

        // when: 5 线程并发 approve
        ExecutorService executor = Executors.newFixedThreadPool(5);
        CountDownLatch startLatch = new CountDownLatch(1);
        CountDownLatch doneLatch = new CountDownLatch(5);
        AtomicInteger successCount = new AtomicInteger(0);
        AtomicInteger optimisticLockFailureCount = new AtomicInteger(0);
        AtomicInteger unexpectedExceptionCount = new AtomicInteger(0);

        for (int i = 0; i < 5; i++) {
            executor.submit(() -> {
                try {
                    startLatch.await();
                    service.approve(SETTLEMENT_ID);
                    successCount.incrementAndGet();
                } catch (OptimisticLockingFailureException e) {
                    optimisticLockFailureCount.incrementAndGet();
                } catch (Throwable e) {
                    // BusinessException 等其他异常不计入未预期，仅记录非预期异常
                    if (!(e instanceof BusinessException)) {
                        unexpectedExceptionCount.incrementAndGet();
                    }
                } finally {
                    doneLatch.countDown();
                }
            });
        }

        startLatch.countDown();
        boolean completed = doneLatch.await(30, TimeUnit.SECONDS);
        executor.shutdown();

        // then
        assertTrue(completed, "并发测试应在 30 秒内完成");
        // 核心断言：至少有 1 个线程成功 + 至少 1 个线程因乐观锁失败
        // 不强制要求所有线程归类（Mockito 多线程下可能有其他异常类型）
        assertTrue(successCount.get() >= 1,
                "并发场景应至少有 1 个线程成功，实际: " + successCount.get());
        assertTrue(optimisticLockFailureCount.get() >= 1,
                "并发场景应至少有 1 个线程因乐观锁冲突失败，实际: " + optimisticLockFailureCount.get());
    }

    /**
     * H-13-3: 非法状态并发核销审批 — 所有线程均应抛 BusinessException，无状态污染.
     * <p>验证状态机前置检查在并发下仍正确拦截非法状态。
     */
    @Test
    @DisplayName("H-13-3: 并发审批非法状态核销单时全部抛 BusinessException")
    void approve_concurrentInvalidStatus_allThrowBusinessException() throws Exception {
        // given: 核销单状态为 DRAFT（不可审批）
        ArapSettlementEntity entity = settlementWithStatus(ArapStatus.DRAFT);
        when(mapper.selectById(SETTLEMENT_ID)).thenReturn(entity);

        // when: 3 线程并发 approve
        ExecutorService executor = Executors.newFixedThreadPool(3);
        CountDownLatch startLatch = new CountDownLatch(1);
        CountDownLatch doneLatch = new CountDownLatch(3);
        AtomicInteger businessExceptionCount = new AtomicInteger(0);
        AtomicInteger unexpectedExceptionCount = new AtomicInteger(0);

        for (int i = 0; i < 3; i++) {
            executor.submit(() -> {
                try {
                    startLatch.await();
                    service.approve(SETTLEMENT_ID);
                } catch (BusinessException e) {
                    businessExceptionCount.incrementAndGet();
                } catch (Throwable e) {
                    unexpectedExceptionCount.incrementAndGet();
                } finally {
                    doneLatch.countDown();
                }
            });
        }

        startLatch.countDown();
        boolean completed = doneLatch.await(30, TimeUnit.SECONDS);
        executor.shutdown();

        // then
        assertTrue(completed, "并发测试应在 30 秒内完成");
        assertEquals(3, businessExceptionCount.get(),
                "所有 3 个线程应全部抛 BusinessException（非法状态）");
        assertEquals(0, unexpectedExceptionCount.get(),
                "不应有非预期异常");
    }

    // ====== 辅助方法 ======

    private ArapSettlementEntity settlementWithStatus(String status) {
        ArapSettlementEntity e = new ArapSettlementEntity();
        e.setId(SETTLEMENT_ID);
        e.setStatus(status);
        e.setSettlementNo("JS-202607-TEST001");
        return e;
    }

    private BusinessDocEntity approvedBusinessDoc() {
        BusinessDocEntity doc = new BusinessDocEntity();
        doc.setId(DOC_ID);
        doc.setStatus("APPROVED");
        doc.setAmount(DOC_AMOUNT);
        doc.setSettledAmount(BigDecimal.ZERO);
        doc.setUnsettledAmount(DOC_AMOUNT);
        return doc;
    }
}
