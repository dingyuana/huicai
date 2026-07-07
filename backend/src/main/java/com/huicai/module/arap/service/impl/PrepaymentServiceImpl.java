package com.huicai.module.arap.service.impl;

import cn.hutool.core.util.IdUtil;
import cn.hutool.core.util.StrUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.huicai.common.exception.BusinessException;
import com.huicai.module.arap.constant.ArapStatus;
import com.huicai.module.arap.entity.*;
import com.huicai.module.arap.mapper.ArapSettlementEntryMapper;
import com.huicai.module.arap.mapper.ArapSettlementMapper;
import com.huicai.module.arap.mapper.PrepaymentMapper;
import com.huicai.module.arap.service.ArapSettlementService;
import com.huicai.module.arap.service.PrepaymentService;
import com.huicai.module.finance.constant.VoucherType;
import com.huicai.module.finance.entity.BusinessDocEntity;
import com.huicai.module.finance.entity.VoucherEntity;
import com.huicai.module.finance.entity.VoucherEntryEntity;
import com.huicai.module.finance.mapper.BusinessDocMapper;
import com.huicai.module.finance.mapper.VoucherEntryMapper;
import com.huicai.module.finance.mapper.VoucherMapper;
import com.huicai.module.finance.service.VoucherNoService;
import com.huicai.module.system.entity.Subject;
import com.huicai.module.system.mapper.SubjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Objects;

/**
 * 预付款/预收款服务实现 — 管理预付账款完整生命周期.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class PrepaymentServiceImpl implements PrepaymentService {

    private static final long DEFAULT_TENANT_ID = 1L;
    private static final long DEFAULT_USER_ID = 1L;

    private static final String SUBJECT_PREPAY = "1123";
    private static final String SUBJECT_PAYABLE = "2202";
    private static final String SUBJECT_RECEIVABLE = "1122";
    private static final String SUBJECT_PREPAID_RECEIPT = "2203";

    private final PrepaymentMapper prepaymentMapper;
    private final BusinessDocMapper businessDocMapper;
    private final ArapSettlementService settlementService;
    private final ArapSettlementMapper settlementMapper;
    private final ArapSettlementEntryMapper settlementEntryMapper;
    private final VoucherMapper voucherMapper;
    private final VoucherEntryMapper voucherEntryMapper;
    private final VoucherNoService voucherNoService;
    private final SubjectMapper subjectMapper;

    @Override
    public IPage<PrepaymentEntity> pageQuery(Long vendorId, Long customerId, String status, Integer current, Integer size) {
        Page<PrepaymentEntity> page = new Page<>(
                current == null ? 1 : current,
                size == null ? 20 : size
        );
        LambdaQueryWrapper<PrepaymentEntity> wrapper = new LambdaQueryWrapper<PrepaymentEntity>()
                .eq(vendorId != null, PrepaymentEntity::getVendorId, vendorId)
                .eq(customerId != null, PrepaymentEntity::getCustomerId, customerId)
                .eq(StrUtil.isNotBlank(status), PrepaymentEntity::getStatus, status)
                .orderByDesc(PrepaymentEntity::getCreatedAt);
        return prepaymentMapper.selectPage(page, wrapper);
    }

    @Override
    public PrepaymentEntity getById(Long id) {
        PrepaymentEntity entity = prepaymentMapper.selectById(id);
        if (entity == null) {
            throw new BusinessException("预付款记录不存在: " + id);
        }
        return entity;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public PrepaymentEntity create(PrepaymentEntity entity) {
        if (entity.getTenantId() == null) entity.setTenantId(DEFAULT_TENANT_ID);
        if (entity.getStatus() == null) entity.setStatus(ArapStatus.DRAFT);
        if (entity.getSettledAmount() == null) entity.setSettledAmount(BigDecimal.ZERO);
        if (entity.getUnsettledAmount() == null) entity.setUnsettledAmount(entity.getAmount());
        if (entity.getTxDate() == null) entity.setTxDate(LocalDate.now());
        prepaymentMapper.insert(entity);
        log.info("预付款创建: id={}, vendorId={}, customerId={}, amount={}, status={}",
                entity.getId(), entity.getVendorId(), entity.getCustomerId(), entity.getAmount(), entity.getStatus());
        return entity;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public PrepaymentEntity confirm(Long id) {
        PrepaymentEntity entity = getById(id);
        if (!ArapStatus.isDraft(entity.getStatus())) {
            throw new BusinessException("仅草稿(DRAFT)状态的预付款可确认, 当前状态: " + entity.getStatus());
        }
        entity.setStatus(ArapStatus.CONFIRMED);
        prepaymentMapper.updateById(entity);
        log.info("预付款确认: id={}, vendorId={}, customerId={}, amount={}", id, entity.getVendorId(), entity.getCustomerId(), entity.getAmount());
        return entity;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public PrepaymentEntity applyToPayable(Long prepayId, Long payableId, BigDecimal applyAmount,
                                           String period, Long userId, String summary) {
        // 1. 校验预付款
        PrepaymentEntity prepay = getById(prepayId);
        if (!ArapStatus.isConfirmed(prepay.getStatus())) {
            throw new BusinessException("预付款状态必须为 CONFIRMED, 当前: " + prepay.getStatus());
        }
        if (prepay.getUnsettledAmount().compareTo(BigDecimal.ZERO) <= 0) {
            throw new BusinessException("预付款已全额核销, 无可抵扣余额");
        }

        // 2. 校验应付单（P34: 查询 BusinessDocEntity 替代 PayableEntity）
        BusinessDocEntity payable = businessDocMapper.selectById(payableId);
        if (payable == null) {
            throw new BusinessException("应付单不存在: " + payableId);
        }
        if (!prepay.getVendorId().equals(payable.getSupplierId())) {
            throw new BusinessException("预付款与应付单的供应商不一致: prepay.vendorId="
                    + prepay.getVendorId() + ", payable.supplierId=" + payable.getSupplierId());
        }
        if (payable.getUnsettledAmount().compareTo(BigDecimal.ZERO) <= 0) {
            throw new BusinessException("应付单已结清, 无可抵扣余额");
        }

        // 3. 确定抵扣金额
        if (applyAmount == null || applyAmount.compareTo(BigDecimal.ZERO) <= 0) {
            applyAmount = prepay.getUnsettledAmount().min(payable.getUnsettledAmount());
        }
        if (applyAmount.compareTo(prepay.getUnsettledAmount()) > 0) {
            throw new BusinessException("抵扣金额超过预付款未结余额: apply=" + applyAmount
                    + ", unsettled=" + prepay.getUnsettledAmount());
        }
        if (applyAmount.compareTo(payable.getUnsettledAmount()) > 0) {
            throw new BusinessException("抵扣金额超过应付单未结余额: apply=" + applyAmount
                    + ", unsettled=" + payable.getUnsettledAmount());
        }

        final BigDecimal finalApply = applyAmount;
        final Long operUserId = userId != null ? userId : DEFAULT_USER_ID;

        // 4. 更新预付款
        BigDecimal newSettled = prepay.getSettledAmount().add(finalApply);
        prepay.setSettledAmount(newSettled);
        prepay.setUnsettledAmount(prepay.getAmount().subtract(newSettled));
        if (prepay.getUnsettledAmount().compareTo(BigDecimal.ZERO) == 0) {
            prepay.setStatus(ArapStatus.APPLIED);
        }
        prepaymentMapper.updateById(prepay);

        // 5. 更新应付单（P34: 更新 BusinessDocEntity 替代 PayableEntity）
        BigDecimal payNewSettled = payable.getSettledAmount().add(finalApply);
        payable.setSettledAmount(payNewSettled);
        payable.setUnsettledAmount(payable.getAmount().subtract(payNewSettled));
        if (payable.getUnsettledAmount().compareTo(BigDecimal.ZERO) == 0
                && ArapStatus.isConfirmed(payable.getStatus())) {
            payable.setStatus(ArapStatus.SETTLED);
        }
        businessDocMapper.updateById(payable);

        // 6. 创建核销单 (ArapSettlement)
        String effectivePeriod = period != null ? period
                : LocalDate.now().format(java.time.format.DateTimeFormatter.ofPattern("yyyyMM"));
        String effectiveSummary = summary != null ? summary : "预付冲应付";

        ArapSettlementEntity settlement = new ArapSettlementEntity();
        settlement.setSettlementType("PAYABLE");
        settlement.setSettlementDate(LocalDate.now());
        settlement.setPeriod(effectivePeriod);
        settlement.setPartyId(prepay.getVendorId());
        settlement.setPartyType("VENDOR");
        settlement.setTotalAmount(finalApply);
        settlement.setDiscountAmount(BigDecimal.ZERO);
        settlement.setStatus(ArapStatus.DRAFT);
        String prefix = "YF";
        settlement.setSettlementNo(prefix + "-" + effectivePeriod + "-"
                + IdUtil.fastSimpleUUID().substring(0, 6).toUpperCase());
        settlement.setCreatedBy(operUserId);
        settlementMapper.insert(settlement);

        ArapSettlementEntryEntity entry = new ArapSettlementEntryEntity();
        entry.setSettlementId(settlement.getId());
        entry.setPayableId(payableId);
        entry.setSettledAmount(finalApply);
        entry.setDiscountAmount(BigDecimal.ZERO);
        settlementEntryMapper.insert(entry);

        // 确认核销单 (更新应付已核销金额 — 我们已手动更新, 此处仅改状态)
        settlement.setStatus(ArapStatus.CONFIRMED);
        settlementMapper.updateById(settlement);

        // 7. 创建凭证 (借:应付账款 / 贷:预付账款)
        String voucherNo = voucherNoService.generateNextNo(effectivePeriod, VoucherType.FK);
        VoucherEntity voucher = new VoucherEntity();
        voucher.setVoucherNo(voucherNo);
        voucher.setPeriod(effectivePeriod);
        voucher.setVoucherTypeId(VoucherType.FK);
        voucher.setStatus("DRAFT");
        voucher.setSource("GENERATED");
        voucher.setSummary(effectiveSummary);
        voucher.setTotalDebit(finalApply);
        voucher.setTotalCredit(finalApply);
        voucher.setCreatedBy(operUserId);
        voucherMapper.insert(voucher);

        // 查找科目
        Long payableSubjectId = findSubjectIdByCode(SUBJECT_PAYABLE);
        Long prepaySubjectId = findSubjectIdByCode(SUBJECT_PREPAY);

        // 分录 1: 借 应付账款
        VoucherEntryEntity entryDr = new VoucherEntryEntity();
        entryDr.setVoucherId(voucher.getId());
        entryDr.setSubjectId(payableSubjectId);
        entryDr.setDebit(finalApply);
        entryDr.setCredit(BigDecimal.ZERO);
        entryDr.setSummary(effectiveSummary);
        entryDr.setSortOrder(1);
        voucherEntryMapper.insert(entryDr);

        // 分录 2: 贷 预付账款
        VoucherEntryEntity entryCr = new VoucherEntryEntity();
        entryCr.setVoucherId(voucher.getId());
        entryCr.setSubjectId(prepaySubjectId);
        entryCr.setDebit(BigDecimal.ZERO);
        entryCr.setCredit(finalApply);
        entryCr.setSummary(effectiveSummary);
        entryCr.setSortOrder(2);
        voucherEntryMapper.insert(entryCr);

        voucherMapper.updateById(voucher);

        // 关联核销单与凭证
        settlement.setVoucherId(voucher.getId());
        settlementMapper.updateById(settlement);

        log.info("预付冲应付完成: prepayId={}, payableId={}, amount={}, voucherId={}, settlementId={}",
                prepayId, payableId, finalApply, voucher.getId(), settlement.getId());

        return prepay;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public PrepaymentEntity applyToReceivable(Long prepayId, Long receivableId, BigDecimal applyAmount,
                                              String period, Long userId, String summary) {
        PrepaymentEntity prepay = getById(prepayId);
        if (!ArapStatus.isConfirmed(prepay.getStatus())) {
            throw new BusinessException("预收款状态必须为 CONFIRMED, 当前: " + prepay.getStatus());
        }
        if (prepay.getUnsettledAmount().compareTo(BigDecimal.ZERO) <= 0) {
            throw new BusinessException("预收款已全额核销, 无可抵扣余额");
        }

        // P34: 查询 BusinessDocEntity 替代 ReceivableEntity
        BusinessDocEntity receivable = businessDocMapper.selectById(receivableId);
        if (receivable == null) {
            throw new BusinessException("应收单不存在: " + receivableId);
        }
        if (!Objects.equals(prepay.getCustomerId(), receivable.getCustomerId())) {
            throw new BusinessException("预收款与应收单的客户不一致: prepay.customerId="
                    + prepay.getCustomerId() + ", receivable.customerId=" + receivable.getCustomerId());
        }
        if (receivable.getUnsettledAmount().compareTo(BigDecimal.ZERO) <= 0) {
            throw new BusinessException("应收单已结清, 无可抵扣余额");
        }

        if (applyAmount == null || applyAmount.compareTo(BigDecimal.ZERO) <= 0) {
            applyAmount = prepay.getUnsettledAmount().min(receivable.getUnsettledAmount());
        }
        if (applyAmount.compareTo(prepay.getUnsettledAmount()) > 0) {
            throw new BusinessException("抵扣金额超过预收款未结余额: apply=" + applyAmount
                    + ", unsettled=" + prepay.getUnsettledAmount());
        }
        if (applyAmount.compareTo(receivable.getUnsettledAmount()) > 0) {
            throw new BusinessException("抵扣金额超过应收单未结余额: apply=" + applyAmount
                    + ", unsettled=" + receivable.getUnsettledAmount());
        }

        final BigDecimal finalApply = applyAmount;
        final Long operUserId = userId != null ? userId : DEFAULT_USER_ID;

        BigDecimal newSettled = prepay.getSettledAmount().add(finalApply);
        prepay.setSettledAmount(newSettled);
        prepay.setUnsettledAmount(prepay.getAmount().subtract(newSettled));
        if (prepay.getUnsettledAmount().compareTo(BigDecimal.ZERO) == 0) {
            prepay.setStatus(ArapStatus.APPLIED);
        }
        prepaymentMapper.updateById(prepay);

        // P34: 更新 BusinessDocEntity 替代 ReceivableEntity
        BigDecimal recNewSettled = receivable.getSettledAmount().add(finalApply);
        receivable.setSettledAmount(recNewSettled);
        receivable.setUnsettledAmount(receivable.getAmount().subtract(recNewSettled));
        if (receivable.getUnsettledAmount().compareTo(BigDecimal.ZERO) == 0
                && ArapStatus.isConfirmed(receivable.getStatus())) {
            receivable.setStatus(ArapStatus.SETTLED);
        }
        businessDocMapper.updateById(receivable);

        String effectivePeriod = period != null ? period
                : LocalDate.now().format(java.time.format.DateTimeFormatter.ofPattern("yyyyMM"));
        String effectiveSummary = summary != null ? summary : "预收冲应收";

        ArapSettlementEntity settlement = new ArapSettlementEntity();
        settlement.setSettlementType("RECEIVABLE");
        settlement.setSettlementDate(LocalDate.now());
        settlement.setPeriod(effectivePeriod);
        settlement.setPartyId(prepay.getCustomerId());
        settlement.setPartyType("CUSTOMER");
        settlement.setTotalAmount(finalApply);
        settlement.setDiscountAmount(BigDecimal.ZERO);
        settlement.setStatus(ArapStatus.DRAFT);
        String prefix = "YS";
        settlement.setSettlementNo(prefix + "-" + effectivePeriod + "-"
                + IdUtil.fastSimpleUUID().substring(0, 6).toUpperCase());
        settlement.setCreatedBy(operUserId);
        settlementMapper.insert(settlement);

        ArapSettlementEntryEntity entry = new ArapSettlementEntryEntity();
        entry.setSettlementId(settlement.getId());
        entry.setReceivableId(receivableId);
        entry.setSettledAmount(finalApply);
        entry.setDiscountAmount(BigDecimal.ZERO);
        settlementEntryMapper.insert(entry);

        settlement.setStatus(ArapStatus.CONFIRMED);
        settlementMapper.updateById(settlement);

        String voucherNo = voucherNoService.generateNextNo(effectivePeriod, VoucherType.SK);
        VoucherEntity voucher = new VoucherEntity();
        voucher.setVoucherNo(voucherNo);
        voucher.setPeriod(effectivePeriod);
        voucher.setVoucherTypeId(VoucherType.SK);
        voucher.setStatus("DRAFT");
        voucher.setSource("GENERATED");
        voucher.setSummary(effectiveSummary);
        voucher.setTotalDebit(finalApply);
        voucher.setTotalCredit(finalApply);
        voucher.setCreatedBy(operUserId);
        voucherMapper.insert(voucher);

        Long prepaidReceiptSubjectId = findSubjectIdByCode(SUBJECT_PREPAID_RECEIPT);
        Long receivableSubjectId = findSubjectIdByCode(SUBJECT_RECEIVABLE);

        VoucherEntryEntity entryDr = new VoucherEntryEntity();
        entryDr.setVoucherId(voucher.getId());
        entryDr.setSubjectId(prepaidReceiptSubjectId);
        entryDr.setDebit(finalApply);
        entryDr.setCredit(BigDecimal.ZERO);
        entryDr.setSummary(effectiveSummary);
        entryDr.setSortOrder(1);
        voucherEntryMapper.insert(entryDr);

        VoucherEntryEntity entryCr = new VoucherEntryEntity();
        entryCr.setVoucherId(voucher.getId());
        entryCr.setSubjectId(receivableSubjectId);
        entryCr.setDebit(BigDecimal.ZERO);
        entryCr.setCredit(finalApply);
        entryCr.setSummary(effectiveSummary);
        entryCr.setSortOrder(2);
        voucherEntryMapper.insert(entryCr);

        voucherMapper.updateById(voucher);

        settlement.setVoucherId(voucher.getId());
        settlementMapper.updateById(settlement);

        log.info("预收冲应收完成: prepayId={}, receivableId={}, amount={}, voucherId={}, settlementId={}",
                prepayId, receivableId, finalApply, voucher.getId(), settlement.getId());

        return prepay;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void reverse(Long id, Long userId, String reason) {
        PrepaymentEntity prepay = getById(id);
        if (!ArapStatus.isConfirmed(prepay.getStatus()) && !ArapStatus.APPLIED.equals(prepay.getStatus())) {
            throw new BusinessException("仅 CONFIRMED 或 APPLIED 状态的预付款可反冲, 当前: " + prepay.getStatus());
        }
        if (StrUtil.isBlank(reason)) {
            throw new BusinessException("反冲必须填写原因");
        }

        // 恢复未结金额
        prepay.setSettledAmount(BigDecimal.ZERO);
        prepay.setUnsettledAmount(prepay.getAmount());
        prepay.setStatus(ArapStatus.REVERSED);
        prepay.setUpdatedAt(LocalDate.now());
        prepaymentMapper.updateById(prepay);

        log.info("预付款反冲: id={}, vendorId={}, customerId={}, amount={}, reason={}", id, prepay.getVendorId(),
                prepay.getCustomerId(), prepay.getAmount(), reason);
    }

    @Override
    public List<PrepaymentEntity> getOpenPrepayments(Long vendorId) {
        return prepaymentMapper.selectList(
                new LambdaQueryWrapper<PrepaymentEntity>()
                        .eq(PrepaymentEntity::getVendorId, vendorId)
                        .gt(PrepaymentEntity::getUnsettledAmount, BigDecimal.ZERO)
                        .eq(PrepaymentEntity::getStatus, ArapStatus.CONFIRMED)
                        .orderByAsc(PrepaymentEntity::getCreatedAt)
        );
    }

    @Override
    public List<PrepaymentEntity> getOpenPrepaymentsForCustomer(Long customerId) {
        return prepaymentMapper.selectList(
                new LambdaQueryWrapper<PrepaymentEntity>()
                        .eq(PrepaymentEntity::getCustomerId, customerId)
                        .gt(PrepaymentEntity::getUnsettledAmount, BigDecimal.ZERO)
                        .eq(PrepaymentEntity::getStatus, ArapStatus.CONFIRMED)
                        .orderByAsc(PrepaymentEntity::getCreatedAt)
        );
    }

    private Long findSubjectIdByCode(String code) {
        List<Subject> list = subjectMapper.selectList(
                new LambdaQueryWrapper<Subject>().eq(Subject::getCode, code).last("LIMIT 1"));
        if (list.isEmpty()) {
            throw new BusinessException("科目编码不存在: " + code + ", 请先配置科目");
        }
        return list.get(0).getId();
    }
}
