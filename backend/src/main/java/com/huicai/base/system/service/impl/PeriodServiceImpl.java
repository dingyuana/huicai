package com.huicai.base.system.service.impl;

import org.springframework.transaction.annotation.Transactional;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.huicai.base.system.entity.PeriodEntity;
import com.huicai.base.system.mapper.PeriodMapper;
import com.huicai.base.system.service.PeriodService;
import com.huicai.common.context.EnterpriseContextHolder;
import com.huicai.common.exception.BusinessException;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.LocalDateTime;

@Service
/**
 * 类级事务：P102/M5b —— 走租户表的读写路径必须有事务，否则切面不触发、
 * app.enterprise_id 设不进去，非超级用户下 RLS 会把本企业数据也过滤掉（读 0 行）。
 */
@Transactional
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

        // 物理清理软删残留：释放唯一索引占位（逻辑删除记录会挡住同编码重建）
        Long enterpriseId = EnterpriseContextHolder.get();
        baseMapper.purgeSoftDeleted(entity.getPeriodCode(), enterpriseId);

        // 查重：同一企业下期间编码不能重复（selectCount 自动过滤已删除记录）
        LambdaQueryWrapper<PeriodEntity> checkWrapper = new LambdaQueryWrapper<>();
        checkWrapper.eq(PeriodEntity::getPeriodCode, entity.getPeriodCode());
        if (enterpriseId != null) {
            checkWrapper.eq(PeriodEntity::getEnterpriseId, enterpriseId);
        }
        if (baseMapper.selectCount(checkWrapper) > 0) {
            throw BusinessException.conflict("期间编码已存在: " + entity.getPeriodCode());
        }

        // P0-fix: 原为两处条件兜底，而 PeriodController#create 直收 @RequestBody PeriodEntity
        // （违反铁律 #13）⇒ 客户端可指定 status 与 deleted。
        // ①chk_period_status 用**小写** open/closed/locked（注意与其它表的 OPEN/CLOSED 大写不同），
        //   POST status=locked 会创建出**已锁定的会计期间**，阻断该期间全部记账；
        // ②deleted 是服务端托管的逻辑删除标记，接受客户端传值等于交出「创建即隐藏」的越权面。
        // 均改为无条件强制。
        entity.setStatus("open");
        entity.setDeleted(0);
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

    @Override
    public void markOpeningEntered(String periodCode, LocalDateTime openedAt, Long openedBy, String openedByName) {
        if (periodCode == null || periodCode.isBlank()) return;
        PeriodEntity entity = getByPeriodCode(periodCode);
        if (entity == null) return;
        entity.setOpeningStatus("entered");
        entity.setOpenedAt(openedAt);
        entity.setOpenedBy(openedBy);
        entity.setOpenedByName(openedByName);
        updateById(entity);
    }
}