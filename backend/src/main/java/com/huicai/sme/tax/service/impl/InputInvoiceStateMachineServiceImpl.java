package com.huicai.sme.tax.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.huicai.common.exception.BusinessException;
import com.huicai.base.business.entity.BusinessDocEntity;
import com.huicai.base.voucher.entity.VoucherEntity;
import com.huicai.base.voucher.entity.VoucherEntryEntity;
import com.huicai.base.business.mapper.BusinessDocMapper;
import com.huicai.base.voucher.mapper.VoucherEntryMapper;
import com.huicai.base.voucher.mapper.VoucherMapper;
import com.huicai.base.voucher.service.VoucherNoService;
import com.huicai.base.system.entity.Subject;
import com.huicai.base.system.mapper.SubjectMapper;
import com.huicai.sme.tax.constant.InvoiceStatus;
import com.huicai.base.business.entity.InputInvoiceEntity;
import com.huicai.base.business.mapper.InputInvoiceMapper;
import com.huicai.sme.tax.service.InputInvoiceStateMachineService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.dao.OptimisticLockingFailureException;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/**
 * 进项发票状态机实现（P40）.
 *
 * 与销项 OutputInvoiceStateMachineServiceImpl 对称：
 * - 审核通过后创建 INVOICE_IN 业务单据 + 凭证
 * - 凭证科目方向：借 1601(原材料)/2221.01(进项税) / 贷 2202(应付账款)
 *
 * 状态变更通过 BaseMapper.updateById 写入数据库。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class InputInvoiceStateMachineServiceImpl implements InputInvoiceStateMachineService {

    private final InputInvoiceMapper invoiceMapper;
    private final BusinessDocMapper businessDocMapper;
    private final VoucherMapper voucherMapper;
    private final VoucherEntryMapper voucherEntryMapper;
    private final VoucherNoService voucherNoService;
    private final SubjectMapper subjectMapper;
    private final StringRedisTemplate redisTemplate;

    private static final long VOUCHER_TYPE_ID = 2L; // FK 付款凭证

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void submitForReview(Long invoiceId, Long userId) {
        InputInvoiceEntity entity = getEntity(invoiceId);
        if (!InvoiceStatus.isPendingConfirm(entity.getStatus())) {
            throw BusinessException.badRequest("仅待确认状态可提交审核，当前: " + entity.getStatus());
        }
        entity.setStatus(InvoiceStatus.PENDING_REVIEW);
        entity.setUpdatedBy(userId);
        invoiceMapper.updateById(entity);
        log.info("进项发票提交审核: id={}, userId={}", invoiceId, userId);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void confirm(Long invoiceId, Long userId) {
        InputInvoiceEntity entity = getEntity(invoiceId);
        if (!InvoiceStatus.PENDING_REVIEW.equals(entity.getStatus())) {
            throw BusinessException.badRequest("仅待审核状态可确认，当前: " + entity.getStatus());
        }
        entity.setStatus(InvoiceStatus.CONFIRMED);
        entity.setAuditedBy(userId);
        entity.setAuditedAt(LocalDateTime.now());
        entity.setUpdatedBy(userId);
        if (invoiceMapper.updateById(entity) == 0) {
            throw new OptimisticLockingFailureException("发票版本冲突, id=" + invoiceId);
        }
        log.info("进项发票审核通过: id={}, userId={}", invoiceId, userId);

        // 创建 INVOICE_IN 业务单据 + 凭证
        createBusinessDocFromInvoice(invoiceId, userId);
        generateVoucherFromInvoiceDirect(invoiceId, userId);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void reject(Long invoiceId, Long userId, String reason) {
        if (reason == null || reason.trim().isEmpty()) {
            throw BusinessException.badRequest("驳回必须填写原因");
        }
        InputInvoiceEntity entity = getEntity(invoiceId);
        if (!InvoiceStatus.PENDING_REVIEW.equals(entity.getStatus())) {
            throw BusinessException.badRequest("仅待审核状态可驳回，当前: " + entity.getStatus());
        }
        entity.setStatus(InvoiceStatus.PENDING_CONFIRM);
        entity.setRejectReason(reason);
        entity.setUpdatedBy(userId);
        invoiceMapper.updateById(entity);
        log.info("进项发票审核驳回: id={}, userId={}, reason={}", invoiceId, userId, reason);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void revertToReview(Long invoiceId, Long userId) {
        InputInvoiceEntity entity = getEntity(invoiceId);
        if (!InvoiceStatus.isConfirmed(entity.getStatus())) {
            throw BusinessException.badRequest("仅已确认状态可回退到待审核，当前: " + entity.getStatus());
        }
        entity.setStatus(InvoiceStatus.PENDING_REVIEW);
        entity.setUpdatedBy(userId);
        invoiceMapper.updateById(entity);
        log.info("进项发票回退到待审核: id={}, userId={}", invoiceId, userId);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void markVouchered(Long invoiceId, Long voucherId, String voucherNo, Long userId) {
        InputInvoiceEntity entity = getEntity(invoiceId);
        if (!InvoiceStatus.isVoucherable(entity.getStatus())) {
            throw BusinessException.badRequest("仅已确认状态可生成凭证，当前: " + entity.getStatus());
        }
        entity.setStatus(InvoiceStatus.VOUCHERED);
        entity.setVoucherId(voucherId);
        entity.setVoucherNo(voucherNo);
        entity.setUpdatedBy(userId);
        invoiceMapper.updateById(entity);
        log.info("进项发票已生成凭证: invoiceId={}, voucherId={}, voucherNo={}", invoiceId, voucherId, voucherNo);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void onReconciliationUpdate(Long invoiceId, BigDecimal unsettledAmount, Long userId) {
        InputInvoiceEntity entity = getEntity(invoiceId);
        if (!InvoiceStatus.isVouchered(entity.getStatus())) {
            log.warn("P40 进项发票未生成凭证跳过核销状态同步: id={}, status={}", invoiceId, entity.getStatus());
            return;
        }
        String newStatus = unsettledAmount.compareTo(BigDecimal.ZERO) == 0
                ? InvoiceStatus.FULLY_RECONCILED
                : InvoiceStatus.PARTIALLY_RECONCILED;
        entity.setStatus(newStatus);
        entity.setUpdatedBy(userId);
        invoiceMapper.updateById(entity);
        log.info("进项发票核销更新: id={}, status={}", invoiceId, newStatus);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void voidInvoice(Long invoiceId, Long userId, String reason) {
        if (reason == null || reason.trim().isEmpty()) {
            throw BusinessException.badRequest("作废必须填写原因");
        }
        InputInvoiceEntity entity = getEntity(invoiceId);
        if (!InvoiceStatus.isVoidable(entity.getStatus())) {
            throw BusinessException.badRequest("当前状态不可作废: " + entity.getStatus());
        }
        entity.setStatus(InvoiceStatus.VOIDED);
        entity.setRejectReason(appendReason(entity.getRejectReason(), reason, userId));
        entity.setUpdatedBy(userId);
        invoiceMapper.updateById(entity);
        log.info("进项发票作废: id={}, userId={}, reason={}", invoiceId, userId, reason);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Long reverseInvoice(Long invoiceId, Long userId, String reason) {
        if (reason == null || reason.trim().isEmpty()) {
            throw BusinessException.badRequest("红冲必须填写原因");
        }
        InputInvoiceEntity original = getEntity(invoiceId);
        if (!InvoiceStatus.isReversible(original.getStatus())) {
            throw BusinessException.badRequest("当前状态不可红冲: " + original.getStatus()
                    + "，仅 CONFIRMED/VOUCHERED/PARTIALLY_RECONCILED 可红冲");
        }

        // 创建红字进项发票（金额取反，status=PENDING_CONFIRM）
        InputInvoiceEntity redInvoice = new InputInvoiceEntity();
        redInvoice.setInvoiceNo(original.getInvoiceNo() + "-R");
        redInvoice.setInvoiceDate(original.getInvoiceDate());
        redInvoice.setPeriod(original.getPeriod());
        redInvoice.setVendorId(original.getVendorId());
        redInvoice.setVendorName(original.getVendorName());
        redInvoice.setAmount(original.getAmount().negate());
        redInvoice.setTaxRate(original.getTaxRate());
        redInvoice.setTaxAmount(original.getTaxAmount().negate());
        redInvoice.setTotalAmount(original.getTotalAmount().negate());
        redInvoice.setInvoiceType(original.getInvoiceType());
        redInvoice.setStatus(InvoiceStatus.PENDING_CONFIRM);
        redInvoice.setCertificationStatus("UNCERTIFIED");
        redInvoice.setRemark(appendReason(original.getRemark(), reason, userId));
        redInvoice.setCreatedBy(userId);
        invoiceMapper.insert(redInvoice);

        // 原发票标记为 REVERSED
        original.setStatus(InvoiceStatus.REVERSED);
        original.setUpdatedBy(userId);
        invoiceMapper.updateById(original);

        log.info("进项发票红冲: originalId={}, redInvoiceId={}, originalInvoiceNo={}, reason={}",
                invoiceId, redInvoice.getId(), original.getInvoiceNo(), reason);
        return redInvoice.getId();
    }

    // ==================== 业务单据 + 凭证生成 ====================

    /**
     * P40：进项发票审核后创建 INVOICE_IN 业务单据.
     * 与销项 createBusinessDocFromInvoice 对称。
     */
    private BusinessDocEntity createBusinessDocFromInvoice(Long invoiceId, Long userId) {
        InputInvoiceEntity invoice = invoiceMapper.selectById(invoiceId);
        if (invoice == null) {
            log.warn("发票不存在: invoiceId={}", invoiceId);
            return null;
        }

        // 防重复创建
        long existingCount = businessDocMapper.selectCount(
                new LambdaQueryWrapper<BusinessDocEntity>()
                        .eq(BusinessDocEntity::getInvoiceNo, invoice.getInvoiceNo())
                        .eq(BusinessDocEntity::getDocType, "INVOICE_IN"));
        if (existingCount > 0) {
            log.info("进项发票已有 INVOICE_IN 业务单据: invoiceId={}, invoiceNo={}, skip",
                    invoiceId, invoice.getInvoiceNo());
            return businessDocMapper.selectOne(
                    new LambdaQueryWrapper<BusinessDocEntity>()
                            .eq(BusinessDocEntity::getInvoiceNo, invoice.getInvoiceNo())
                            .eq(BusinessDocEntity::getDocType, "INVOICE_IN")
                            .last("LIMIT 1"));
        }

        BusinessDocEntity doc = new BusinessDocEntity();
        doc.setDocNo(generateDocNo(invoice.getPeriod()));
        doc.setDocType("INVOICE_IN");
        doc.setDocDate(invoice.getInvoiceDate());
        doc.setPeriod(invoice.getPeriod());
        doc.setAmount(invoice.getTotalAmount());
        doc.setStatus("DRAFT");
        doc.setSupplierId(invoice.getVendorId());
        doc.setSummary(invoice.getVendorName());
        doc.setSource("MANUAL");
        doc.setInvoiceNo(invoice.getInvoiceNo());
        doc.setInvoiceId(invoice.getId());
        doc.setSettledAmount(BigDecimal.ZERO);
        doc.setUnsettledAmount(invoice.getTotalAmount());
        doc.setCreatedBy(userId);
        doc.setSubmittedBy(userId);

        businessDocMapper.insert(doc);
        log.info("P40 进项发票业务单据生成: invoiceId={}, docId={}, docNo={}, amount={}",
                invoiceId, doc.getId(), doc.getDocNo(), invoice.getTotalAmount());

        // 回写发票：业务单据 ID 和编号
        invoice.setDocId(doc.getId());
        invoice.setDocNo(doc.getDocNo());
        invoiceMapper.updateById(invoice);

        return doc;
    }

    /**
     * P40：进项发票审核后创建凭证.
     * 科目方向：借 1601(原材料) + 2221.01(进项税) / 贷 2202(应付账款)
     */
    private void generateVoucherFromInvoiceDirect(Long invoiceId, Long userId) {
        try {
            InputInvoiceEntity invoice = invoiceMapper.selectById(invoiceId);
            if (invoice == null) return;

            // 批量查询科目
            Map<String, Subject> subjects = findSubjectsByCodes(
                    List.of("1601", "2221.01", "2202"));
            Subject subjectInventory = subjects.get("1601");
            Subject subjectInputTax = subjects.get("2221.01");
            Subject subjectPayable = subjects.get("2202");
            if (subjectPayable == null) {
                throw new BusinessException(500, "缺少基础科目配置(2202 应付账款)");
            }

            BigDecimal exclTax = invoice.getAmount() != null ? invoice.getAmount() : BigDecimal.ZERO;
            BigDecimal taxAmt = invoice.getTaxAmount() != null ? invoice.getTaxAmount() : BigDecimal.ZERO;
            BigDecimal totalAmt = invoice.getTotalAmount() != null ? invoice.getTotalAmount() : exclTax.add(taxAmt);

            String voucherNo = voucherNoService.generateNextNo(invoice.getPeriod(), VOUCHER_TYPE_ID);

            VoucherEntity voucher = new VoucherEntity();
            voucher.setVoucherNo(voucherNo);
            voucher.setPeriod(invoice.getPeriod());
            voucher.setVoucherTypeId(VOUCHER_TYPE_ID);
            voucher.setStatus("DRAFT");
            voucher.setSource("GENERATED");
            String summary = "进项发票: " + (invoice.getInvoiceNo() != null ? invoice.getInvoiceNo() : "")
                    + " - " + (invoice.getVendorName() != null ? invoice.getVendorName() : "");
            voucher.setSummary(summary);
            voucher.setTotalDebit(totalAmt);
            voucher.setTotalCredit(totalAmt);
            voucher.setCreatedBy(userId);
            voucher.setSourceDocId(invoice.getId());
            voucher.setSourceDocType("INPUT_INVOICE");
            voucher.setSourceDocNo(invoice.getInvoiceNo());
            voucherMapper.insert(voucher);

            int sort = 1;
            String entrySummary = (invoice.getInvoiceNo() != null ? invoice.getInvoiceNo() : "")
                    + " " + (invoice.getVendorName() != null ? invoice.getVendorName() : "");

            // 借: 1601 原材料/存货
            if (subjectInventory != null) {
                VoucherEntryEntity dr1 = new VoucherEntryEntity();
                dr1.setVoucherId(voucher.getId());
                dr1.setSubjectId(subjectInventory.getId());
                dr1.setDebit(exclTax);
                dr1.setCredit(BigDecimal.ZERO);
                dr1.setSummary(entrySummary);
                dr1.setSortOrder(sort++);
                voucherEntryMapper.insert(dr1);
            }

            // 借: 2221.01 进项税额
            if (subjectInputTax != null && taxAmt.compareTo(BigDecimal.ZERO) != 0) {
                VoucherEntryEntity dr2 = new VoucherEntryEntity();
                dr2.setVoucherId(voucher.getId());
                dr2.setSubjectId(subjectInputTax.getId());
                dr2.setDebit(taxAmt);
                dr2.setCredit(BigDecimal.ZERO);
                dr2.setSummary(entrySummary);
                dr2.setSortOrder(sort++);
                voucherEntryMapper.insert(dr2);
            }

            // 贷: 2202 应付账款
            VoucherEntryEntity cr = new VoucherEntryEntity();
            cr.setVoucherId(voucher.getId());
            cr.setSubjectId(subjectPayable.getId());
            cr.setDebit(BigDecimal.ZERO);
            cr.setCredit(totalAmt);
            cr.setSummary(entrySummary);
            cr.setSortOrder(sort);
            voucherEntryMapper.insert(cr);

            // 回写发票 -> VOUCHERED
            invoice.setStatus(InvoiceStatus.VOUCHERED);
            invoice.setVoucherId(voucher.getId());
            invoice.setVoucherNo(voucherNo);
            invoice.setUpdatedBy(userId);
            invoiceMapper.updateById(invoice);

            // 回写业务单据
            if (invoice.getDocId() != null) {
                BusinessDocEntity doc = businessDocMapper.selectById(invoice.getDocId());
                if (doc != null) {
                    doc.setVoucherId(voucher.getId());
                    doc.setVoucherNo(voucherNo);
                    doc.setStatus("VOUCHERED");
                    doc.setUpdatedBy(userId);
                    businessDocMapper.updateById(doc);
                }
            }

            log.info("P40 进项发票凭证生成: invoiceId={}, voucherId={}, voucherNo={}",
                    invoiceId, voucher.getId(), voucherNo);
        } catch (Exception e) {
            log.error("P40 进项发票凭证生成失败: invoiceId={}, error={}", invoiceId, e.getMessage(), e);
            throw new BusinessException(500, "进项发票凭证生成失败: " + e.getMessage());
        }
    }

    // ==================== 工具方法 ====================

    private InputInvoiceEntity getEntity(Long id) {
        InputInvoiceEntity entity = invoiceMapper.selectById(id);
        if (entity == null) {
            throw BusinessException.notFound("进项发票不存在: id=" + id);
        }
        return entity;
    }

    private Map<String, Subject> findSubjectsByCodes(List<String> codes) {
        List<Subject> list = subjectMapper.selectList(
                new LambdaQueryWrapper<Subject>().in(Subject::getCode, codes));
        Map<String, Subject> result = new java.util.HashMap<>(list.size() * 2);
        for (Subject s : list) result.put(s.getCode(), s);
        return result;
    }

    private String generateDocNo(String period) {
        String key = "doc:no:" + period + ":INVOICE_IN";
        Long serial = redisTemplate.opsForValue().increment(key);
        if (serial == null) serial = 1L;
        return "FPR" + period + String.format("%04d", serial);
    }

    private String appendReason(String existing, String reason, Long userId) {
        String entry = "[" + userId + "] " + reason;
        if (existing == null || existing.isBlank()) {
            return entry;
        }
        return existing + " | " + entry;
    }
}
