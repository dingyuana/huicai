package com.huicai.sme.arap.service;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.huicai.sme.arap.entity.BadDebtProvisionEntity;
import com.huicai.sme.arap.entity.BadDebtProvisionSchemeEntity;

import java.math.BigDecimal;
import java.util.Map;

public interface BadDebtService {
    IPage<BadDebtProvisionEntity> pageQuery(String status, Integer current, Integer size);
    BadDebtProvisionEntity getById(Long id);
    BadDebtProvisionEntity provisionByAging(String period, Map<String, BigDecimal> ratios);
    BadDebtProvisionEntity provisionByPercentage(String period, BigDecimal ratio);
    BadDebtProvisionEntity confirm(Long id, Long userId);
    void delete(Long id);

    // ===== P43 新增 =====
    /** 获取默认计提方案 */
    BadDebtProvisionSchemeEntity getDefaultScheme();
    /** 更新默认方案的计提比例 */
    void updateSchemeRatios(Map<String, BigDecimal> ratios);
    /** 坏账核销 */
    Void writeOff(Long sourceId, String sourceType, BigDecimal amount, String reason, Long userId);
    /** 已核销收回 */
    Void recovery(Long sourceId, BigDecimal amount, Long userId);
}