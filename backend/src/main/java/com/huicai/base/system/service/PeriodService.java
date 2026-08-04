package com.huicai.base.system.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.huicai.base.system.entity.PeriodEntity;

/**
 * 会计期间 Service
 */
public interface PeriodService extends IService<PeriodEntity> {

    /**
     * 启用期间
     */
    void openPeriod(Long id);

    /**
     * 关闭期间
     */
    void closePeriod(Long id);

    /**
     * 锁定期间
     */
    void lockPeriod(Long id);

    /**
     * 解锁期间
     */
    void unlockPeriod(Long id);

    /**
     * 按期间编码 (YYYYMM) 查询期间。
     * 企业数据权限由 EnterpriseDataPermissionInterceptor 自动注入 enterprise_id 条件。
     */
    PeriodEntity getByPeriodCode(String periodCode);

    /**
     * 原子更新指定期间的 opening_status。
     * 用于期初建账/锁定/解锁/清空。
     */
    void setOpeningStatus(String periodCode, String openingStatus);
}
