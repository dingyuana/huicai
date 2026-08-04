package com.huicai.base.system.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.huicai.base.system.entity.PeriodEntity;
import com.huicai.base.system.mapper.PeriodMapper;
import com.huicai.base.system.service.PeriodService;
import com.huicai.common.context.EnterpriseContextHolder;
import com.huicai.common.exception.BusinessException;
import org.springframework.stereotype.Service;

import java.time.LocalDate;

@Service
public class PeriodServiceImpl extends ServiceImpl<PeriodMapper, PeriodEntity> implements PeriodService {

    @Override
    public boolean save(PeriodEntity entity) {
        // 自动生成 period_code: yyyyMM
        if (entity.getPeriodCode() == null || entity.getPeriodCode().isBlank()) {
            if (entity.getYear() != null && entity.getMonth() != null) {
                entity.setPeriodCode(String.format("%04d%02d", entity.getYear(), entity.getMonth()));
            } else if (entity.getStartDate() != null) {
                entity.setPeriodCode(String.format("%04d%02d", entity.getStartDate().getYear(), entity.getStartDate().getMonthValue()));
            }
        }
        // 自动计算 start_date / end_date
        if (entity.getStartDate() == null && entity.getPeriodCode() != null && entity.getPeriodCode().length() == 6) {
            int year = Integer.parseInt(entity.getPeriodCode().substring(0, 4));
            int month = Integer.parseInt(entity.getPeriodCode().substring(4, 6));
            entity.setYear(year);
            entity.setMonth(month);
            entity.setStartDate(LocalDate.of(year, month, 1));
            entity.setEndDate(LocalDate.of(year, month, 1).plusMonths(1).minusDays(1));
        }

        // 查重：同一企业下期间编码不能重复
        LambdaQueryWrapper<PeriodEntity> checkWrapper = new LambdaQueryWrapper<>();
        checkWrapper.eq(PeriodEntity::getPeriodCode, entity.getPeriodCode());
        Long enterpriseId = EnterpriseContextHolder.get();
        if (enterpriseId != null) {
            checkWrapper.eq(PeriodEntity::getEnterpriseId, enterpriseId);
        }
        if (baseMapper.selectCount(checkWrapper) > 0) {
            throw BusinessException.conflict("期间编码已存在: " + entity.getPeriodCode());
        }

        if (entity.getStatus() == null) entity.setStatus("open");
        if (entity.getDeleted() == null) entity.setDeleted(0);
        return super.save(entity);
    }

    @Override
    public void openPeriod(Long id) {
        PeriodEntity entity = getById(id);
        if (entity != null) {
            entity.setStatus("open");
            updateById(entity);
        }
    }

    @Override
    public void closePeriod(Long id) {
        PeriodEntity entity = getById(id);
        if (entity != null) {
            entity.setStatus("closed");
            updateById(entity);
        }
    }

    @Override
    public void lockPeriod(Long id) {
        PeriodEntity entity = getById(id);
        if (entity != null) {
            entity.setStatus("locked");
            updateById(entity);
        }
    }

    @Override
    public void unlockPeriod(Long id) {
        PeriodEntity entity = getById(id);
        if (entity != null) {
            entity.setStatus("open");
            updateById(entity);
        }
    }

    @Override
    public PeriodEntity getByPeriodCode(String periodCode) {
        if (periodCode == null || periodCode.isBlank()) return null;
        LambdaQueryWrapper<PeriodEntity> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(PeriodEntity::getPeriodCode, periodCode);
        return getOne(wrapper);
    }

    @Override
    public void setOpeningStatus(String periodCode, String openingStatus) {
        if (periodCode == null || periodCode.isBlank()) return;
        PeriodEntity entity = getByPeriodCode(periodCode);
        if (entity == null) return;
        entity.setOpeningStatus(openingStatus);
        updateById(entity);
    }
}