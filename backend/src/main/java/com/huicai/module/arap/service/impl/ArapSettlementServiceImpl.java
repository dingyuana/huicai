package com.huicai.module.arap.service.impl;

import cn.hutool.core.util.IdUtil;
import cn.hutool.core.util.StrUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.huicai.common.exception.BusinessException;
import com.huicai.module.arap.constant.ArapStatus;
import com.huicai.module.arap.dto.vo.ArapSettlementVO;
import com.huicai.module.arap.entity.ArapSettlementEntity;
import com.huicai.module.arap.entity.ArapSettlementEntryEntity;
import com.huicai.module.arap.entity.ReconciliationLogEntity;
import com.huicai.module.arap.mapper.ArapSettlementEntryMapper;
import com.huicai.module.arap.mapper.ArapSettlementMapper;
import com.huicai.module.arap.mapper.CustomerMapper;
import com.huicai.module.arap.mapper.ReconciliationLogMapper;
import com.huicai.module.arap.mapper.VendorMapper;
import com.huicai.module.arap.service.ArapSettlementService;
import com.huicai.module.finance.constant.BusinessDocStatus;
import com.huicai.module.finance.constant.VoucherType;
import com.huicai.module.finance.entity.BusinessDocEntity;
import com.huicai.module.finance.entity.VoucherEntity;
import com.huicai.module.finance.entity.VoucherEntryEntity;
import com.huicai.module.finance.mapper.BusinessDocMapper;
import com.huicai.module.finance.mapper.VoucherEntryMapper;
import com.huicai.module.finance.mapper.VoucherMapper;
import com.huicai.module.finance.service.VoucherNoService;
import com.huicai.module.finance.service.VoucherTemplateService;
import com.huicai.module.finance.entity.VoucherTemplateEntity;
import com.huicai.module.finance.entity.VoucherTemplateLineEntity;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.retry.annotation.Retryable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;

@Service
@RequiredArgsConstructor
public class ArapSettlementServiceImpl implements ArapSettlementService {

    private static final Logger log = LoggerFactory.getLogger(ArapSettlementServiceImpl.class);
    private static final long DEFAULT_USER_ID = 0L;

    private final ArapSettlementMapper mapper;
    private final ArapSettlementEntryMapper entryMapper;
    private final BusinessDocMapper businessDocMapper;
    private final CustomerMapper customerMapper;
    private final VendorMapper vendorMapper;
    private final VoucherTemplateService voucherTemplateService;
    private final VoucherMapper voucherMapper;
    private final VoucherEntryMapper voucherEntryMapper;
    private final VoucherNoService voucherNoService;
    private final ReconciliationLogMapper logMapper;

    @Override
    public IPage<ArapSettlementEntity> pageQuery(String status, String voucherNo, Integer current, Integer size) {
        Page<ArapSettlementEntity> page = new Page<>(
                current == null ? 1 : current,
                size == null ? 20 : size
        );
        LambdaQueryWrapper<ArapSettlementEntity> wrapper = new LambdaQueryWrapper<>();
        if (StrUtil.isNotBlank(status)) {
            wrapper.eq(ArapSettlementEntity::getStatus, status);
        }
        if (StrUtil.isNotBlank(voucherNo)) {
            wrapper.eq(ArapSettlementEntity::getVoucherNo, voucherNo);
        }
        wrapper.orderByDesc(ArapSettlementEntity::getCreatedAt);
        return mapper.selectPage(page, wrapper);
    }

    @Override
    public IPage<ArapSettlementVO> pageQueryWithPartyName(String status, String voucherNo, Integer current, Integer size) {
        Page<ArapSettlementVO> page = new Page<>(current == null ? 1 : current, size == null ? 20 : size);
        return mapper.pageWithPartyName(page);
    }

    @Override
    public ArapSettlementEntity getById(Long id) {
        ArapSettlementEntity entity = mapper.selectById(id);
        if (entity == null) {
            throw new BusinessException("核销单不存在");
        }
        return entity;
    }

    @Override
    public ArapSettlementVO getDetailWithPartyName(Long id) {
        ArapSettlementEntity entity = mapper.selectById(id);
        if (entity == null) throw new BusinessException("核销单不存在");
        ArapSettlementVO vo = new ArapSettlementVO();
        org.springframework.beans.BeanUtils.copyProperties(entity, vo);
        if (entity.getPartyId() != null && "CUSTOMER".equals(entity.getPartyType())) {
            com.huicai.module.arap.entity.CustomerEntity customer = customerMapper.selectById(entity.getPartyId());
            if (customer != null) vo.setCustomerName(customer.getName());
        } else if (entity.getPartyId() != null && "VENDOR".equals(entity.getPartyType())) {
            com.huicai.module.arap.entity.VendorEntity vendor = vendorMapper.selectById(entity.getPartyId());
            if (vendor != null) vo.setVendorName(vendor.getName());
        }
        return vo;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public ArapSettlementEntity create(ArapSettlementEntity entity, List<ArapSettlementEntryEntity> entries) {
        if (StrUtil.isBlank(entity.getSettlementNo())) {
            String prefix = "RECEIVE".equals(entity.getSettlementType()) ? "JS" : "FS";
            entity.setSettlementNo(prefix + "-" + entity.getPeriod() + "-" + IdUtil.fastSimpleUUID().substring(0, 6).toUpperCase());
        }
        if (entity.getStatus() == null) entity.setStatus(ArapStatus.DRAFT);
        if (entity.getDiscountAmount() == null) entity.setDiscountAmount(BigDecimal.ZERO);
        BigDecimal total = BigDecimal.ZERO;
        for (ArapSettlementEntryEntity entry : entries) {
            total = total.add(entry.getSettledAmount());
        }
        entity.setTotalAmount(total);
        mapper.insert(entity);

        for (ArapSettlementEntryEntity entry : entries) {
            entry.setSettlementId(entity.getId());
            entryMapper.insert(entry);
        }
        return entity;
    }

    @Override
    @Retryable(retryFor = OptimisticLockingFailureException.class, maxAttempts = 3, backoff = @org.springframework.retry.annotation.Backoff(delay = 100))
    @Transactional(rollbackFor = Exception.class)
    public ArapSettlementEntity confirm(Long id) {
        // Backward compat: 旧接口 call submit→approve 串联
        ArapSettlementEntity entity = getById(id);
        if (ArapStatus.DRAFT.equals(entity.getStatus())) {
            submit(id); // DRAFT → SUBMITTED
        }
        return approve(id); // SUBMITTED → CONFIRMED
    }

    @Override
    public void delete(Long id) {
        ArapSettlementEntity entity = getById(id);
        if (!ArapStatus.isDraft(entity.getStatus())) {
            throw new BusinessException("仅草稿状态可删除");
        }
        entryMapper.delete(new LambdaQueryWrapper<ArapSettlementEntryEntity>()
                .eq(ArapSettlementEntryEntity::getSettlementId, id));
        mapper.deleteById(id);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void submit(Long id) {
        ArapSettlementEntity entity = getById(id);
        if (!ArapStatus.isSubmitable(entity.getStatus())) {
            throw new BusinessException("核销单状态不允许提交: " + entity.getStatus());
        }
        entity.setStatus(ArapStatus.SUBMITTED);
        mapper.updateById(entity);
        logReconciliationLog(entity, "SUBMIT", null, null, DEFAULT_USER_ID);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public ArapSettlementEntity approve(Long id) {
        ArapSettlementEntity entity = getById(id);
        if (!ArapStatus.isApprovable(entity.getStatus())) {
            throw new BusinessException("核销单状态不允许审批: " + entity.getStatus());
        }
        // 复用原 confirm 逻辑：更新明细对应应收/应付的已核销金额
        List<ArapSettlementEntryEntity> entries = entryMapper.selectList(
                new LambdaQueryWrapper<ArapSettlementEntryEntity>()
                        .eq(ArapSettlementEntryEntity::getSettlementId, id)
        );
        for (ArapSettlementEntryEntity entry : entries) {
            if (entry.getBusinessDocId() != null) {
                BusinessDocEntity doc = businessDocMapper.selectById(entry.getBusinessDocId());
                if (doc != null) {
                    // P30-C5: 仅已审批状态的业务单据可核销
                    if (!"APPROVED".equals(doc.getStatus())) {
                        throw BusinessException.badRequest("仅已审批状态的业务单据可核销，当前状态: " + doc.getStatus());
                    }
                    BigDecimal newSettled = doc.getSettledAmount() != null
                            ? doc.getSettledAmount().add(entry.getSettledAmount())
                            : entry.getSettledAmount();
                    doc.setSettledAmount(newSettled);
                    doc.setUnsettledAmount(doc.getAmount().subtract(newSettled));
                    doc.setStatus(doc.getUnsettledAmount().compareTo(BigDecimal.ZERO) == 0
                            ? "FULLY_RECONCILED" : "PARTIALLY_RECONCILED");
                    if (businessDocMapper.updateById(doc) == 0) {
                        throw new OptimisticLockingFailureException("BusinessDoc确认版本冲突, id=" + doc.getId());
                    }
                }
            } else if (entry.getReceivableId() != null) {
                throw new BusinessException("核销明细仍使用旧格式(receivableId)，请迁移至 businessDocId: id=" + entry.getReceivableId());
            } else if (entry.getPayableId() != null) {
                throw new BusinessException("核销明细仍使用旧格式(payableId)，请迁移至 businessDocId: id=" + entry.getPayableId());
            }
        }
        entity.setStatus(ArapStatus.CONFIRMED);
        mapper.updateById(entity);
        logReconciliationLog(entity, "APPROVE", null, null, DEFAULT_USER_ID);
        return entity;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void reject(Long id) {
        reject(id, null);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void reject(Long id, String reason) {
        ArapSettlementEntity entity = getById(id);
        if (!ArapStatus.isRejectable(entity.getStatus())) {
            throw new BusinessException("核销单状态不允许驳回: " + entity.getStatus());
        }
        entity.setStatus(ArapStatus.REJECTED);
        mapper.updateById(entity);
        logReconciliationLog(entity, "REJECT", reason, null, DEFAULT_USER_ID);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void cancel(Long id) {
        ArapSettlementEntity entity = getById(id);
        if (!ArapStatus.isCancellable(entity.getStatus())) {
            throw new BusinessException("核销单状态不允许取消: " + entity.getStatus());
        }
        entity.setStatus(ArapStatus.CANCELLED);
        mapper.updateById(entity);
        logReconciliationLog(entity, "CANCEL", null, null, DEFAULT_USER_ID);
    }

    @Override
    public List<ArapSettlementEntryEntity> getEntries(Long settlementId) {
        return entryMapper.selectList(
                new LambdaQueryWrapper<ArapSettlementEntryEntity>()
                        .eq(ArapSettlementEntryEntity::getSettlementId, settlementId)
        );
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void generateVoucher(Long id) {
        ArapSettlementEntity entity = getById(id);
        if (!ArapStatus.canTransition(entity.getStatus(), ArapStatus.VOUCHERED)) {
            throw new BusinessException("核销单状态不允许生成凭证: " + entity.getStatus());
        }
        if (entity.getVoucherId() != null) {
            throw new BusinessException("该核销单已生成凭证, voucherId=" + entity.getVoucherId());
        }

        // 获取核销明细（用于回写应收/应付单的 voucherNo）
        List<ArapSettlementEntryEntity> entries = entryMapper.selectList(
                new LambdaQueryWrapper<ArapSettlementEntryEntity>()
                        .eq(ArapSettlementEntryEntity::getSettlementId, id)
        );

        String classifySuffix;
        if (entity.getSettlementType() == null) {
            classifySuffix = "receivable";
        } else {
            switch (entity.getSettlementType().toLowerCase()) {
                case "receive": classifySuffix = "receivable"; break;
                case "pay":     classifySuffix = "payment";    break;
                default:        classifySuffix = entity.getSettlementType().toLowerCase();
            }
        }
        String classification = "settlement_" + classifySuffix;
        VoucherTemplateEntity template = voucherTemplateService.matchByClassification(classification);
        List<VoucherTemplateLineEntity> lines = template != null
                ? voucherTemplateService.getLines(template.getId()) : null;

        String period = entity.getPeriod() != null ? entity.getPeriod()
                : java.time.LocalDate.now().format(java.time.format.DateTimeFormatter.ofPattern("yyyyMM"));
        long voucherTypeId = "payment".equals(classifySuffix) ? VoucherType.FK : VoucherType.SK;
        String voucherNo = voucherNoService.generateNextNo(period, voucherTypeId);

        VoucherEntity voucher = new VoucherEntity();
        voucher.setVoucherNo(voucherNo);
        voucher.setPeriod(period);
        voucher.setVoucherTypeId(voucherTypeId);
        voucher.setStatus("DRAFT");
        voucher.setSource("GENERATED");
        voucher.setSummary("往来核销生成 — " + (entity.getSettlementNo() != null ? entity.getSettlementNo() : ""));
        if (template != null) voucher.setTemplateId(template.getId());
        voucher.setCreatedBy(DEFAULT_USER_ID);
        // 新增：溯源字段（核销单 → 凭证）
        voucher.setSourceDocType("SETTLEMENT");
        voucher.setSourceDocNo(entity.getSettlementNo());
        voucherMapper.insert(voucher);

        BigDecimal total = BigDecimal.ZERO;
        int sort = 1;

        if (lines == null || lines.isEmpty()) {
            throw new BusinessException("未找到核销单凭证模板(classification=" + classification
                    + "), 请先在凭证模板中配置。可在 V40 迁移中添加 settlement_receivable/settlement_payment 模板");
        }

        for (VoucherTemplateLineEntity line : lines) {
            BigDecimal amount = entity.getTotalAmount();
            BigDecimal dr = "debit".equals(line.getDirection()) ? amount : BigDecimal.ZERO;
            BigDecimal cr = "credit".equals(line.getDirection()) ? amount : BigDecimal.ZERO;
            if (dr.compareTo(BigDecimal.ZERO) == 0 && cr.compareTo(BigDecimal.ZERO) == 0) continue;

            String summary = line.getSummaryTemplate() != null
                    ? line.getSummaryTemplate().replace("{{settlementNo}}", entity.getSettlementNo() != null ? entity.getSettlementNo() : "")
                    : "往来核销";
            VoucherEntryEntity ve = new VoucherEntryEntity();
            ve.setVoucherId(voucher.getId());
            ve.setSubjectId(line.getSubjectId());
            ve.setDebit(dr);
            ve.setCredit(cr);
            ve.setSummary(summary);
            ve.setSortOrder(sort++);
            voucherEntryMapper.insert(ve);
            total = total.add(dr).add(cr);
        }

        BigDecimal maxAmt = total.divide(BigDecimal.valueOf(2), 2, java.math.RoundingMode.HALF_UP);
        voucher.setTotalDebit(maxAmt);
        voucher.setTotalCredit(maxAmt);
        voucherMapper.updateById(voucher);

        entity.setVoucherId(voucher.getId());
        entity.setVoucherNo(voucherNo);      // 新增：回写凭证编号到核销单
        entity.setStatus(ArapStatus.VOUCHERED);
        mapper.updateById(entity);
        logReconciliationLog(entity, "GENERATE_VOUCHER", null, voucherNo, DEFAULT_USER_ID);

        // 新增：回写凭证编号到所有核销明细对应的业务单据/应收/应付单
        for (ArapSettlementEntryEntity entry : entries) {
            if (entry.getBusinessDocId() != null) {
                BusinessDocEntity doc = businessDocMapper.selectById(entry.getBusinessDocId());
                if (doc != null && doc.getVoucherNo() == null) {
                    doc.setVoucherNo(voucherNo);
                    doc.setVoucherId(voucher.getId());
                    doc.setStatus(BusinessDocStatus.VOUCHERED); // P38-F8: 推进BusinessDoc状态
                    if (businessDocMapper.updateById(doc) == 0) {
                        throw new OptimisticLockingFailureException("BusinessDoc回写凭证版本冲突, id=" + doc.getId());
                    }
                }
            } else if (entry.getReceivableId() != null) {
                // P34 过渡期：应收单尚未迁移到 BusinessDoc，跳过凭证编号回写
                log.debug("核销凭证回写跳过应收单: receivableId={}", entry.getReceivableId());
            } else if (entry.getPayableId() != null) {
                // P34 过渡期：应付单尚未迁移到 BusinessDoc，跳过凭证编号回写
                log.debug("核销凭证回写跳过应付单: payableId={}", entry.getPayableId());
            }
        }

        log.info("核销单生成凭证: settlementId={}, voucherId={}, voucherNo={}, amount={}",
                id, voucher.getId(), voucherNo, entity.getTotalAmount());
    }

    @Override
    @Retryable(retryFor = OptimisticLockingFailureException.class, maxAttempts = 3, backoff = @org.springframework.retry.annotation.Backoff(delay = 100))
    @Transactional(rollbackFor = Exception.class)
    public void reverse(Long id) {
        ArapSettlementEntity entity = getById(id);
        if (!ArapStatus.isSettlementReversible(entity.getStatus())) {
            throw new BusinessException("仅已确认或已记账的核销单可反核销, 当前: " + entity.getStatus());
        }
        // 创建对冲核销单（红冲）— 对齐 Voucher 红冲模式
        ArapSettlementEntity reverseSettlement = new ArapSettlementEntity();
        reverseSettlement.setSettlementNo(entity.getSettlementNo() + "-H");
        reverseSettlement.setSettlementType("REVERSAL");
        reverseSettlement.setSettlementDate(entity.getSettlementDate());
        reverseSettlement.setPeriod(entity.getPeriod());
        reverseSettlement.setPartyId(entity.getPartyId());
        reverseSettlement.setPartyType(entity.getPartyType());
        reverseSettlement.setTotalAmount(entity.getTotalAmount().negate()); // 金额取负
        reverseSettlement.setDiscountAmount(entity.getDiscountAmount());
        reverseSettlement.setStatus(ArapStatus.DRAFT);
        reverseSettlement.setReversedFromSettlementId(id);
        reverseSettlement.setCreatedBy(DEFAULT_USER_ID);
        mapper.insert(reverseSettlement);

        // 复制原核销明细，金额取负
        List<ArapSettlementEntryEntity> entries = entryMapper.selectList(
                new LambdaQueryWrapper<ArapSettlementEntryEntity>()
                        .eq(ArapSettlementEntryEntity::getSettlementId, id)
        );
        for (ArapSettlementEntryEntity entry : entries) {
            ArapSettlementEntryEntity reverseEntry = new ArapSettlementEntryEntity();
            reverseEntry.setSettlementId(reverseSettlement.getId());
            reverseEntry.setBusinessDocId(entry.getBusinessDocId());
            reverseEntry.setReceivableId(entry.getReceivableId());
            reverseEntry.setPayableId(entry.getPayableId());
            reverseEntry.setSettledAmount(entry.getSettledAmount().negate()); // 金额取负
            reverseEntry.setBeforeBalance(entry.getBeforeBalance());
            reverseEntry.setAfterBalance(entry.getAfterBalance());
            entryMapper.insert(reverseEntry);
        }

        // 原核销单状态改为 REVERSED
        entity.setStatus(ArapStatus.REVERSED);
        mapper.updateById(entity);
        logReconciliationLog(entity, "REVERSE", "红冲反核销，创建对冲单据 id=" + reverseSettlement.getId(), null, DEFAULT_USER_ID);
    }

    private void restoreUnsettledAmount(ArapSettlementEntryEntity entry) {
        if (entry.getBusinessDocId() != null) {
            BusinessDocEntity doc = businessDocMapper.selectById(entry.getBusinessDocId());
            if (doc != null) {
                BigDecimal newSettled = doc.getSettledAmount().subtract(entry.getSettledAmount());
                doc.setSettledAmount(newSettled);
                doc.setUnsettledAmount(doc.getAmount().subtract(newSettled));
                doc.setStatus("APPROVED");
                if (businessDocMapper.updateById(doc) == 0) {
                    throw new OptimisticLockingFailureException("BusinessDoc反核销版本冲突, id=" + doc.getId());
                }
            }
        } else if (entry.getReceivableId() != null) {
            log.debug("反核销跳过应收单: receivableId={}", entry.getReceivableId());
        } else if (entry.getPayableId() != null) {
            log.debug("反核销跳过应付单: payableId={}", entry.getPayableId());
        }
    }

    /**
     * 记录核销日志到 t_reconciliation_log。
     */
    private void logReconciliationLog(ArapSettlementEntity settlement, String operationType,
                                      String remark, String voucherNo, Long userId) {
        try {
            ReconciliationLogEntity logEntity = new ReconciliationLogEntity();
            logEntity.setSourceDocType("SETTLEMENT");
            logEntity.setSourceDocId(settlement.getId());
            logEntity.setTargetDocType(null);
            logEntity.setTargetDocId(null);
            logEntity.setAllocatedAmount(settlement.getTotalAmount());
            logEntity.setMatchScore(BigDecimal.ONE);
            logEntity.setMatchMethod("MANUAL");
            logEntity.setStatus(settlement.getStatus());
            logEntity.setOperationType(operationType);
            logEntity.setRemark(remark);
            logEntity.setCreatedBy(userId);
            logMapper.insert(logEntity);
            log.info("核销日志已写入: settlementId={}, operationType={}, status={}",
                    settlement.getId(), operationType, settlement.getStatus());
        } catch (Exception e) {
            log.warn("写入核销日志失败: settlementId={}, error={}", settlement.getId(), e.getMessage());
        }
    }
}
