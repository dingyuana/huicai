package com.huicai.module.arap.service;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.huicai.module.arap.entity.BadDebtProvisionEntity;

import java.math.BigDecimal;
import java.util.Map;

public interface BadDebtService {
    IPage<BadDebtProvisionEntity> pageQuery(String status, Integer current, Integer size);
    BadDebtProvisionEntity getById(Long id);
    BadDebtProvisionEntity provisionByAging(String period, Map<String, BigDecimal> ratios);
    BadDebtProvisionEntity provisionByPercentage(String period, BigDecimal ratio);
    BadDebtProvisionEntity confirm(Long id);
    void delete(Long id);
}
