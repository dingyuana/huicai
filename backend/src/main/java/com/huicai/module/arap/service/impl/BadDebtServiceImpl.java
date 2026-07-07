package com.huicai.module.arap.service.impl;

import cn.hutool.core.util.IdUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.huicai.common.exception.BusinessException;
import com.huicai.module.arap.constant.ArapStatus;
import com.huicai.module.arap.entity.BadDebtProvisionEntity;
import com.huicai.module.arap.mapper.BadDebtProvisionMapper;
import com.huicai.module.finance.entity.BusinessDocEntity;
import com.huicai.module.finance.mapper.BusinessDocMapper;
import com.huicai.module.arap.service.BadDebtService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class BadDebtServiceImpl implements BadDebtService {

    private final BadDebtProvisionMapper mapper;
    private final BusinessDocMapper businessDocMapper;

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
        // P34: 加载所有销售发票类业务单据未核销明细
        List<BusinessDocEntity> docs = businessDocMapper.selectList(
                new LambdaQueryWrapper<BusinessDocEntity>()
                        .eq(BusinessDocEntity::getPeriod, period)
                        .eq(BusinessDocEntity::getDocType, "INVOICE_OUT")
                        .gt(BusinessDocEntity::getUnsettledAmount, BigDecimal.ZERO)
        );
        BigDecimal total = BigDecimal.ZERO;
        LocalDate today = LocalDate.now();
        for (BusinessDocEntity r : docs) {
            String bucket = computeAgingBucket(today, r.getDueDate());
            BigDecimal ratio = ratios.getOrDefault(bucket, BigDecimal.ZERO);
            if (ratio.compareTo(BigDecimal.ZERO) > 0) {
                BigDecimal provision = r.getUnsettledAmount().multiply(ratio)
                        .setScale(2, RoundingMode.HALF_UP);
                total = total.add(provision);
            }
        }
        BadDebtProvisionEntity entity = new BadDebtProvisionEntity();
        entity.setPeriod(period);
        entity.setMethod("AGING_RATIO");
        entity.setProvisionDate(today);
        entity.setTotalAmount(total);
        entity.setStatus(ArapStatus.DRAFT);
        entity.setRemark("账龄比例法: " + ratios);
        mapper.insert(entity);
        return entity;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public BadDebtProvisionEntity provisionByPercentage(String period, BigDecimal ratio) {
        // P34: 加载所有销售发票类业务单据未核销明细
        List<BusinessDocEntity> docs = businessDocMapper.selectList(
                new LambdaQueryWrapper<BusinessDocEntity>()
                        .eq(BusinessDocEntity::getPeriod, period)
                        .eq(BusinessDocEntity::getDocType, "INVOICE_OUT")
                        .gt(BusinessDocEntity::getUnsettledAmount, BigDecimal.ZERO)
        );
        BigDecimal totalUnsettled = docs.stream()
                .map(BusinessDocEntity::getUnsettledAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal provision = totalUnsettled.multiply(ratio)
                .setScale(2, RoundingMode.HALF_UP);
        BadDebtProvisionEntity entity = new BadDebtProvisionEntity();
        entity.setPeriod(period);
        entity.setMethod("PERCENTAGE");
        entity.setProvisionDate(LocalDate.now());
        entity.setTotalAmount(provision);
        entity.setStatus(ArapStatus.DRAFT);
        entity.setRemark("余额百分比法: " + ratio);
        mapper.insert(entity);
        return entity;
    }

    @Override
    public BadDebtProvisionEntity confirm(Long id) {
        BadDebtProvisionEntity entity = getById(id);
        if (!ArapStatus.isDraft(entity.getStatus())) {
            throw new BusinessException("仅草稿状态可确认");
        }
        entity.setStatus(ArapStatus.CONFIRMED);
        mapper.updateById(entity);
        return entity;
    }

    @Override
    public void delete(Long id) {
        BadDebtProvisionEntity entity = getById(id);
        if (!ArapStatus.isDraft(entity.getStatus())) {
            throw new BusinessException("仅草稿状态可删除");
        }
        mapper.deleteById(id);
    }

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
}
