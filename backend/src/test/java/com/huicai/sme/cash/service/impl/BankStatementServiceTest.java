package com.huicai.sme.cash.service.impl;

import com.huicai.common.exception.BusinessException;
import com.huicai.common.test.StateMachineTestHelper;
import com.huicai.sme.arap.service.impl.AutoGenerationService;
import com.huicai.sme.cash.entity.BankStatementEntity;
import com.huicai.sme.cash.mapper.BankJournalMapper;
import com.huicai.sme.cash.mapper.BankStatementMapper;
import com.huicai.sme.cash.service.ClassificationRuleService;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.isA;
import static org.mockito.Mockito.*;

import java.io.Serializable;

/**
 * 银行流水对账单状态机测试.
 *
 * <p>每方法覆盖：
 * <ul>
 *   <li>正向断言：后置状态正确、写入字段正确、必要 side-effect 被调用</li>
 *   <li>负向断言：不存在、前置状态不符、不应有的副作用未发生</li>
 * </ul>
 *
 * @see <a href="file://docs/specs/P23-bank-statement-state-machine.md">P23 SPEC</a>
 */
@ExtendWith(MockitoExtension.class)
class BankStatementServiceTest {

    @Mock private BankStatementMapper statementMapper;
    @Mock private BankJournalMapper journalMapper;
    @Mock private ClassificationRuleService classificationRuleService;
    @Mock private FallbackHeuristicService fallbackHeuristic;
    @Mock private AutoGenerationService autoGenerationService;

    @InjectMocks private BankStatementServiceImpl service;

    // ===================== helpers =====================

    private BankStatementEntity stub(Long id, String classification) {
        BankStatementEntity s = new BankStatementEntity();
        s.setId(id);
        s.setAccountId(1L);
        s.setClassification(classification);
        return s;
    }

    private BankStatementEntity stubWithStatus(Long id, String classification, String reviewStatus) {
        BankStatementEntity s = stub(id, classification);
        s.setReviewStatus(reviewStatus);
        return s;
    }

    // ==================== review ====================

    @Nested
    class ReviewTest {

        @Test
        void review_已分类_标记CONFIRMED() {
            // review() 只做状态审核确认 → CONFIRMED, 不再 autoGenerate; 制证由 generateVoucher() 独立触发
            BankStatementEntity stmt = stub(1L, "bank_interest_fee");
            when(statementMapper.selectById(1L)).thenReturn(stmt);
            when(statementMapper.updateById(any(BankStatementEntity.class))).thenReturn(1);

            BankStatementEntity result = service.review(1L, 1L);

            assertNotNull(result);
            assertEquals("CONFIRMED", result.getReviewStatus());
            assertEquals(1L, result.getReviewedBy());
            assertNotNull(result.getReviewedAt());
            verify(autoGenerationService, never()).autoGenerateInNewTx(anyLong(), anyLong());
            verify(statementMapper).updateById(any(BankStatementEntity.class));
        }

        @Test
        void review_从PENDING_标记CONFIRMED() {
            BankStatementEntity stmt = stubWithStatus(1L, "bank_interest_fee", "PENDING");
            when(statementMapper.selectById(1L)).thenReturn(stmt);
            when(statementMapper.updateById(any(BankStatementEntity.class))).thenReturn(1);

            BankStatementEntity result = service.review(1L, 1L);

            assertEquals("CONFIRMED", result.getReviewStatus());
            verify(autoGenerationService, never()).autoGenerateInNewTx(anyLong(), anyLong());
        }

        @Test
        void review_从RECLASSIFIED_标记CONFIRMED() {
            BankStatementEntity stmt = stubWithStatus(1L, "bank_interest_fee", "RECLASSIFIED");
            when(statementMapper.selectById(1L)).thenReturn(stmt);
            when(statementMapper.updateById(any(BankStatementEntity.class))).thenReturn(1);

            BankStatementEntity result = service.review(1L, 1L);

            assertEquals("CONFIRMED", result.getReviewStatus());
            verify(autoGenerationService, never()).autoGenerateInNewTx(anyLong(), anyLong());
        }

        @Test
        void review_不存在_throwNotFound() {
            when(statementMapper.selectById(99L)).thenReturn(null);
            BusinessException ex = assertThrows(BusinessException.class, () -> service.review(99L, 1L));
            assertTrue(ex.getMessage().contains("不存在"));
            verify(statementMapper, never()).updateById(any(BankStatementEntity.class));
        }

        @Test
        void review_未分类_throwBadRequest() {
            when(statementMapper.selectById(1L)).thenReturn(stub(1L, null));
            BusinessException ex = assertThrows(BusinessException.class, () -> service.review(1L, 1L));
            assertTrue(ex.getMessage().contains("尚未分类"));
            verify(statementMapper, never()).updateById(any(BankStatementEntity.class));
        }

        @Test
        void review_CONFIRMED状态_throwBadRequest() {
            BankStatementEntity stmt = stubWithStatus(1L, "bank_interest_fee", "CONFIRMED");
            when(statementMapper.selectById(1L)).thenReturn(stmt);
            BusinessException ex = assertThrows(BusinessException.class, () -> service.review(1L, 1L));
            assertTrue(ex.getMessage().contains("无法复审"));
            verify(statementMapper, never()).updateById(any(BankStatementEntity.class));
        }

        @Test
        void review_approved状态_throwBadRequest() {
            BankStatementEntity stmt = stubWithStatus(1L, "bank_interest_fee", "approved");
            when(statementMapper.selectById(1L)).thenReturn(stmt);
            BusinessException ex = assertThrows(BusinessException.class, () -> service.review(1L, 1L));
            assertTrue(ex.getMessage().contains("无法复审"));
            verify(statementMapper, never()).updateById(any(BankStatementEntity.class));
        }

        @Test
        void review_写入字段验证() {
            BankStatementEntity stmt = stub(1L, "bank_interest_fee");
            when(statementMapper.selectById(1L)).thenReturn(stmt);
            when(statementMapper.updateById(any(BankStatementEntity.class))).thenReturn(1);

            service.review(1L, 42L);

            ArgumentCaptor<BankStatementEntity> captor = ArgumentCaptor.forClass(BankStatementEntity.class);
            verify(statementMapper).updateById(captor.capture());
            BankStatementEntity captured = captor.getValue();
            assertEquals("CONFIRMED", captured.getReviewStatus());
            assertEquals(42L, captured.getReviewedBy());
            assertNotNull(captured.getReviewedAt());
        }
    }

    // ==================== batchReview ====================

    @Nested
    class BatchReviewTest {

        @Test
        void batchReview_空列表_throwBadRequest() {
            BusinessException ex = assertThrows(BusinessException.class, () -> service.batchReview(List.of(), 1L));
            assertTrue(ex.getMessage().contains("为空"));
        }

        @Test
        void batchReview_null_throwBadRequest() {
            BusinessException ex = assertThrows(BusinessException.class, () -> service.batchReview(null, 1L));
            assertTrue(ex.getMessage().contains("为空"));
        }

        @Test
        void batchReview_混合成功失败_返回成功数() {
            when(statementMapper.selectById(1L)).thenReturn(stub(1L, "bank_interest_fee"));
            when(statementMapper.selectById(2L)).thenReturn(stub(2L, null));
            when(statementMapper.selectById(3L)).thenReturn(null);
            when(statementMapper.updateById(any(BankStatementEntity.class))).thenReturn(1);

            int confirmed = service.batchReview(List.of(1L, 2L, 3L), 1L);

            assertEquals(1, confirmed);
            verify(statementMapper, times(1)).updateById(any(BankStatementEntity.class));
        }
    }

    // ==================== audit ====================

    @Nested
    class AuditTest {

        @Test
        void audit_CONFIRMED_自动制证_标记voucher_generated() {
            // bank_interest_fee → classifyType() → "A" → voucher_generated
            BankStatementEntity confirmed = stubWithStatus(1L, "bank_interest_fee", "CONFIRMED");
            BankStatementEntity afterGen = stubWithStatus(1L, "bank_interest_fee", "voucher_generated");
            // 第一次 select 取原数据，autoGenerate 后 re-select 取生成后数据
            when(statementMapper.selectById(1L)).thenReturn(confirmed, afterGen);
            when(statementMapper.updateById(any(BankStatementEntity.class))).thenReturn(1);
            when(autoGenerationService.autoGenerateInNewTx(1L, 1L)).thenReturn(true);

            BankStatementEntity result = service.audit(1L, 1L);

            assertEquals("voucher_generated", result.getReviewStatus());
            verify(autoGenerationService).autoGenerateInNewTx(1L, 1L);
            verify(statementMapper).updateById(any(BankStatementEntity.class));
        }

        @Test
        void audit_不存在_throwNotFound() {
            when(statementMapper.selectById(99L)).thenReturn(null);
            BusinessException ex = assertThrows(BusinessException.class, () -> service.audit(99L, 1L));
            assertTrue(ex.getMessage().contains("不存在"));
            verify(statementMapper, never()).updateById(any(BankStatementEntity.class));
        }

        @Test
        void audit_非CONFIRMED状态_throwBadRequest() {
            for (String badStatus : new String[]{"PENDING", "voucher_generated", "payment_created", "approved", null}) {
                BankStatementEntity stmt = stubWithStatus(1L, "bank_interest_fee", badStatus);
                when(statementMapper.selectById(1L)).thenReturn(stmt);
                BusinessException ex = assertThrows(BusinessException.class, () -> service.audit(1L, 1L));
                assertTrue(ex.getMessage().contains("无法审核"),
                        () -> "期望「无法审核」，实际: " + ex.getMessage() + " for status=" + badStatus);
                verify(statementMapper, never()).updateById(any(BankStatementEntity.class));
                reset(statementMapper);
            }
        }

        @Test
        void batchAudit_混合成功失败_返回成功数() {
            BankStatementEntity confirmed = stubWithStatus(1L, "bank_interest_fee", "CONFIRMED");
            when(statementMapper.selectById(1L)).thenReturn(confirmed, confirmed);  // audit 内部会 re-select
            when(statementMapper.selectById(2L)).thenReturn(stubWithStatus(2L, "bank_interest_fee", "PENDING"));
            when(statementMapper.selectById(3L)).thenReturn(null);
            when(statementMapper.updateById(any(BankStatementEntity.class))).thenReturn(1);
            when(autoGenerationService.autoGenerateInNewTx(1L, 1L)).thenReturn(true);

            int audited = service.batchAudit(List.of(1L, 2L, 3L), 1L);

            assertEquals(1, audited);
            verify(statementMapper, times(1)).updateById(any(BankStatementEntity.class));
        }
    }

    // ==================== generateVoucher ====================

    @Nested
    class GenerateVoucherTest {

        @Test
        void generateVoucher_A类_标记voucher_generated() {
            // bank_interest_fee → classifyType() → "A" → voucher_generated
            // 作为恢复重试入口，允许从 CONFIRMED 状态触发
            BankStatementEntity stmt = stubWithStatus(1L, "bank_interest_fee", "CONFIRMED");
            when(statementMapper.selectById(1L)).thenReturn(stmt, stmt);
            when(statementMapper.updateById(any(BankStatementEntity.class))).thenReturn(1);
            when(autoGenerationService.autoGenerateInNewTx(1L, 1L)).thenReturn(true);

            BankStatementEntity result = service.generateVoucher(1L, 1L);

            assertEquals("voucher_generated", result.getReviewStatus());
            verify(autoGenerationService).autoGenerateInNewTx(1L, 1L);
        }

        @Test
        void generateVoucher_B类_标记payment_created() {
            // business_payment → classifyType() → "B" → payment_created
            // 作为恢复重试入口，允许从 CONFIRMED 状态触发
            BankStatementEntity stmt = stubWithStatus(1L, "business_payment", "CONFIRMED");
            when(statementMapper.selectById(1L)).thenReturn(stmt, stmt);
            when(statementMapper.updateById(any(BankStatementEntity.class))).thenReturn(1);
            when(autoGenerationService.autoGenerateInNewTx(1L, 1L)).thenReturn(true);

            BankStatementEntity result = service.generateVoucher(1L, 1L);

            assertEquals("payment_created", result.getReviewStatus());
            verify(autoGenerationService).autoGenerateInNewTx(1L, 1L);
        }

        @Test
        void generateVoucher_autoGenerate失败_throwBadRequest() {
            BankStatementEntity stmt = stubWithStatus(1L, "bank_interest_fee", "CONFIRMED");
            when(statementMapper.selectById(1L)).thenReturn(stmt);
            when(autoGenerationService.autoGenerateInNewTx(1L, 1L)).thenReturn(false);

            BusinessException ex = assertThrows(BusinessException.class, () -> service.generateVoucher(1L, 1L));
            assertTrue(ex.getMessage().contains("自动制证失败"));
        }

        @Test
        void generateVoucher_不存在_throwNotFound() {
            when(statementMapper.selectById(99L)).thenReturn(null);
            BusinessException ex = assertThrows(BusinessException.class, () -> service.generateVoucher(99L, 1L));
            assertTrue(ex.getMessage().contains("不存在"));
            verify(autoGenerationService, never()).autoGenerateInNewTx(anyLong(), anyLong());
        }

        @Test
        void generateVoucher_非CONFIRMED非AUDITED状态_throwBadRequest() {
            // CONFIRMED 允许（重试入口），AUDITED 允许（旧数据兼容），其他状态拒绝
            for (String badStatus : new String[]{"PENDING", "voucher_generated", "payment_created", "approved", null}) {
                BankStatementEntity stmt = stubWithStatus(1L, "bank_interest_fee", badStatus);
                when(statementMapper.selectById(1L)).thenReturn(stmt);
                BusinessException ex = assertThrows(BusinessException.class, () -> service.generateVoucher(1L, 1L));
                assertTrue(ex.getMessage().contains("无法生成凭证"),
                        () -> "期望「无法生成凭证」，实际: " + ex.getMessage() + " for status=" + badStatus);
                verify(autoGenerationService, never()).autoGenerateInNewTx(anyLong(), anyLong());
                reset(statementMapper);
            }
        }

        @Test
        void batchGenerateVouchers_混合成功失败_返回成功数() {
            // 从 CONFIRMED 生成（兼容旧流程的重试入口）
            BankStatementEntity ok = stubWithStatus(1L, "bank_interest_fee", "CONFIRMED");
            BankStatementEntity bad = stubWithStatus(2L, "bank_interest_fee", "PENDING");
            when(statementMapper.selectById(1L)).thenReturn(ok, ok);
            when(statementMapper.selectById(2L)).thenReturn(bad);
            when(statementMapper.updateById(any(BankStatementEntity.class))).thenReturn(1);
            when(autoGenerationService.autoGenerateInNewTx(1L, 1L)).thenReturn(true);

            int generated = service.batchGenerateVouchers(List.of(1L, 2L), 1L);

            assertEquals(1, generated);
        }
    }

    // ==================== approve ====================

    @Nested
    class ApproveTest {

        @Test
        void approve_voucher_generated_标记approved() {
            BankStatementEntity stmt = stubWithStatus(1L, "bank_interest_fee", "voucher_generated");
            when(statementMapper.selectById(1L)).thenReturn(stmt);
            when(statementMapper.updateById(any(BankStatementEntity.class))).thenReturn(1);

            service.approve(1L);

            assertEquals("approved", stmt.getReviewStatus());
            // 负向断言：核准阶段不应制证
            verify(autoGenerationService, never()).autoGenerateInNewTx(anyLong(), anyLong());
            verify(statementMapper).updateById(any(BankStatementEntity.class));
        }

        @Test
        void approve_payment_created_标记approved() {
            BankStatementEntity stmt = stubWithStatus(1L, "business_payment", "payment_created");
            when(statementMapper.selectById(1L)).thenReturn(stmt);
            when(statementMapper.updateById(any(BankStatementEntity.class))).thenReturn(1);

            service.approve(1L);

            assertEquals("approved", stmt.getReviewStatus());
            verify(autoGenerationService, never()).autoGenerateInNewTx(anyLong(), anyLong());
            verify(statementMapper).updateById(any(BankStatementEntity.class));
        }

        @Test
        void approve_不存在_throwNotFound() {
            when(statementMapper.selectById(99L)).thenReturn(null);
            BusinessException ex = assertThrows(BusinessException.class, () -> service.approve(99L));
            assertTrue(ex.getMessage().contains("不存在"));
            verify(statementMapper, never()).updateById(any(BankStatementEntity.class));
        }

        @Test
        void approve_非终态前置_throwBadRequest() {
            for (String badStatus : new String[]{"CONFIRMED", "AUDITED", "PENDING", "approved", null}) {
                BankStatementEntity stmt = stubWithStatus(1L, "bank_interest_fee", badStatus);
                when(statementMapper.selectById(1L)).thenReturn(stmt);
                BusinessException ex = assertThrows(BusinessException.class, () -> service.approve(1L));
                assertTrue(ex.getMessage().contains("无法核准"),
                        () -> "期望「无法核准」，实际: " + ex.getMessage() + " for status=" + badStatus);
                verify(statementMapper, never()).updateById(any(BankStatementEntity.class));
                reset(statementMapper);
            }
        }
    }

    // ==================== processManual ====================

    @Nested
    class ProcessManualTest {

        @Test
        void processManual_A类_标记voucher_generated() {
            BankStatementEntity stmt = stubWithStatus(1L, "other", "manual_pending");
            when(statementMapper.selectById(1L)).thenReturn(stmt);
            when(statementMapper.updateById(any(BankStatementEntity.class))).thenReturn(1);
            when(autoGenerationService.autoGenerateInNewTx(1L, 1L)).thenReturn(true);

            BankStatementEntity result = service.processManual(1L, "A", null, 1L);

            assertEquals("voucher_generated", result.getReviewStatus());
            assertEquals(1L, result.getReviewedBy());
            assertNotNull(result.getReviewedAt());
            verify(autoGenerationService).autoGenerateInNewTx(1L, 1L);
        }

        @Test
        void processManual_B类_标记payment_created() {
            BankStatementEntity stmt = stubWithStatus(1L, "other", "manual_pending");
            when(statementMapper.selectById(1L)).thenReturn(stmt);
            when(statementMapper.updateById(any(BankStatementEntity.class))).thenReturn(1);
            when(autoGenerationService.autoGenerateInNewTx(1L, 1L)).thenReturn(true);

            BankStatementEntity result = service.processManual(1L, "B", "bank_transfer", 1L);

            assertEquals("payment_created", result.getReviewStatus());
            assertEquals(1L, result.getReviewedBy());
            assertNotNull(result.getReviewedAt());
            verify(autoGenerationService).autoGenerateInNewTx(1L, 1L);
        }

        @Test
        void processManual_autoGenerate失败_保持manual_pending() {
            // processManual 中 autoGenerateInNewTx 返回 false 时不会抛异常，
            // 而是设置状态回 manual_pending，由调用方决定重试
            BankStatementEntity stmt = stubWithStatus(1L, "other", "manual_pending");
            when(statementMapper.selectById(1L)).thenReturn(stmt);
            when(statementMapper.updateById(any(BankStatementEntity.class))).thenReturn(1);
            when(autoGenerationService.autoGenerateInNewTx(1L, 1L)).thenReturn(false);

            BankStatementEntity result = service.processManual(1L, "A", null, 1L);

            assertEquals("manual_pending", result.getReviewStatus());
            verify(autoGenerationService).autoGenerateInNewTx(1L, 1L);
            verify(statementMapper).updateById(any(BankStatementEntity.class));
        }

        @Test
        void processManual_不存在_throwNotFound() {
            when(statementMapper.selectById(99L)).thenReturn(null);
            BusinessException ex = assertThrows(BusinessException.class,
                    () -> service.processManual(99L, "A", null, 1L));
            assertTrue(ex.getMessage().contains("不存在"));
        }

        @Test
        void processManual_非manual_pending_throwBadRequest() {
            for (String badStatus : new String[]{"PENDING", "CONFIRMED", "AUDITED", "voucher_generated", "approved", null}) {
                BankStatementEntity stmt = stubWithStatus(1L, "other", badStatus);
                when(statementMapper.selectById(1L)).thenReturn(stmt);
                BusinessException ex = assertThrows(BusinessException.class,
                        () -> service.processManual(1L, "A", null, 1L));
                assertTrue(ex.getMessage().contains("无法人工处理"),
                        () -> "期望「无法人工处理」，实际: " + ex.getMessage() + " for status=" + badStatus);
                reset(statementMapper);
            }
        }
    }

    // ==================== 全流程 ====================

    @Nested
    class FullFlowTest {

        @Test
        void fullStateTransition_从null到approved() {
            // 1. review: null → CONFIRMED（出纳确认，不制证）
            BankStatementEntity stmt = stub(1L, "bank_interest_fee");
            when(statementMapper.selectById(1L)).thenReturn(stmt);
            when(statementMapper.updateById(any(BankStatementEntity.class))).thenReturn(1);

            BankStatementEntity r1 = service.review(1L, 1L);
            assertEquals("CONFIRMED", r1.getReviewStatus());
            verify(autoGenerationService, never()).autoGenerateInNewTx(anyLong(), anyLong());

            // 2. audit: CONFIRMED → voucher_generated（主管审核，内部自动制证）
            BankStatementEntity confirmed = stubWithStatus(1L, "bank_interest_fee", "CONFIRMED");
            // 第二次 selectById 返回生成后的数据
            BankStatementEntity afterGen = stubWithStatus(1L, "bank_interest_fee", "voucher_generated");
            when(statementMapper.selectById(1L)).thenReturn(confirmed, afterGen);
            when(autoGenerationService.autoGenerateInNewTx(1L, 1L)).thenReturn(true);

            BankStatementEntity r2 = service.audit(1L, 1L);
            assertEquals("voucher_generated", r2.getReviewStatus());
            verify(autoGenerationService).autoGenerateInNewTx(1L, 1L);

            // 3. approve: voucher_generated → approved（主管会计核准过账）
            when(statementMapper.selectById(1L)).thenReturn(r2);

            service.approve(1L);
            assertEquals("approved", r2.getReviewStatus());

            // 最终: review(1) + audit(1) + approve(1) = 3 次 update
            verify(statementMapper, times(3)).updateById(any(BankStatementEntity.class));
        }
    }

    // ==================== deleteStatement ====================

    @Nested
    class DeleteTest {

        @Test
        void deleteStatement_未锁定状态_可删除() {
            for (String okStatus : new String[]{null, "PENDING", "manual_pending"}) {
                BankStatementEntity stmt = stubWithStatus(1L, "bank_interest_fee", okStatus);
                when(statementMapper.selectById(1L)).thenReturn(stmt);
                when(statementMapper.deleteById(1L)).thenReturn(1);

                service.deleteStatement(1L);

                verify(statementMapper).deleteById(1L);
                reset(statementMapper);
            }
        }

        @Test
        void deleteStatement_不存在_throwNotFound() {
            when(statementMapper.selectById(99L)).thenReturn(null);
            BusinessException ex = assertThrows(BusinessException.class, () -> service.deleteStatement(99L));
            assertTrue(ex.getMessage().contains("不存在"));
            verify(statementMapper, never()).deleteById(isA(Serializable.class));
        }

        @Test
        void deleteStatement_CONFIRMED状态_throwBadRequest() {
            for (String lockedStatus : new String[]{"CONFIRMED", "voucher_generated", "payment_created", "approved"}) {
                BankStatementEntity stmt = stubWithStatus(1L, "bank_interest_fee", lockedStatus);
                when(statementMapper.selectById(1L)).thenReturn(stmt);
                BusinessException ex = assertThrows(BusinessException.class, () -> service.deleteStatement(1L));
                assertTrue(ex.getMessage().contains("不允许删除"),
                        () -> "期望「不允许删除」，实际: " + ex.getMessage() + " for status=" + lockedStatus);
                verify(statementMapper, never()).deleteById(isA(Serializable.class));
                reset(statementMapper);
            }
        }
    }

    // ==================== updateClassification ====================

    @Nested
    class UpdateClassificationTest {

        @Test
        void updateClassification_未锁定状态_修改成功() {
            for (String okStatus : new String[]{null, "PENDING", "manual_pending", "RECLASSIFIED"}) {
                BankStatementEntity stmt = stubWithStatus(1L, "old_class", okStatus);
                when(statementMapper.selectById(1L)).thenReturn(stmt);
                when(statementMapper.updateById(any(BankStatementEntity.class))).thenReturn(1);

                BankStatementEntity result = service.updateClassification(1L, "new_class");

                assertEquals("new_class", result.getClassification());
                verify(statementMapper).updateById(any(BankStatementEntity.class));
                reset(statementMapper);
            }
        }

        @Test
        void updateClassification_不存在_throwNotFound() {
            when(statementMapper.selectById(99L)).thenReturn(null);
            BusinessException ex = assertThrows(BusinessException.class, () -> service.updateClassification(99L, "new_class"));
            assertTrue(ex.getMessage().contains("不存在"));
            verify(statementMapper, never()).updateById(any(BankStatementEntity.class));
        }

        @Test
        void updateClassification_CONFIRMED状态_throwBadRequest() {
            for (String lockedStatus : new String[]{"CONFIRMED", "voucher_generated", "payment_created", "approved"}) {
                BankStatementEntity stmt = stubWithStatus(1L, "bank_interest_fee", lockedStatus);
                when(statementMapper.selectById(1L)).thenReturn(stmt);
                BusinessException ex = assertThrows(BusinessException.class, () -> service.updateClassification(1L, "new_class"));
                assertTrue(ex.getMessage().contains("不允许修改分类"),
                        () -> "期望「不允许修改分类」，实际: " + ex.getMessage() + " for status=" + lockedStatus);
                verify(statementMapper, never()).updateById(any(BankStatementEntity.class));
                reset(statementMapper);
            }
        }
    }
}