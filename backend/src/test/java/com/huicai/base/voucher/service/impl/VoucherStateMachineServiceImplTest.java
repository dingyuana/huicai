package com.huicai.base.voucher.service.impl;

import com.huicai.common.exception.BusinessException;
import com.huicai.base.voucher.entity.VoucherEntity;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * VoucherStateMachineService 单元测试
 * 2026-06-22 P22 commit 3/3
 * 覆盖 4 状态流转检查 (assertSubmittable/Auditable/Postable/Reversible) + isReversed
 */
class VoucherStateMachineServiceImplTest {

    private final VoucherStateMachineServiceImpl service = new VoucherStateMachineServiceImpl();

    private VoucherEntity voucherWithStatus(String status) {
        VoucherEntity e = new VoucherEntity();
        e.setId(1L);
        e.setStatus(status);
        return e;
    }

    // ====== assertSubmittable (DRAFT → SUBMITTED) ======

    @Test
    @DisplayName("assertSubmittable_DRAFT_通过")
    void assertSubmittable_draft_passes() {
        assertDoesNotThrow(() -> service.assertSubmittable(voucherWithStatus("DRAFT")));
    }

    @Test
    @DisplayName("assertSubmittable_SUBMITTED_抛异常")
    void assertSubmittable_submitted_throws() {
        BusinessException ex = assertThrows(BusinessException.class,
                () -> service.assertSubmittable(voucherWithStatus("SUBMITTED")));
        assertTrue(ex.getMessage().contains("不可提交"));
    }

    @Test
    @DisplayName("assertSubmittable_AUDITED_抛异常")
    void assertSubmittable_audited_throws() {
        assertThrows(BusinessException.class,
                () -> service.assertSubmittable(voucherWithStatus("AUDITED")));
    }

    // ====== assertAuditable (SUBMITTED → AUDITED) ======

    @Test
    @DisplayName("assertAuditable_SUBMITTED_通过")
    void assertAuditable_submitted_passes() {
        assertDoesNotThrow(() -> service.assertAuditable(voucherWithStatus("SUBMITTED")));
    }

    @Test
    @DisplayName("assertAuditable_DRAFT_抛异常")
    void assertAuditable_draft_throws() {
        BusinessException ex = assertThrows(BusinessException.class,
                () -> service.assertAuditable(voucherWithStatus("DRAFT")));
        assertTrue(ex.getMessage().contains("不可审核"));
    }

    @Test
    @DisplayName("assertAuditable_POSTED_抛异常")
    void assertAuditable_posted_throws() {
        assertThrows(BusinessException.class,
                () -> service.assertAuditable(voucherWithStatus("POSTED")));
    }

    // ====== assertPostable (AUDITED → POSTED) ======

    @Test
    @DisplayName("assertPostable_AUDITED_通过")
    void assertPostable_audited_passes() {
        assertDoesNotThrow(() -> service.assertPostable(voucherWithStatus("AUDITED")));
    }

    @Test
    @DisplayName("assertPostable_SUBMITTED_抛异常")
    void assertPostable_submitted_throws() {
        BusinessException ex = assertThrows(BusinessException.class,
                () -> service.assertPostable(voucherWithStatus("SUBMITTED")));
        assertTrue(ex.getMessage().contains("不可记账"));
    }

    // ====== assertReversible (POSTED → 红字) ======

    @Test
    @DisplayName("assertReversible_POSTED_通过")
    void assertReversible_posted_passes() {
        assertDoesNotThrow(() -> service.assertReversible(voucherWithStatus("POSTED")));
    }

    @Test
    @DisplayName("assertReversible_DRAFT_抛异常")
    void assertReversible_draft_throws() {
        BusinessException ex = assertThrows(BusinessException.class,
                () -> service.assertReversible(voucherWithStatus("DRAFT")));
        assertTrue(ex.getMessage().contains("不可红冲"));
    }

    // ====== isReversed (status=POSTED + reversedFrom 非空) ======

    @Test
    @DisplayName("isReversed_reversedFrom_null_返回false")
    void isReversed_reversedFromNull_false() {
        VoucherEntity e = voucherWithStatus("POSTED");
        e.setReversedFrom(null);
        assertFalse(service.isReversed(e));
    }

    @Test
    @DisplayName("isReversed_reversedFrom_非空_返回true")
    void isReversed_reversedFromNonNull_true() {
        VoucherEntity e = voucherWithStatus("POSTED");
        e.setReversedFrom(100L);
        assertTrue(service.isReversed(e));
    }

    // ====== H-12 负向断言补充：多状态非法值覆盖，确保"不该做的没做" ======

    @Test
    @DisplayName("assertSubmittable_CLOSED_抛异常_已结账不可提交")
    void assertSubmittable_closed_throws() {
        assertThrows(BusinessException.class,
                () -> service.assertSubmittable(voucherWithStatus("CLOSED")));
    }

    @Test
    @DisplayName("assertAuditable_CLOSED_抛异常_已结账不可审核")
    void assertAuditable_closed_throws() {
        assertThrows(BusinessException.class,
                () -> service.assertAuditable(voucherWithStatus("CLOSED")));
    }

    @Test
    @DisplayName("assertPostable_AUDITED_消息包含不可记账")
    void assertPostable_audited_messageContains() {
        BusinessException ex = assertThrows(BusinessException.class,
                () -> service.assertPostable(voucherWithStatus("DRAFT")));
        assertTrue(ex.getMessage().contains("不可记账"));
    }

    @Test
    @DisplayName("assertPostable_CLOSED_抛异常_已结账不可记账")
    void assertPostable_closed_throws() {
        assertThrows(BusinessException.class,
                () -> service.assertPostable(voucherWithStatus("CLOSED")));
    }

    @Test
    @DisplayName("assertReversible_CLOSED_通过_已结账可红冲")
    void assertReversible_closed_passes() {
        assertDoesNotThrow(() -> service.assertReversible(voucherWithStatus("CLOSED")));
    }

    @Test
    @DisplayName("assertReversible_SUBMITTED_抛异常_已提交不可红冲")
    void assertReversible_submitted_throws() {
        assertThrows(BusinessException.class,
                () -> service.assertReversible(voucherWithStatus("SUBMITTED")));
    }

    @Test
    @DisplayName("isReversed_DRAFT_reversedFrom非空_仍返回true_因isReversed仅检查reversedFrom")
    void isReversed_draft_reversedFromNonNull_stillTrue() {
        // isReversed 仅检查 reversedFrom 非空，不校验 status
        // 此测试固化当前实现契约：状态字段不参与 isReversed 判定
        VoucherEntity e = voucherWithStatus("DRAFT");
        e.setReversedFrom(100L);
        assertTrue(service.isReversed(e));
    }

    @Test
    @DisplayName("isReversed_POSTED_reversedFrom为null_返回false")
    void isReversed_posted_reversedFromNull_false() {
        VoucherEntity e = voucherWithStatus("POSTED");
        e.setReversedFrom(null);
        assertFalse(service.isReversed(e));
    }
}
