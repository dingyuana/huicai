package com.huicai.module.tax.service.impl;

import com.huicai.common.exception.BusinessException;
import com.huicai.module.tax.constant.InvoiceStatus;
import com.huicai.module.tax.entity.OutputInvoiceEntity;
import com.huicai.module.tax.mapper.OutputInvoiceMapper;
import com.huicai.module.tax.service.OutputInvoiceStateMachineService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;

/**
 * 销售发票状态机实现.
 *
 * <p>依据 SPEC docs/specs/P21-sales-invoice-state-machine.md §4.1
 * 状态变更通过 BaseMapper.updateById 写入数据库，
 * P24 StatusChangeAspect 自动拦截并写入 t_audit_log。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class OutputInvoiceStateMachineServiceImpl implements OutputInvoiceStateMachineService {

    private final OutputInvoiceMapper invoiceMapper;

    @Override
    @Transactional
    public void submitForReview(Long invoiceId, Long userId) {
        OutputInvoiceEntity entity = getEntity(invoiceId);
        if (!InvoiceStatus.isPendingConfirm(entity.getStatus())) {
            throw BusinessException.badRequest("仅待确认状态可提交审核，当前: " + entity.getStatus());
        }
        entity.setStatus(InvoiceStatus.PENDING_REVIEW);
        entity.setUpdatedBy(userId);
        invoiceMapper.updateById(entity);
        log.info("销售发票提交审核: id={}, userId={}", invoiceId, userId);
    }

    @Override
    @Transactional
    public void confirm(Long invoiceId, Long userId) {
        OutputInvoiceEntity entity = getEntity(invoiceId);
        if (!InvoiceStatus.PENDING_REVIEW.equals(entity.getStatus())) {
            throw BusinessException.badRequest("仅待审核状态可确认，当前: " + entity.getStatus());
        }
        entity.setStatus(InvoiceStatus.CONFIRMED);
        entity.setUpdatedBy(userId);
        invoiceMapper.updateById(entity);
        log.info("销售发票审核通过: id={}, userId={}", invoiceId, userId);
    }

    @Override
    @Transactional
    public void reject(Long invoiceId, Long userId, String reason) {
        if (reason == null || reason.trim().isEmpty()) {
            throw BusinessException.badRequest("驳回必须填写原因");
        }
        OutputInvoiceEntity entity = getEntity(invoiceId);
        if (!InvoiceStatus.PENDING_REVIEW.equals(entity.getStatus())) {
            throw BusinessException.badRequest("仅待审核状态可驳回，当前: " + entity.getStatus());
        }
        entity.setStatus(InvoiceStatus.PENDING_CONFIRM);
        entity.setRemark(reason);
        entity.setUpdatedBy(userId);
        invoiceMapper.updateById(entity);
        log.info("销售发票审核驳回: id={}, userId={}, reason={}", invoiceId, userId, reason);
    }

    @Override
    @Transactional
    public void revertToReview(Long invoiceId, Long userId) {
        OutputInvoiceEntity entity = getEntity(invoiceId);
        if (!InvoiceStatus.isConfirmed(entity.getStatus())) {
            throw BusinessException.badRequest("仅已确认状态可回退到待审核，当前: " + entity.getStatus());
        }
        entity.setStatus(InvoiceStatus.PENDING_REVIEW);
        entity.setUpdatedBy(userId);
        invoiceMapper.updateById(entity);
        log.info("销售发票回退到待审核: id={}, userId={}", invoiceId, userId);
    }

    @Override
    @Transactional
    public void markVouchered(Long invoiceId, Long voucherId, Long userId) {
        OutputInvoiceEntity entity = getEntity(invoiceId);
        if (!InvoiceStatus.isVoucherable(entity.getStatus())) {
            throw BusinessException.badRequest("仅已确认状态可生成凭证，当前: " + entity.getStatus());
        }
        entity.setStatus(InvoiceStatus.VOUCHERED);
        entity.setVoucherId(voucherId);
        entity.setUpdatedBy(userId);
        invoiceMapper.updateById(entity);
        log.info("销售发票已生成凭证: invoiceId={}, voucherId={}", invoiceId, voucherId);
    }

    @Override
    @Transactional
    public void onReconciliationUpdate(Long invoiceId, BigDecimal unsettledAmount, Long userId) {
        OutputInvoiceEntity entity = getEntity(invoiceId);
        if (!InvoiceStatus.isVouchered(entity.getStatus())) {
            throw BusinessException.badRequest("仅已生成凭证的发票可核销");
        }
        String newStatus = unsettledAmount.compareTo(BigDecimal.ZERO) == 0
                ? InvoiceStatus.FULLY_RECONCILED
                : InvoiceStatus.PARTIALLY_RECONCILED;
        entity.setStatus(newStatus);
        entity.setUpdatedBy(userId);
        invoiceMapper.updateById(entity);
        log.info("销售发票核销更新: id={}, status={}", invoiceId, newStatus);
    }

    @Override
    @Transactional
    public void voidInvoice(Long invoiceId, Long userId, String reason) {
        if (reason == null || reason.trim().isEmpty()) {
            throw BusinessException.badRequest("作废必须填写原因");
        }
        OutputInvoiceEntity entity = getEntity(invoiceId);
        if (!InvoiceStatus.isVoidable(entity.getStatus())) {
            throw BusinessException.badRequest("当前状态不可作废: " + entity.getStatus());
        }
        entity.setStatus(InvoiceStatus.VOIDED);
        entity.setRemark(appendReason(entity.getRemark(), reason, userId));
        entity.setUpdatedBy(userId);
        invoiceMapper.updateById(entity);
        log.info("销售发票作废: id={}, userId={}, reason={}", invoiceId, userId, reason);
    }

    private OutputInvoiceEntity getEntity(Long id) {
        OutputInvoiceEntity entity = invoiceMapper.selectById(id);
        if (entity == null) {
            throw BusinessException.notFound("发票不存在: id=" + id);
        }
        return entity;
    }

    private String appendReason(String existing, String reason, Long userId) {
        String entry = "[" + userId + "] " + reason;
        if (existing == null || existing.isBlank()) {
            return entry;
        }
        return existing + " | " + entry;
    }
}