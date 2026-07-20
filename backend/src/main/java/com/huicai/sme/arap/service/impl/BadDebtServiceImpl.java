package com.huicai.sme.arap.service.impl;

import cn.hutool.core.util.IdUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.huicai.common.exception.BusinessException;
import com.huicai.sme.arap.constant.ArapStatus;
import com.huicai.sme.arap.entity.BadDebtProvisionDetailEntity;
import com.huicai.sme.arap.entity.BadDebtProvisionEntity;
import com.huicai.sme.arap.entity.BadDebtProvisionSchemeEntity;
import com.huicai.sme.arap.entity.BadDebtProvisionSchemeItemEntity;
import com.huicai.base.business.entity.BusinessDocEntity;
import com.huicai.sme.arap.entity.PrepaymentEntity;
import com.huicai.sme.arap.mapper.BadDebtProvisionDetailMapper;
import com.huicai.sme.arap.mapper.BadDebtProvisionMapper;
import com.huicai.sme.arap.mapper.BadDebtProvisionSchemeMapper;
import com.huicai.sme.arap.mapper.BadDebtProvisionSchemeItemMapper;
import com.huicai.base.business.mapper.BusinessDocMapper;
import com.huicai.sme.arap.mapper.PrepaymentMapper;
import com.huicai.sme.arap.service.BadDebtService;
import com.huicai.sme.arap.service.CustomerStatementService;
import com.huicai.base.voucher.dto.VoucherCreateDTO;
import com.huicai.base.voucher.dto.VoucherCreateDTO.EntryDTO;
import com.huicai.base.business.entity.BusinessDocEntity;
import com.huicai.base.voucher.entity.VoucherEntity;
import com.huicai.base.business.mapper.BusinessDocMapper;
import com.huicai.base.voucher.mapper.VoucherEntryMapper;
import com.huicai.base.voucher.service.VoucherService;
import com.huicai.base.system.entity.Subject;
import com.huicai.base.system.mapper.SubjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.*;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class BadDebtServiceImpl implements BadDebtService {

    private final BadDebtProvisionMapper mapper;
    private final BadDebtProvisionDetailMapper detailMapper;
    private final BadDebtProvisionSchemeMapper schemeMapper;
    private final BadDebtProvisionSchemeItemMapper schemeItemMapper;
    private final BusinessDocMapper businessDocMapper;
    private final PrepaymentMapper prepaymentMapper;
    private final CustomerStatementService customerStatementService;
    private final VoucherService voucherService;
    private final VoucherEntryMapper voucherEntryMapper;
    private final SubjectMapper subjectMapper;

    // ==================== 公共 ====================

    @Override
    public IPage<BadDebtProvisionEntity> pageQuery(String status, Integer current, Integer size) {
        Page<BadDebtProvisionEntity> page = new Page<>(
                current == null ? 1 : current,
                size == null ? 20 : size
        );
        LambdaQueryWrapper<BadDebtProvisionEntity> wrapper = new LambdaQueryWrapper<>();
        if (status != null && !status.isBlank()) {
            wrapper.eq(BadDebtProvisionEntity::getStatus, status);
        }
        wrapper.orderByDesc(BadDebtProvisionEntity::getCreatedAt);
        return mapper.selectPage(page, wrapper);
    }

    @Override
    public BadDebtProvisionEntity getById(Long id) {
        BadDebtProvisionEntity entity = mapper.selectById(id);
        if (entity == null) {
            throw new BusinessException("坏账准备记录不存在");
        }
        return entity;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public BadDebtProvisionEntity provisionByAging(String period, Map<String, BigDecimal> ratios) {
        // 1. 查询所有未清数据（4 数据源）
        List<UnsettledRow> allRows = new ArrayList<>();

        // 1a. INVOICE_OUT
        List<BusinessDocEntity> invoices = businessDocMapper.selectList(
                new LambdaQueryWrapper<BusinessDocEntity>()
                        .eq(BusinessDocEntity::getPeriod, period)
                        .eq(BusinessDocEntity::getDocType, "INVOICE_OUT")
                        .gt(BusinessDocEntity::getUnsettledAmount, BigDecimal.ZERO)
        );
        for (BusinessDocEntity doc : invoices) {
            allRows.add(new UnsettledRow("INVOICE_OUT", doc.getId(), doc.getCustomerId(),
                    doc.getDocNo(), doc.getDueDate(), doc.getUnsettledAmount()));
        }

        // 1b. OTHER_RECEIVABLE
        List<BusinessDocEntity> otherRecv = businessDocMapper.selectList(
                new LambdaQueryWrapper<BusinessDocEntity>()
                        .eq(BusinessDocEntity::getPeriod, period)
                        .eq(BusinessDocEntity::getDocType, "OTHER_RECEIVABLE")
                        .gt(BusinessDocEntity::getUnsettledAmount, BigDecimal.ZERO)
        );
        for (BusinessDocEntity doc : otherRecv) {
            allRows.add(new UnsettledRow("OTHER_RECEIVABLE", doc.getId(), doc.getCustomerId(),
                    doc.getDocNo(), doc.getDueDate(), doc.getUnsettledAmount()));
        }

        // 1c. NOTE_RECEIVABLE
        List<BusinessDocEntity> notes = businessDocMapper.selectList(
                new LambdaQueryWrapper<BusinessDocEntity>()
                        .eq(BusinessDocEntity::getPeriod, period)
                        .eq(BusinessDocEntity::getDocType, "NOTE_RECEIVABLE")
                        .gt(BusinessDocEntity::getUnsettledAmount, BigDecimal.ZERO)
        );
        for (BusinessDocEntity doc : notes) {
            allRows.add(new UnsettledRow("NOTE_RECEIVABLE", doc.getId(), doc.getCustomerId(),
                    doc.getDocNo(), doc.getDueDate(), doc.getUnsettledAmount()));
        }

        // 1d. PREPAYMENT（预付款视为应收类未清项纳入账龄分析）
        List<PrepaymentEntity> prepayments = prepaymentMapper.selectList(
                new LambdaQueryWrapper<PrepaymentEntity>()
                        .eq(PrepaymentEntity::getPeriod, period)
                        .gt(PrepaymentEntity::getUnsettledAmount, BigDecimal.ZERO)
        );
        for (PrepaymentEntity prep : prepayments) {
            allRows.add(new UnsettledRow("PREPAYMENT", prep.getId(), prep.getCustomerId(),
                    String.valueOf(prep.getId()), prep.getTxDate(), prep.getUnsettledAmount()));
        }

        // 2. 过滤掉有未解决差异的客户
        List<Long> disputeCustomerIds = customerStatementService.getCustomerIdsWithOpenDisputes();
        Set<Long> disputeSet = new HashSet<>(disputeCustomerIds != null ? disputeCustomerIds : Collections.emptyList());
        List<UnsettledRow> filtered = allRows.stream()
                .filter(r -> !disputeSet.contains(r.customerId))
                .collect(Collectors.toList());

        // 3. 计算 each row 的 aging bucket 和 provision amount
        LocalDate today = LocalDate.now();
        BigDecimal expectedBalance = BigDecimal.ZERO;
        List<BadDebtProvisionDetailEntity> details = new ArrayList<>();

        for (UnsettledRow r : filtered) {
            String bucket = computeAgingBucket(today, r.dueDate);
            BigDecimal ratio = ratios.getOrDefault(bucket, BigDecimal.ZERO);
            if (ratio.compareTo(BigDecimal.ZERO) > 0) {
                BigDecimal provision = r.unsettledAmount.multiply(ratio)
                        .setScale(2, RoundingMode.HALF_UP);

                BadDebtProvisionDetailEntity detail = new BadDebtProvisionDetailEntity();
                detail.setSourceType(r.sourceType);
                detail.setSourceId(r.sourceId);
                detail.setCustomerId(r.customerId);
                detail.setDocNo(r.docNo);
                detail.setDueDate(r.dueDate);
                detail.setUnsettledAmount(r.unsettledAmount);
                detail.setAgingBucket(bucket);
                detail.setRatio(ratio);
                detail.setProvisionAmount(provision);
                details.add(detail);

                expectedBalance = expectedBalance.add(provision);
            }
        }

        // 4. 查询科目 1231（坏账准备）当前余额
        BigDecimal existingBalance = querySubjectBalanceByCode("1231");

        // 5. 计算调整金额
        BigDecimal adjustment = expectedBalance.subtract(existingBalance);
        String adjustmentType;
        if (adjustment.compareTo(BigDecimal.ZERO) >= 0) {
            adjustmentType = "PROVISION";   // 补提
        } else {
            adjustmentType = "REVERSAL";    // 冲回
            adjustment = adjustment.abs();
        }

        // 6. 存储主表
        BadDebtProvisionEntity entity = new BadDebtProvisionEntity();
        entity.setPeriod(period);
        entity.setMethod("AGING_RATIO");
        entity.setProvisionDate(today);
        entity.setTotalAmount(adjustment);
        entity.setExpectedBalance(expectedBalance);
        entity.setExistingBalance(existingBalance);
        entity.setAdjustmentAmount(adjustment);
        entity.setAdjustmentType(adjustmentType);
        entity.setStatus(ArapStatus.DRAFT);
        entity.setRemark("账龄比例法计提，应有余额=" + expectedBalance + "，已有余额=" + existingBalance);
        mapper.insert(entity);

        // 7. 存储明细
        for (BadDebtProvisionDetailEntity detail : details) {
            detail.setProvisionId(entity.getId());
            detailMapper.insert(detail);
        }

        log.info("坏账准备账龄法计提完成: id={}, period={}, expectedBalance={}, existingBalance={}, adjustment={}, type={}",
                entity.getId(), period, expectedBalance, existingBalance, adjustment, adjustmentType);
        return entity;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public BadDebtProvisionEntity provisionByPercentage(String period, BigDecimal ratio) {
        // 查询所有未清数据（4 数据源同 provisionByAging）
        List<UnsettledRow> allRows = new ArrayList<>();

        for (String docType : List.of("INVOICE_OUT", "OTHER_RECEIVABLE", "NOTE_RECEIVABLE")) {
            List<BusinessDocEntity> docs = businessDocMapper.selectList(
                    new LambdaQueryWrapper<BusinessDocEntity>()
                            .eq(BusinessDocEntity::getPeriod, period)
                            .eq(BusinessDocEntity::getDocType, docType)
                            .gt(BusinessDocEntity::getUnsettledAmount, BigDecimal.ZERO)
            );
            for (BusinessDocEntity doc : docs) {
                allRows.add(new UnsettledRow(docType, doc.getId(), doc.getCustomerId(),
                        doc.getDocNo(), doc.getDueDate(), doc.getUnsettledAmount()));
            }
        }

        List<PrepaymentEntity> prepayments = prepaymentMapper.selectList(
                new LambdaQueryWrapper<PrepaymentEntity>()
                        .eq(PrepaymentEntity::getPeriod, period)
                        .gt(PrepaymentEntity::getUnsettledAmount, BigDecimal.ZERO)
        );
        for (PrepaymentEntity prep : prepayments) {
            allRows.add(new UnsettledRow("PREPAYMENT", prep.getId(), prep.getCustomerId(),
                    String.valueOf(prep.getId()), prep.getTxDate(), prep.getUnsettledAmount()));
        }

        // 过滤有差异客户
        List<Long> disputeCustomerIds = customerStatementService.getCustomerIdsWithOpenDisputes();
        Set<Long> disputeSet = new HashSet<>(disputeCustomerIds != null ? disputeCustomerIds : Collections.emptyList());
        List<UnsettledRow> filtered = allRows.stream()
                .filter(r -> !disputeSet.contains(r.customerId))
                .collect(Collectors.toList());

        BigDecimal totalUnsettled = filtered.stream()
                .map(r -> r.unsettledAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        BigDecimal expectedBalance = totalUnsettled.multiply(ratio)
                .setScale(2, RoundingMode.HALF_UP);

        // 查询科目 1231 当前余额
        BigDecimal existingBalance = querySubjectBalanceByCode("1231");

        BigDecimal adjustment = expectedBalance.subtract(existingBalance);
        String adjustmentType;
        if (adjustment.compareTo(BigDecimal.ZERO) >= 0) {
            adjustmentType = "PROVISION";
        } else {
            adjustmentType = "REVERSAL";
            adjustment = adjustment.abs();
        }

        BadDebtProvisionEntity entity = new BadDebtProvisionEntity();
        entity.setPeriod(period);
        entity.setMethod("PERCENTAGE");
        entity.setProvisionDate(LocalDate.now());
        entity.setTotalAmount(adjustment);
        entity.setExpectedBalance(expectedBalance);
        entity.setExistingBalance(existingBalance);
        entity.setAdjustmentAmount(adjustment);
        entity.setAdjustmentType(adjustmentType);
        entity.setStatus(ArapStatus.DRAFT);
        entity.setRemark("余额百分比法: 比例=" + ratio + "，应有余额=" + expectedBalance + "，已有余额=" + existingBalance);
        mapper.insert(entity);

        log.info("坏账准备百分比法计提完成: id={}, period={}, expectedBalance={}, existingBalance={}, adjustment={}, type={}",
                entity.getId(), period, expectedBalance, existingBalance, adjustment, adjustmentType);
        return entity;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public BadDebtProvisionEntity confirm(Long id, Long userId) {
        BadDebtProvisionEntity entity = getById(id);
        if (!ArapStatus.isDraft(entity.getStatus())) {
            throw new BusinessException("仅草稿状态可确认");
        }

        // 查找科目ID：1231（坏账准备）、6701（信用减值损失）
        Subject subject1231 = subjectMapper.selectOne(
                new LambdaQueryWrapper<Subject>().eq(Subject::getCode, "1231"));
        Subject subject6701 = subjectMapper.selectOne(
                new LambdaQueryWrapper<Subject>().eq(Subject::getCode, "6701"));
        if (subject1231 == null || subject6701 == null) {
            throw new BusinessException("科目 1231（坏账准备）或 6701（信用减值损失）不存在，请先配置科目");
        }

        // 生成凭证
        // 补提：借 6701（信用减值损失） 贷 1231（坏账准备）
        // 冲回：借 1231（坏账准备） 贷 6701（信用减值损失）
        VoucherCreateDTO dto = new VoucherCreateDTO();
        dto.setPeriod(entity.getPeriod());
        dto.setVoucherTypeId(1L); // 默认凭证类型ID（记账凭证）
        dto.setSummary("坏账准备计提 - " + entity.getPeriod());

        List<EntryDTO> entries = new ArrayList<>();
        EntryDTO entry1 = new EntryDTO();
        EntryDTO entry2 = new EntryDTO();

        BigDecimal amount = entity.getAdjustmentAmount();
        if ("PROVISION".equals(entity.getAdjustmentType())) {
            // 补提：借6701 贷1231
            entry1.setSubjectId(subject6701.getId());
            entry1.setDebit(amount);
            entry1.setCredit(BigDecimal.ZERO);
            entry1.setSummary("坏账准备补提");
            entry1.setSortOrder(1);

            entry2.setSubjectId(subject1231.getId());
            entry2.setDebit(BigDecimal.ZERO);
            entry2.setCredit(amount);
            entry2.setSummary("坏账准备补提");
            entry2.setSortOrder(2);
        } else {
            // 冲回：借1231 贷6701
            entry1.setSubjectId(subject1231.getId());
            entry1.setDebit(amount);
            entry1.setCredit(BigDecimal.ZERO);
            entry1.setSummary("坏账准备冲回");
            entry1.setSortOrder(1);

            entry2.setSubjectId(subject6701.getId());
            entry2.setDebit(BigDecimal.ZERO);
            entry2.setCredit(amount);
            entry2.setSummary("坏账准备冲回");
            entry2.setSortOrder(2);
        }

        entries.add(entry1);
        entries.add(entry2);
        dto.setEntries(entries);

        // 调用 VoucherService 创建凭证（状态 DRAFT，人工审核）
        var voucherVO = voucherService.create(dto, userId);

        // 回写凭证信息
        entity.setVoucherId(voucherVO.getId());
        entity.setVoucherNo(voucherVO.getVoucherNo());
        entity.setStatus(ArapStatus.VOUCHERED);
        mapper.updateById(entity);

        log.info("坏账准备确认并生成凭证: provisionId={}, voucherId={}, voucherNo={}, userId={}",
                id, voucherVO.getId(), voucherVO.getVoucherNo(), userId);
        return entity;
    }

    @Override
    public void delete(Long id) {
        BadDebtProvisionEntity entity = getById(id);
        if (!ArapStatus.isDraft(entity.getStatus())) {
            throw new BusinessException("仅草稿状态可删除");
        }
        mapper.deleteById(id);
        // 同时删除明细
        detailMapper.delete(new LambdaQueryWrapper<BadDebtProvisionDetailEntity>()
                .eq(BadDebtProvisionDetailEntity::getProvisionId, id));
    }

    // ==================== P43 新增 ====================

    @Override
    public BadDebtProvisionSchemeEntity getDefaultScheme() {
        BadDebtProvisionSchemeEntity scheme = schemeMapper.selectOne(
                new LambdaQueryWrapper<BadDebtProvisionSchemeEntity>()
                        .eq(BadDebtProvisionSchemeEntity::getIsDefault, true)
                        .eq(BadDebtProvisionSchemeEntity::getIsActive, true)
                        .last("LIMIT 1")
        );
        if (scheme == null) {
            throw new BusinessException("未找到默认坏账计提方案，请先配置");
        }
        return scheme;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void updateSchemeRatios(Map<String, BigDecimal> ratios) {
        BadDebtProvisionSchemeEntity scheme = getDefaultScheme();
        // 删除旧明细
        schemeItemMapper.delete(new LambdaQueryWrapper<BadDebtProvisionSchemeItemEntity>()
                .eq(BadDebtProvisionSchemeItemEntity::getSchemeId, scheme.getId()));

        // 插入新明细
        int sort = 1;
        for (Map.Entry<String, BigDecimal> entry : ratios.entrySet()) {
            BadDebtProvisionSchemeItemEntity item = new BadDebtProvisionSchemeItemEntity();
            item.setSchemeId(scheme.getId());
            item.setBucketName(entry.getKey());
            item.setRatio(entry.getValue());
            item.setSortOrder(sort++);
            schemeItemMapper.insert(item);
        }
        log.info("更新默认计提方案比例: schemeId={}, ratios={}", scheme.getId(), ratios);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Void writeOff(Long sourceId, String sourceType, BigDecimal amount, String reason, Long userId) {
        // 校验参数
        if (amount == null || amount.compareTo(BigDecimal.ZERO) <= 0) {
            throw new BusinessException("核销金额必须大于0");
        }

        // 根据 sourceType 查询并更新源单据的未清金额
        switch (sourceType) {
            case "INVOICE_OUT":
            case "OTHER_RECEIVABLE":
            case "NOTE_RECEIVABLE": {
                BusinessDocEntity doc = businessDocMapper.selectById(sourceId);
                if (doc == null) {
                    throw new BusinessException("业务单据不存在: id=" + sourceId);
                }
                if (doc.getUnsettledAmount().compareTo(amount) < 0) {
                    throw new BusinessException("核销金额不能大于未清金额");
                }
                doc.setUnsettledAmount(doc.getUnsettledAmount().subtract(amount));
                doc.setSettledAmount(doc.getSettledAmount() == null ? amount : doc.getSettledAmount().add(amount));
                if (doc.getUnsettledAmount().compareTo(BigDecimal.ZERO) == 0) {
                    doc.setStatus(ArapStatus.SETTLED);
                }
                doc.setUpdatedBy(userId);
                businessDocMapper.updateById(doc);
                log.info("坏账核销: sourceType={}, sourceId={}, amount={}, reason={}, userId={}",
                        sourceType, sourceId, amount, reason, userId);
                break;
            }
            case "PREPAYMENT": {
                PrepaymentEntity prep = prepaymentMapper.selectById(sourceId);
                if (prep == null) {
                    throw new BusinessException("预付款单据不存在: id=" + sourceId);
                }
                if (prep.getUnsettledAmount().compareTo(amount) < 0) {
                    throw new BusinessException("核销金额不能大于未清金额");
                }
                prep.setUnsettledAmount(prep.getUnsettledAmount().subtract(amount));
                prep.setSettledAmount(prep.getSettledAmount() == null ? amount : prep.getSettledAmount().add(amount));
                if (prep.getUnsettledAmount().compareTo(BigDecimal.ZERO) == 0) {
                    prep.setStatus(ArapStatus.SETTLED);
                }
                prepaymentMapper.updateById(prep);
                log.info("坏账核销: sourceType=PREPAYMENT, sourceId={}, amount={}, reason={}, userId={}",
                        sourceId, amount, reason, userId);
                break;
            }
            default:
                throw new BusinessException("不支持的数据来源类型: " + sourceType);
        }
        return null;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Void recovery(Long sourceId, BigDecimal amount, Long userId) {
        if (amount == null || amount.compareTo(BigDecimal.ZERO) <= 0) {
            throw new BusinessException("收回金额必须大于0");
        }

        // 先尝试从 BusinessDoc 恢复
        BusinessDocEntity doc = businessDocMapper.selectById(sourceId);
        if (doc != null) {
            doc.setUnsettledAmount(doc.getUnsettledAmount() == null ? amount : doc.getUnsettledAmount().add(amount));
            doc.setSettledAmount(doc.getSettledAmount().subtract(amount));
            if (doc.getSettledAmount().compareTo(BigDecimal.ZERO) == 0) {
                doc.setSettledAmount(BigDecimal.ZERO);
            }
            if (doc.getUnsettledAmount().compareTo(BigDecimal.ZERO) > 0) {
                doc.setStatus(ArapStatus.DRAFT);
            }
            doc.setUpdatedBy(userId);
            businessDocMapper.updateById(doc);
            log.info("坏账收回: sourceId={}, amount={}, userId={}", sourceId, amount, userId);
            return null;
        }

        // 再尝试从 Prepayment 恢复
        PrepaymentEntity prep = prepaymentMapper.selectById(sourceId);
        if (prep != null) {
            prep.setUnsettledAmount(prep.getUnsettledAmount() == null ? amount : prep.getUnsettledAmount().add(amount));
            prep.setSettledAmount(prep.getSettledAmount().subtract(amount));
            if (prep.getSettledAmount().compareTo(BigDecimal.ZERO) == 0) {
                prep.setSettledAmount(BigDecimal.ZERO);
            }
            if (prep.getUnsettledAmount().compareTo(BigDecimal.ZERO) > 0) {
                prep.setStatus(ArapStatus.DRAFT);
            }
            prepaymentMapper.updateById(prep);
            log.info("坏账收回: sourceId={}, amount={}, userId={}", sourceId, amount, userId);
            return null;
        }

        throw new BusinessException("未找到对应的单据: sourceId=" + sourceId);
    }

    // ==================== 内部方法 ====================

    private String computeAgingBucket(LocalDate today, LocalDate dueDate) {
        if (dueDate == null) return "current";
        long days = today.toEpochDay() - dueDate.toEpochDay();
        if (days <= 0) return "current";
        if (days <= 30) return "days_0_30";
        if (days <= 60) return "days_31_60";
        if (days <= 90) return "days_61_90";
        if (days <= 180) return "days_91_180";
        if (days <= 365) return "days_181_365";
        return "over_365";
    }

    /**
     * 查询指定科目编码的当前余额（通过汇总 t_voucher_entry 中该科目的借贷差）
     */
    private BigDecimal querySubjectBalanceByCode(String subjectCode) {
        Subject subject = subjectMapper.selectOne(
                new LambdaQueryWrapper<Subject>().eq(Subject::getCode, subjectCode));
        if (subject == null) {
            return BigDecimal.ZERO;
        }
        // 汇总 t_voucher_entry 中该科目的所有分录
        List<com.huicai.base.voucher.entity.VoucherEntryEntity> entries = voucherEntryMapper.selectList(
                new LambdaQueryWrapper<com.huicai.base.voucher.entity.VoucherEntryEntity>()
                        .eq(com.huicai.base.voucher.entity.VoucherEntryEntity::getSubjectId, subject.getId())
        );
        BigDecimal totalDebit = entries.stream()
                .map(e -> e.getDebit() != null ? e.getDebit() : BigDecimal.ZERO)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal totalCredit = entries.stream()
                .map(e -> e.getCredit() != null ? e.getCredit() : BigDecimal.ZERO)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        // 坏账准备（1231）是资产类备抵科目，贷方余额表示计提的坏账准备
        // 所以余额 = 贷方 - 借方
        return totalCredit.subtract(totalDebit);
    }

    /**
     * 未清数据结构体（内部使用）
     */
    private record UnsettledRow(
            String sourceType,
            Long sourceId,
            Long customerId,
            String docNo,
            LocalDate dueDate,
            BigDecimal unsettledAmount
    ) {}
}