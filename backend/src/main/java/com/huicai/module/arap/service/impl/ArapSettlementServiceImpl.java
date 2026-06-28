package com.huicai.module.arap.service.impl;

import cn.hutool.core.util.IdUtil;
import cn.hutool.core.util.StrUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.huicai.common.exception.BusinessException;
import com.huicai.module.arap.constant.ArapStatus;
import com.huicai.module.arap.entity.ArapSettlementEntity;
import com.huicai.module.arap.entity.ArapSettlementEntryEntity;
import com.huicai.module.arap.entity.PayableEntity;
import com.huicai.module.arap.entity.ReceivableEntity;
import com.huicai.module.arap.mapper.ArapSettlementEntryMapper;
import com.huicai.module.arap.mapper.ArapSettlementMapper;
import com.huicai.module.arap.mapper.PayableMapper;
import com.huicai.module.arap.mapper.ReceivableMapper;
import com.huicai.module.arap.service.ArapSettlementService;
import com.huicai.module.finance.entity.VoucherEntity;
import com.huicai.module.finance.entity.VoucherEntryEntity;
import com.huicai.module.finance.mapper.VoucherEntryMapper;
import com.huicai.module.finance.mapper.VoucherMapper;
import com.huicai.module.finance.service.VoucherNoService;
import com.huicai.module.finance.service.VoucherTemplateService;
import com.huicai.module.finance.entity.VoucherTemplateEntity;
import com.huicai.module.finance.entity.VoucherTemplateLineEntity;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;

@Service
@RequiredArgsConstructor
public class ArapSettlementServiceImpl implements ArapSettlementService {

    private static final Logger log = LoggerFactory.getLogger(ArapSettlementServiceImpl.class);
    private static final long DEFAULT_VOUCHER_TYPE_ID = 1L;
    private static final long DEFAULT_USER_ID = 0L;

    private final ArapSettlementMapper mapper;
    private final ArapSettlementEntryMapper entryMapper;
    private final ReceivableMapper receivableMapper;
    private final PayableMapper payableMapper;
    private final VoucherTemplateService voucherTemplateService;
    private final VoucherMapper voucherMapper;
    private final VoucherEntryMapper voucherEntryMapper;
    private final VoucherNoService voucherNoService;

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
    public ArapSettlementEntity getById(Long id) {
        ArapSettlementEntity entity = mapper.selectById(id);
        if (entity == null) {
            throw new BusinessException("核销单不存在");
        }
        return entity;
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
    @Transactional(rollbackFor = Exception.class)
    public ArapSettlementEntity confirm(Long id) {
        ArapSettlementEntity entity = getById(id);
        if (!ArapStatus.isDraft(entity.getStatus())) {
            throw new BusinessException("仅草稿状态可确认");
        }
        // 更新明细对应应收/应付的已核销金额
        List<ArapSettlementEntryEntity> entries = entryMapper.selectList(
                new LambdaQueryWrapper<ArapSettlementEntryEntity>()
                        .eq(ArapSettlementEntryEntity::getSettlementId, id)
        );
        for (ArapSettlementEntryEntity entry : entries) {
            if (entry.getReceivableId() != null) {
                ReceivableEntity r = receivableMapper.selectById(entry.getReceivableId());
                if (r != null) {
                    BigDecimal newSettled = r.getSettledAmount().add(entry.getSettledAmount());
                    r.setSettledAmount(newSettled);
                    r.setUnsettledAmount(r.getAmount().subtract(newSettled));
                    if (r.getUnsettledAmount().compareTo(BigDecimal.ZERO) == 0
                            && ArapStatus.isConfirmed(r.getStatus())) {
                        r.setStatus(ArapStatus.SETTLED);
                    }
                    receivableMapper.updateById(r);
                }
            } else if (entry.getPayableId() != null) {
                PayableEntity p = payableMapper.selectById(entry.getPayableId());
                if (p != null) {
                    BigDecimal newSettled = p.getSettledAmount().add(entry.getSettledAmount());
                    p.setSettledAmount(newSettled);
                    p.setUnsettledAmount(p.getAmount().subtract(newSettled));
                    if (p.getUnsettledAmount().compareTo(BigDecimal.ZERO) == 0
                            && ArapStatus.isConfirmed(p.getStatus())) {
                        p.setStatus(ArapStatus.SETTLED);
                    }
                    payableMapper.updateById(p);
                }
            }
        }
        entity.setStatus(ArapStatus.CONFIRMED);
        mapper.updateById(entity);
        return entity;
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
    public void generateVoucher(Long id) {
        ArapSettlementEntity entity = getById(id);
        if (!ArapStatus.isConfirmed(entity.getStatus())) {
            throw new BusinessException("仅已确认(CONFIRMED)的核销单可生成凭证");
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
        String voucherNo = voucherNoService.generateNextNo(period, DEFAULT_VOUCHER_TYPE_ID);

        VoucherEntity voucher = new VoucherEntity();
        voucher.setVoucherNo(voucherNo);
        voucher.setPeriod(period);
        voucher.setVoucherTypeId(DEFAULT_VOUCHER_TYPE_ID);
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

        // 新增：回写凭证编号到所有核销明细对应的应收/应付单
        for (ArapSettlementEntryEntity entry : entries) {
            if (entry.getReceivableId() != null) {
                ReceivableEntity r = receivableMapper.selectById(entry.getReceivableId());
                if (r != null && r.getVoucherNo() == null) {
                    r.setVoucherNo(voucherNo);
                    receivableMapper.updateById(r);
                }
            } else if (entry.getPayableId() != null) {
                PayableEntity p = payableMapper.selectById(entry.getPayableId());
                if (p != null && p.getVoucherNo() == null) {
                    p.setVoucherNo(voucherNo);
                    payableMapper.updateById(p);
                }
            }
        }

        log.info("核销单生成凭证: settlementId={}, voucherId={}, voucherNo={}, amount={}",
                id, voucher.getId(), voucherNo, entity.getTotalAmount());
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void reverse(Long id) {
        ArapSettlementEntity entity = getById(id);
        if (!ArapStatus.isConfirmed(entity.getStatus())) {
            throw new BusinessException("仅已确认(CONFIRMED)的核销单可反核销");
        }
        List<ArapSettlementEntryEntity> entries = entryMapper.selectList(
                new LambdaQueryWrapper<ArapSettlementEntryEntity>()
                        .eq(ArapSettlementEntryEntity::getSettlementId, id)
        );
        for (ArapSettlementEntryEntity entry : entries) {
            restoreUnsettledAmount(entry);
        }
        entity.setStatus(ArapStatus.REVERSED);
        mapper.updateById(entity);
    }

    private void restoreUnsettledAmount(ArapSettlementEntryEntity entry) {
        if (entry.getReceivableId() != null) {
            ReceivableEntity r = receivableMapper.selectById(entry.getReceivableId());
            if (r != null) {
                BigDecimal newSettled = r.getSettledAmount().subtract(entry.getSettledAmount());
                r.setSettledAmount(newSettled);
                r.setUnsettledAmount(r.getAmount().subtract(newSettled));
                if (ArapStatus.isSettled(r.getStatus())) {
                    r.setStatus(ArapStatus.CONFIRMED);
                }
                receivableMapper.updateById(r);
            }
        } else if (entry.getPayableId() != null) {
            PayableEntity p = payableMapper.selectById(entry.getPayableId());
            if (p != null) {
                BigDecimal newSettled = p.getSettledAmount().subtract(entry.getSettledAmount());
                p.setSettledAmount(newSettled);
                p.setUnsettledAmount(p.getAmount().subtract(newSettled));
                if (ArapStatus.isSettled(p.getStatus())) {
                    p.setStatus(ArapStatus.CONFIRMED);
                }
                payableMapper.updateById(p);
            }
        }
    }
}
