package com.huicai.module.system.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.huicai.module.system.entity.PeriodEntity;

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
}
