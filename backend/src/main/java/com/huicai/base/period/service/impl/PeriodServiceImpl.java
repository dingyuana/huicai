package com.huicai.base.period.service.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.huicai.base.period.entity.PeriodEntity;
import com.huicai.base.period.mapper.PeriodMapper;
import com.huicai.base.period.service.PeriodService;
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
}