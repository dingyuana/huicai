package com.huicai.sme.tax.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.huicai.common.exception.BusinessException;
import com.huicai.base.business.entity.BusinessDocEntity;
import com.huicai.base.voucher.entity.VoucherEntity;
import com.huicai.base.business.mapper.BusinessDocMapper;
import com.huicai.base.voucher.mapper.VoucherMapper;
import com.huicai.sme.tax.constant.InvoiceStatus;
import com.huicai.base.business.entity.OutputInvoiceEntity;
import com.huicai.base.business.mapper.OutputInvoiceMapper;
import com.huicai.base.business.service.OutputInvoiceStateMachineService;
import com.huicai.sme.tax.service.TaxService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Lazy;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.context.ApplicationContext;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.dao.OptimisticLockingFailureException;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

import com.huicai.common.context.EnterpriseContextHolder;

/**
 * 销售发票状态机实现（P34 恢复：业务单据体系）.
 *
 * <p>P34 改动：发票确认后创建 INVOICE_OUT 业务单据 + 凭证，不再创建独立应收单。
 * <p>编号关联：发票 ↔ 业务单据 ↔ 凭证 双向追溯。
 *
 * <p>依据 SPEC docs/specs/P34-receivable-payable-to-businessdoc.md §2.3
 * 状态变更通过 BaseMapper.updateById 写入数据库，
 * P24 StatusChangeAspect 自动拦截并写入 t_audit_log。
 */
@Slf4j
@Service
public class OutputInvoiceStateMachineServiceImpl implements OutputInvoiceStateMachineService {

    private final OutputInvoiceMapper invoiceMapper;
    private final BusinessDocMapper businessDocMapper;
    private final VoucherMapper voucherMapper;
    private final StringRedisTemplate redisTemplate;
    private final ApplicationContext applicationContext;

    private final OutputInvoiceStateMachineServiceImpl self;

    /**
     * 构造器注入（替代 @Autowired 字段注入）.
     * self 自注入用于 @Transactional 内部调用生效.
     */
    public OutputInvoiceStateMachineServiceImpl(
        OutputInvoiceMapper invoiceMapper,
        BusinessDocMapper businessDocMapper,
        VoucherMapper voucherMapper,
        StringRedisTemplate redisTemplate,
        @Lazy OutputInvoiceStateMachineServiceImpl self,
        ApplicationContext applicationContext) {
        this.invoiceMapper = invoiceMapper;
this.businessDocMapper = businessDocMapper;
this.voucherMapper = voucherMapper;
this.redisTemplate = redisTemplate;
this.self = self;
this.applicationContext = applicationContext;
    }

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
        if (invoiceMapper.updateById(entity) == 0) {
            throw new OptimisticLockingFailureException("发票版本冲突, id=" + invoiceId);
        }
        log.info("销售发票审核通过: id={}, userId={}", invoiceId, userId);

        // P34: 审核后创建 INVOICE_OUT 业务单据 + 凭证（不再创建独立应收单）
        BusinessDocEntity doc = createBusinessDocFromInvoice(invoiceId, userId);
        generateVoucherFromInvoiceDirect(invoiceId, userId);
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
            log.warn("P38 发票未生成凭证跳过核销状态同步: id={}, status={}", invoiceId, entity.getStatus());
            return;
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

    @Override
    @Transactional
    public Long reverseInvoice(Long invoiceId, Long userId, String reason) {
        if (reason == null || reason.trim().isEmpty()) {
            throw BusinessException.badRequest("红冲必须填写原因");
        }
        OutputInvoiceEntity original = getEntity(invoiceId);
        if (!InvoiceStatus.isReversible(original.getStatus())) {
            throw BusinessException.badRequest("当前状态不可红冲: " + original.getStatus()
                    + "，仅 CONFIRMED/VOUCHERED/PARTIALLY_RECONCILED 可红冲");
        }
        // 检查是否已被红冲
        if (original.getReversedFrom() != null) {
            throw BusinessException.badRequest("该发票已被红冲，不可重复红冲");
        }

        // 1. 创建红字发票（金额取反，status=DRAFT）
        OutputInvoiceEntity redInvoice = new OutputInvoiceEntity();
        redInvoice.setInvoiceNo(original.getInvoiceNo() + "-R");
        redInvoice.setInvoiceDate(original.getInvoiceDate());
        redInvoice.setPeriod(original.getPeriod());
        redInvoice.setCustomerId(original.getCustomerId());
        redInvoice.setCustomerName(original.getCustomerName());
        redInvoice.setAmount(original.getAmount().negate());
        redInvoice.setTaxRate(original.getTaxRate());
        redInvoice.setTaxAmount(original.getTaxAmount().negate());
        redInvoice.setTotalAmount(original.getTotalAmount().negate());
        redInvoice.setInvoiceType(original.getInvoiceType());
        redInvoice.setStatus(InvoiceStatus.PENDING_CONFIRM);
        redInvoice.setRemark(appendReason(original.getRemark(), reason, userId));
        redInvoice.setOriginalInvoiceNo(original.getInvoiceNo());
        redInvoice.setCreatedBy(userId);
        invoiceMapper.insert(redInvoice);

        // 2. 原蓝字发票标记为 REVERSED，记录 reversedFrom
        original.setStatus(InvoiceStatus.REVERSED);
        original.setReversedFrom(redInvoice.getId());
        original.setUpdatedBy(userId);
        invoiceMapper.updateById(original);

        log.info("销售发票红冲: originalId={}, redInvoiceId={}, originalInvoiceNo={}, reason={}",
                invoiceId, redInvoice.getId(), original.getInvoiceNo(), reason);
        return redInvoice.getId();
    }

    // ==================== P34: 业务单据核心逻辑 ====================

    /**
     * P34：发票审核后创建 INVOICE_OUT 业务单据，不再创建独立应收单。
     *
     * 编号关联：
     * - 业务单据 → invoiceNo（发票编号冗余）
     * - 发票 → docId（业务单据ID）+ docNo（业务单据编号）
     *
     * P36 红冲：如果发票是红字发票（金额<0 或 originalInvoiceNo 非空），
     * 则标记 reversedFrom 指向被红冲的蓝字业务单据。
     */
    private BusinessDocEntity createBusinessDocFromInvoice(Long invoiceId, Long userId) {
        OutputInvoiceEntity invoice = invoiceMapper.selectById(invoiceId);
        if (invoice == null) {
            log.warn("发票不存在: invoiceId={}", invoiceId);
            return null;
        }

        // P36: 判断是否为红字发票
        boolean isRedInvoice = invoice.getAmount() != null && invoice.getAmount().compareTo(BigDecimal.ZERO) < 0
                || invoice.getOriginalInvoiceNo() != null;

        // 防重复创建：查 invoiceNo + INVOICE_OUT 是否已有业务单据
        long existingCount = businessDocMapper.selectCount(
                new LambdaQueryWrapper<BusinessDocEntity>()
                        .eq(BusinessDocEntity::getInvoiceNo, invoice.getInvoiceNo())
                        .eq(BusinessDocEntity::getDocType, "INVOICE_OUT"));
        if (existingCount > 0) {
            log.info("发票已有 INVOICE_OUT 业务单据: invoiceId={}, invoiceNo={}, skip", invoiceId, invoice.getInvoiceNo());
            return businessDocMapper.selectOne(
                    new LambdaQueryWrapper<BusinessDocEntity>()
                            .eq(BusinessDocEntity::getInvoiceNo, invoice.getInvoiceNo())
                            .eq(BusinessDocEntity::getDocType, "INVOICE_OUT")
                            .last("LIMIT 1"));
        }

        BusinessDocEntity doc = new BusinessDocEntity();
        doc.setDocNo(generateDocNo("INVOICE_OUT", invoice.getPeriod()));
        doc.setDocType("INVOICE_OUT");
        doc.setDocDate(invoice.getInvoiceDate());
        doc.setPeriod(invoice.getPeriod());
        doc.setAmount(invoice.getTotalAmount());
        doc.setStatus("DRAFT");
        doc.setCustomerId(invoice.getCustomerId());
        doc.setSummary(isRedInvoice ? "红冲: " + invoice.getCustomerName() : invoice.getCustomerName());
        doc.setSource(isRedInvoice ? "RED_FLUSH" : "IMPORTED");
        doc.setInvoiceNo(invoice.getInvoiceNo());
        doc.setInvoiceId(invoice.getId());   // P1: 直接关联发票ID
        doc.setSettledAmount(BigDecimal.ZERO);
        doc.setUnsettledAmount(invoice.getTotalAmount());
        doc.setCreatedBy(userId);
        doc.setSubmittedBy(userId);
        Long enterpriseId = EnterpriseContextHolder.get();
        if (enterpriseId == null) {
            // Fallback: 从当前认证用户获取 enterpriseId（理论上不应发生）
            enterpriseId = 1L; // 默认企业 1
        }
        doc.setEnterpriseId(enterpriseId);

        // P36: 红字发票 → 关联被红冲的蓝字业务单据
        if (isRedInvoice && invoice.getReversedFrom() != null) {
            // 通过蓝字发票 ID 查找其对应的蓝字业务单据
            BusinessDocEntity blueDoc = businessDocMapper.selectOne(
                    new LambdaQueryWrapper<BusinessDocEntity>()
                            .eq(BusinessDocEntity::getInvoiceNo, getOriginalBlueInvoiceNo(invoice))
                            .eq(BusinessDocEntity::getDocType, "INVOICE_OUT")
                            .last("LIMIT 1"));
            if (blueDoc != null) {
                doc.setReversedFrom(blueDoc.getId());
                log.info("P36 红冲业务单据关联原单据: redDocId={}, reversedFrom={}", doc.getId(), blueDoc.getId());
            }
        }

        businessDocMapper.insert(doc);
        log.info("P34 销售发票业务单据生成: invoiceId={}, docId={}, docNo={}, amount={}",
                invoiceId, doc.getId(), doc.getDocNo(), invoice.getTotalAmount());

        // 回写发票：业务单据 ID 和编号（双向追溯）
        invoice.setDocId(doc.getId());
        invoice.setDocNo(doc.getDocNo());
        invoiceMapper.updateById(invoice);

        return doc;
    }

    /**
     * 从红字发票获取原蓝字发票号。
     * 红字发票的 originalInvoiceNo 字段保存了蓝字发票号。
     */
    private String getOriginalBlueInvoiceNo(OutputInvoiceEntity redInvoice) {
        if (redInvoice.getOriginalInvoiceNo() != null) {
            return redInvoice.getOriginalInvoiceNo();
        }
        // 兜底：从 remark 中提取（如果 originalInvoiceNo 未设置）
        if (redInvoice.getRemark() != null) {
            // remark 格式: "[userId] 原因" — 不从这里提取，直接返回 null
        }
        return null;
    }

    /**
     * 生成业务单据号 (类型码 + period + 4位序号).
     */
    private String generateDocNo(String docType, String period) {
        String key = "doc:no:" + period + ":" + docType;
        initRedisCounterIfMissing(key, "FPS" + period);
        Long serial = redisTemplate.opsForValue().increment(key);
        if (serial == null) serial = 1L;
        String typeCode = "FPS"; // INVOICE_OUT
        return typeCode + period + String.format("%04d", serial);
    }

    private void initRedisCounterIfMissing(String redisKey, String docNoPrefix) {
        Boolean existed = redisTemplate.hasKey(redisKey);
        if (Boolean.FALSE.equals(existed)) {
            String maxNo = businessDocMapper.selectMaxDocNoByPrefix(docNoPrefix);
            if (maxNo != null && maxNo.length() > docNoPrefix.length()) {
                String serialStr = maxNo.substring(docNoPrefix.length());
                // 去除前导零，INCR 要求纯数字
                String numeric = serialStr.replaceFirst("^0+", "");
                if (numeric.isEmpty()) numeric = "0";
                redisTemplate.opsForValue().setIfAbsent(redisKey, numeric);
            }
        }
    }

    /**
     * P33 简化：发票审核后直接创建凭证（DRAFT 状态，等待人工审核）。
     *
     * P36 红冲：如果是红字发票，生成凭证后回填 reversedFrom 指向原凭证。
     */
    private void generateVoucherFromInvoiceDirect(Long invoiceId, Long userId) {
        try {
            OutputInvoiceEntity invoice = invoiceMapper.selectById(invoiceId);
            boolean isRedInvoice = invoice != null
                    && (invoice.getAmount() != null && invoice.getAmount().compareTo(BigDecimal.ZERO) < 0
                        || invoice.getOriginalInvoiceNo() != null);

            applicationContext.getBean(TaxService.class).generateVoucherFromInvoice(invoiceId, userId);

            // 重新读取发票（markVouchered 已设置 voucherId/voucherNo）
            OutputInvoiceEntity updatedInv = invoiceMapper.selectById(invoiceId);
            if (updatedInv != null && updatedInv.getVoucherId() != null) {
                // 回写业务单据：凭证ID + 状态
                BusinessDocEntity doc = businessDocMapper.selectOne(
                        new LambdaQueryWrapper<BusinessDocEntity>()
                                .eq(BusinessDocEntity::getInvoiceNo, updatedInv.getInvoiceNo())
                                .eq(BusinessDocEntity::getDocType, "INVOICE_OUT")
                                .last("LIMIT 1"));
                if (doc != null) {
                    doc.setVoucherId(updatedInv.getVoucherId());
                    doc.setVoucherNo(updatedInv.getVoucherNo());
doc.setStatus("VOUCHERED");
                    doc.setUpdatedBy(userId);
                    businessDocMapper.updateById(doc);
                    log.info("P0 业务单据凭证状态回写: docId={}, voucherId={}, status=DRAFT",
                            doc.getId(), updatedInv.getVoucherId());

                    // P1: 回写凭证的 businessDocId
                    VoucherEntity v = new VoucherEntity();
                    v.setId(updatedInv.getVoucherId());
                    v.setBusinessDocId(doc.getId());
                    voucherMapper.updateById(v);
                    log.info("P1 凭证关联业务单据: voucherId={}, businessDocId={}",
                            updatedInv.getVoucherId(), doc.getId());
                }
            }

            // P36: 红字发票 → 回填凭证 reversedFrom
            if (isRedInvoice && invoice.getReversedFrom() != null) {
                OutputInvoiceEntity redInv = invoiceMapper.selectById(invoiceId);
                if (redInv != null && redInv.getVoucherId() != null) {
                    OutputInvoiceEntity blueInv = invoiceMapper.selectById(invoice.getReversedFrom());
                    if (blueInv != null && blueInv.getVoucherId() != null) {
                        VoucherEntity redVoucher = new VoucherEntity();
                        redVoucher.setId(redInv.getVoucherId());
                        redVoucher.setReversedFrom(blueInv.getVoucherId());
                        voucherMapper.updateById(redVoucher);
                        log.info("P36 红冲凭证关联: redVoucherId={}, blueVoucherId={}",
                                redInv.getVoucherId(), blueInv.getVoucherId());
                    }
                }
            }

            log.info("P33 销售发票凭证直连生成: invoiceId={}", invoiceId);
        } catch (Exception e) {
            log.error("P33 销售发票凭证生成失败: invoiceId={}, error={}", invoiceId, e.getMessage());
        }
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
