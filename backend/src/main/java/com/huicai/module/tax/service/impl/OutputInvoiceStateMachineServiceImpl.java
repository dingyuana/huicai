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
import org.springframework.scheduling.annotation.Async;
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
     * 自注入实现 AOP 代理调用, 使 @Async / @Transactional 生效.
     */
    @Lazy
    @Autowired
    private OutputInvoiceStateMachineServiceImpl self;

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

        // P31: 通过自注入调用 AOP 代理, 确保 @Async 在独立事务中执行业务单→应收单→凭证全流程
        self.postProcessAfterInvoiceConfirm(invoiceId, userId);
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
     * P31: 发票审核通过后异步执行后续流程. 流程:
     * 1. 创建业务单 (INVOICE_OUT, DRAFT) + 业务单分录
     * 2. 创建应收单 (DRAFT)
     * 3. 审核业务单 (DRAFT → SUBMITTED → APPROVED)
     * 4. 审核应收单 (DRAFT → CONFIRMED)
     * 5. 生成凭证 (APPROVED → VOUCHERED, 凭证状态为 PENDING_REVIEW 等待人工终审)
     * 异常情况: 记录失败明细到日志, 不影响批次整体结果.
     */
    @Async
    public void postProcessAfterInvoiceConfirm(Long invoiceId, Long userId) {
        log.info("P31 发票审核后异步流程开始: invoiceId={}", invoiceId);
        int docOk = 0, docFail = 0;
        int receivableOk = 0, receivableFail = 0;
        int voucherOk = 0, voucherFail = 0;
        List<String> failureDetails = new ArrayList<>();

        OutputInvoiceEntity invoice = invoiceMapper.selectById(invoiceId);
        if (invoice == null) {
            log.warn("P31 发票不存在: invoiceId={}", invoiceId);
            return;
        }

        // Step 1: 创建业务单
        BusinessDocEntity doc = null;
        try {
            doc = createBusinessDocFromInvoice(invoice, userId);
            docOk++;
        } catch (Exception e) {
            docFail++;
            failureDetails.add("发票#" + invoiceId + " 创建业务单失败: " + e.getMessage());
            log.warn("P31 创建业务单失败: invoiceId={}, err={}", invoiceId, e.getMessage());
            return;
        }

        // Step 2: 创建应收单
        try {
            createReceivableFromInvoice(doc, invoice, userId);
            receivableOk++;
        } catch (Exception e) {
            receivableFail++;
            failureDetails.add("发票#" + invoiceId + " 创建应收单失败: " + e.getMessage());
            log.warn("P31 创建应收单失败: invoiceId={}, err={}", invoiceId, e.getMessage());
            return;
        }

        // Step 3: 审核业务单
        try {
            businessDocService.submit(doc.getId(), userId);
            businessDocService.approve(doc.getId(), userId);
            log.info("P31 业务单审核通过: docId={}", doc.getId());
        } catch (Exception e) {
            docFail++;
            failureDetails.add("业务单#" + doc.getId() + " 审核失败: " + e.getMessage());
            log.warn("P31 业务单审核失败: docId={}, err={}", doc.getId(), e.getMessage());
            return;
        }

        // Step 4: 审核应收单
        try {
            List<ReceivableEntity> receivables = receivableMapper.selectList(
                    new LambdaQueryWrapper<ReceivableEntity>().eq(ReceivableEntity::getDocId, doc.getId())
            );
            for (ReceivableEntity recv : receivables) {
                receivableStateMachineService.confirm(recv.getId(), userId);
                receivableOk++;
            }
        } catch (Exception e) {
            receivableFail++;
            failureDetails.add("业务单#" + doc.getId() + " 应收单审核失败: " + e.getMessage());
            log.warn("P31 应收单审核失败: docId={}, err={}", doc.getId(), e.getMessage());
            return;
        }

        // Step 5: 生成凭证
        try {
            businessDocService.generateVoucher(doc.getId(), userId);
            voucherOk++;
        } catch (Exception e) {
            voucherFail++;
            failureDetails.add("业务单#" + doc.getId() + " 生凭证失败: " + e.getMessage());
            log.warn("P31 生凭证失败: docId={}, err={}", doc.getId(), e.getMessage());
        }

        log.info("P31 发票审核后异步流程完成: invoiceId={}, 业务单={}/{}, 应收单={}/{}, 生凭证={}/{}",
                invoiceId, docOk, docFail, receivableOk, receivableFail, voucherOk, voucherFail);
        if (!failureDetails.isEmpty()) {
            log.warn("P31 失败明细 (invoiceId={}):\n{}", invoiceId, String.join("\n", failureDetails));
        }
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
        doc.setStatus("DRAFT");
        doc.setSource("INVOICE_IMPORT");
        doc.setCreatedBy(DEFAULT_USER_ID);
        docMapper.insert(doc);

        // 创建业务单分录（供 generateVoucher 使用）
        BusinessDocEntryEntity entry = new BusinessDocEntryEntity();
        entry.setDocId(doc.getId());
        entry.setAmount(invoice.getTotalAmount());
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