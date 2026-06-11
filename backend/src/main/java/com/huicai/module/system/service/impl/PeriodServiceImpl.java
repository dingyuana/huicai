package com.huicai.module.system.service.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.huicai.common.exception.BusinessException;
import com.huicai.module.system.entity.PeriodEntity;
import com.huicai.module.system.mapper.PeriodMapper;
import com.huicai.module.system.service.PeriodService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 会计期间 Service 实现
 */
@Service
public class PeriodServiceImpl extends ServiceImpl<PeriodMapper, PeriodEntity> implements PeriodService {
    @Override
    @Transactional
    public void openPeriod(Long id) {
        PeriodEntity period = getById(id);
        if (period == null) {
            throw BusinessException.notFound("期间不存在");
        }
        period.setStatus("open");
        updateById(period);
    }

    @Override
    @Transactional
    public void closePeriod(Long id) {
        PeriodEntity period = getById(id);
        if (period == null) {
            throw BusinessException.notFound("期间不存在");
        }
        period.setStatus("closed");
        updateById(period);
    }

    @Override
    @Transactional
    public void lockPeriod(Long id) {
        PeriodEntity period = getById(id);
        if (period == null) {
            throw BusinessException.notFound("期间不存在");
        }
        period.setStatus("locked");
        updateById(period);
    }

    @Override
    @Transactional
    public void unlockPeriod(Long id) {
        PeriodEntity period = getById(id);
        if (period == null) {
            throw BusinessException.notFound("期间不存在");
        }
        period.setStatus("open");
        updateById(period);
    }
}
