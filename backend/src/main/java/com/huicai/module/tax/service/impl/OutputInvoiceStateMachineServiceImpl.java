package com.huicai.module.tax.service.impl;

import com.huicai.common.exception.BusinessException;
import com.huicai.module.tax.constant.InvoiceStatus;
import com.huicai.module.tax.entity.OutputInvoiceEntity;
import com.huicai.module.tax.mapper.OutputInvoiceMapper;
import com.huicai.module.tax.service.OutputInvoiceStateMachineService;
import com.huicai.module.finance.service.BusinessDocService;
import com.huicai.module.arap.service.ReceivableStateMachineService;
import com.huicai.module.finance.mapper.BusinessDocMapper;
import com.huicai.module.finance.mapper.BusinessDocEntryMapper;
import com.huicai.module.arap.mapper.ReceivableMapper;
import com.huicai.module.finance.entity.BusinessDocEntity;
import com.huicai.module.finance.entity.BusinessDocEntryEntity;
import com.huicai.module.arap.entity.ReceivableEntity;
import com.huicai.module.arap.constant.ArapStatus;
import com.huicai.module.tax.service.TaxService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Lazy;

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
    private final BusinessDocService businessDocService;
    private final ReceivableStateMachineService receivableStateMachineService;
    private final BusinessDocMapper docMapper;
    private final BusinessDocEntryMapper docEntryMapper;
    private final ReceivableMapper receivableMapper;
    private final StringRedisTemplate redisTemplate;

    @Value("${invoice.auto-flow-after-import:false}")
    private boolean autoFlowAfterImport;

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

    /**
     * 发票审核通过后自动生成：业务单(DRAFT) + 应收单(DRAFT) + 凭证。
     *
     * 流程：发票审核 → 生成应收单据和凭证（无需中间的业务单审批环节）。
     * 业务单仅作为追溯记录，无需手动审批。
     */
    public void postProcessAfterInvoiceConfirm(Long invoiceId, Long userId) {
        OutputInvoiceEntity invoice = invoiceMapper.selectById(invoiceId);
        if (invoice == null) {
            log.warn("发票不存在: invoiceId={}", invoiceId);
            return;
        }

        BusinessDocEntity doc = createBusinessDocFromInvoice(invoice, userId);
        log.info("发票审核后创建业务单: invoiceId={}, docId={}", invoiceId, doc.getId());

        createReceivableFromInvoice(doc, invoice, userId);
        log.info("发票审核后创建应收单完成: invoiceId={}, docId={}", invoiceId, doc.getId());

        taxService.generateVoucherFromInvoice(invoiceId, userId);

        OutputInvoiceEntity updated = invoiceMapper.selectById(invoiceId);
        if (updated != null && updated.getVoucherId() != null) {
            doc.setVoucherId(updated.getVoucherId());
            docMapper.updateById(doc);

            ReceivableEntity recv = receivableMapper.selectOne(
                    new LambdaQueryWrapper<ReceivableEntity>()
                            .eq(ReceivableEntity::getDocId, doc.getId()));
            if (recv != null) {
                recv.setVoucherId(updated.getVoucherId());
                receivableMapper.updateById(recv);
            }
        }
        log.info("发票审核后自动生成凭证完成: invoiceId={}", invoiceId);
    }

    private static final long DEFAULT_USER_ID = 1L;

    /**
     * 根据发票信息创建业务单和分录.
     */
    private BusinessDocEntity createBusinessDocFromInvoice(OutputInvoiceEntity invoice, Long userId) {
        BusinessDocEntity doc = new BusinessDocEntity();
        doc.setDocNo(generateDocNo(invoice.getPeriod()));
        doc.setDocType("INVOICE_OUT");
        doc.setDocDate(invoice.getInvoiceDate());
        doc.setPeriod(invoice.getPeriod());
        doc.setAmount(invoice.getTotalAmount());
        doc.setCustomerId(invoice.getCustomerId());
        doc.setSummary(invoice.getCustomerName());
        doc.setInvoiceNo(invoice.getInvoiceNo());
        doc.setStatus("DRAFT");
        doc.setSource("INVOICE_IMPORT");
        doc.setCreatedBy(DEFAULT_USER_ID);
        docMapper.insert(doc);

        // 创建业务单分录（供 generateVoucher 使用）
        BusinessDocEntryEntity entry = new BusinessDocEntryEntity();
        entry.setDocId(doc.getId());
        entry.setAmount(invoice.getTotalAmount());
        entry.setInvoiceNo(invoice.getInvoiceNo());
        entry.setSummary(invoice.getCustomerName());
        entry.setSortOrder(1);
        docEntryMapper.insert(entry);

        // 更新发票的 docId
        invoice.setDocId(doc.getId());
        invoiceMapper.updateById(invoice);

        return doc;
    }

    /**
     * 根据业务单和发票信息创建应收单.
     */
    private void createReceivableFromInvoice(BusinessDocEntity doc, OutputInvoiceEntity invoice, Long userId) {
        ReceivableEntity recv = new ReceivableEntity();
        recv.setCustomerId(invoice.getCustomerId());
        recv.setDocId(doc.getId());
        recv.setVoucherId(doc.getVoucherId());
        recv.setPeriod(invoice.getPeriod());
        recv.setTxDate(invoice.getInvoiceDate());
        recv.setAmount(invoice.getTotalAmount());
        recv.setSettledAmount(BigDecimal.ZERO);
        recv.setUnsettledAmount(invoice.getTotalAmount());
        recv.setSummary(invoice.getCustomerName());
        recv.setStatus(ArapStatus.DRAFT);
        receivableMapper.insert(recv);
        log.info("P31 销售发票应收单生成: customerId={}, docId={}, amount={}",
                invoice.getCustomerId(), doc.getId(), invoice.getTotalAmount());
    }

    /**
     * 生成业务单号 (FPS + period + 4位序号).
     */
    private String generateDocNo(String period) {
        String key = "doc:no:INVOICE_OUT:" + period;
        Long seq = redisTemplate.opsForValue().increment(key);
        return "FPS" + period + String.format("%04d", seq);
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