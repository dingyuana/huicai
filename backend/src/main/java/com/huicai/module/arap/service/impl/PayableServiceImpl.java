package com.huicai.module.arap.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.huicai.common.exception.BusinessException;
import com.huicai.module.arap.entity.PayableEntity;
import com.huicai.module.arap.mapper.PayableMapper;
import com.huicai.module.arap.service.PayableService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class PayableServiceImpl implements PayableService {

    private final PayableMapper mapper;

    private static final List<String> AGING_BUCKETS = List.of(
            "current", "days_0_30", "days_31_60", "days_61_90",
            "days_91_180", "days_181_365", "over_365"
    );

    @Override
    public IPage<PayableEntity> pageQuery(Long vendorId, String period, Integer current, Integer size) {
        Page<PayableEntity> page = new Page<>(
                current == null ? 1 : current,
                size == null ? 20 : size
        );
        LambdaQueryWrapper<PayableEntity> wrapper = new LambdaQueryWrapper<>();
        if (vendorId != null) wrapper.eq(PayableEntity::getVendorId, vendorId);
        if (period != null && !period.isBlank()) wrapper.eq(PayableEntity::getPeriod, period);
        wrapper.gt(PayableEntity::getUnsettledAmount, BigDecimal.ZERO);
        wrapper.orderByDesc(PayableEntity::getTxDate);
        return mapper.selectPage(page, wrapper);
    }

    @Override
    public PayableEntity getById(Long id) {
        PayableEntity entity = mapper.selectById(id);
        if (entity == null) {
            throw new BusinessException("应付明细不存在");
        }
        return entity;
    }

    @Override
    public PayableEntity create(PayableEntity entity) {
        if (entity.getSettledAmount() == null) entity.setSettledAmount(BigDecimal.ZERO);
        entity.setUnsettledAmount(entity.getAmount().subtract(entity.getSettledAmount()));
        mapper.insert(entity);
        return entity;
    }

    @Override
    public Map<String, Object> agingAnalysis(Long vendorId) {
        List<Map<String, Object>> rows = mapper.agingByVendor(vendorId);
        Map<String, Object> result = new LinkedHashMap<>();
        Map<String, BigDecimal> amounts = new LinkedHashMap<>();
        Map<String, Integer> counts = new LinkedHashMap<>();
        for (String bucket : AGING_BUCKETS) {
            amounts.put(bucket, BigDecimal.ZERO);
            counts.put(bucket, 0);
        }
        for (Map<String, Object> row : rows) {
            String bucket = (String) row.get("aging_bucket");
            Object amountObj = row.get("amount");
            Object countObj = row.get("count");
            amounts.put(bucket, amountObj == null ? BigDecimal.ZERO :
                    new BigDecimal(amountObj.toString()));
            counts.put(bucket, countObj == null ? 0 : ((Number) countObj).intValue());
        }
        result.put("buckets", amounts.keySet());
        result.put("amounts", amounts.values());
        result.put("counts", counts.values());
        BigDecimal total = amounts.values().stream().reduce(BigDecimal.ZERO, BigDecimal::add);
        result.put("total", total);
        return result;
    }
}
