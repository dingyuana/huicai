package com.huicai.module.tax.service.impl;

import com.huicai.common.exception.BusinessException;
import com.huicai.module.tax.constant.InvoiceStatus;
import com.huicai.module.tax.entity.OutputInvoiceEntity;
import com.huicai.module.tax.mapper.OutputInvoiceMapper;
import com.huicai.module.tax.service.OutputInvoiceStateMachineService;
import com.huicai.module.arap.service.ReceivableStateMachineService;
import com.huicai.module.arap.mapper.ReceivableMapper;
import com.huicai.module.arap.entity.ReceivableEntity;
import com.huicai.module.arap.constant.ArapStatus;
import com.huicai.module.tax.service.TaxService;
import org.springframework.data.redis.core.StringRedisTemplate;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Lazy;

/**
 * 销售发票状态机实现（P33 简化版）.
 *
 * <p>P33 改动：发票确认后直接创建应收单 + 凭证，不再经过业务单中间环节。
 * <p>编号关联：发票 ↔ 应收单 ↔ 凭证 双向追溯保持不变。
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
    private final ReceivableStateMachineService receivableStateMachineService;
    private final ReceivableMapper receivableMapper;
    private final StringRedisTemplate redisTemplate;

    /**
     * 自注入实现 AOP 代理调用, 使 @Transactional 生效.
     */
    @Lazy
    @Autowired
    private OutputInvoiceStateMachineServiceImpl self;

    @Lazy
    @Autowired
    private TaxService taxService;

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
        entity.setAuditedBy(userId);       // 记录审核人
        entity.setAuditedAt(LocalDateTime.now());  // 记录审核时间
        entity.setUpdatedBy(userId);
        invoiceMapper.updateById(entity);
        log.info("销售发票审核通过: id={}, userId={}", invoiceId, userId);

        // P33: 审核后直接创建应收单（不再经过业务单）
        createReceivableFromInvoiceDirect(invoiceId, userId);
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
    public void markVouchered(Long invoiceId, Long voucherId, String voucherNo, Long userId) {
        OutputInvoiceEntity entity = getEntity(invoiceId);
        if (!InvoiceStatus.isVoucherable(entity.getStatus())) {
            throw BusinessException.badRequest("仅已确认状态可生成凭证，当前: " + entity.getStatus());
        }
        entity.setStatus(InvoiceStatus.VOUCHERED);
        entity.setVoucherId(voucherId);
        entity.setVoucherNo(voucherNo);      // 新增：凭证编号冗余
        entity.setUpdatedBy(userId);
        invoiceMapper.updateById(entity);
        log.info("销售发票已生成凭证: invoiceId={}, voucherId={}, voucherNo={}", invoiceId, voucherId, voucherNo);
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

    // ==================== P33: 简化后的核心逻辑 ====================

    /**
     * P33 简化：发票审核后直接创建应收单，不再经过业务单中间环节。
     * 
     * 编号关联：
     * - 应收单 → invoiceId（直接关联发票ID）+ invoiceNo（编号冗余）
     * - 发票 → receivableId（应收单ID）+ receivableNo（应收单编号）
     */
    private void createReceivableFromInvoiceDirect(Long invoiceId, Long userId) {
        OutputInvoiceEntity invoice = invoiceMapper.selectById(invoiceId);
        if (invoice == null) {
            log.warn("发票不存在: invoiceId={}", invoiceId);
            return;
        }

        // 防重复创建：查 invoice_id 是否有应收单
        long existingCount = receivableMapper.selectCount(
                new LambdaQueryWrapper<ReceivableEntity>()
                        .eq(ReceivableEntity::getInvoiceId, invoiceId)
                        .eq(ReceivableEntity::getCustomerId, invoice.getCustomerId()));
        if (existingCount > 0) {
            log.info("发票已有应收单: invoiceId={}, count={}, skip", invoiceId, existingCount);
            return;
        }

        ReceivableEntity recv = new ReceivableEntity();
        recv.setCustomerId(invoice.getCustomerId());
        recv.setInvoiceId(invoice.getId());             // P33: 直接关联发票ID
        recv.setInvoiceNo(invoice.getInvoiceNo());      // 发票编号冗余
        recv.setVoucherId(null);                        // 凭证尚未生成
        recv.setPeriod(invoice.getPeriod());
        recv.setTxDate(invoice.getInvoiceDate());
        recv.setAmount(invoice.getTotalAmount());
        recv.setSettledAmount(BigDecimal.ZERO);
        recv.setUnsettledAmount(invoice.getTotalAmount());
        recv.setSummary(invoice.getCustomerName());
        recv.setStatus(ArapStatus.DRAFT);

        // 生成应收单编号: YS + period + 4位序号
        String receivableNo = generateReceivableNo(invoice.getPeriod());
        recv.setReceivableNo(receivableNo);

        receivableMapper.insert(recv);
        log.info("P33 销售发票应收单生成: invoiceId={}, customerId={}, receivableId={}, receivableNo={}, amount={}",
                invoiceId, invoice.getCustomerId(), recv.getId(), receivableNo, invoice.getTotalAmount());

        // 回写发票：应收单 ID 和编号（双向追溯）
        invoice.setReceivableId(recv.getId());
        invoice.setReceivableNo(receivableNo);
        invoiceMapper.updateById(invoice);
    }

    /**
     * 生成应收单号 (YS + period + 4位序号).
     */
    private String generateReceivableNo(String period) {
        String key = "receivable:no:" + period;
        Long seq = redisTemplate.opsForValue().increment(key);
        return "YS" + period + String.format("%04d", seq);
    }

    // ==================== 工具方法 ====================

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
