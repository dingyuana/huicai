package com.huicai.base.system.service.impl;

import org.springframework.transaction.annotation.Transactional;
import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.huicai.base.system.entity.PeriodEntity;
import com.huicai.base.system.mapper.PeriodMapper;
import com.huicai.base.system.service.PeriodService;
import com.huicai.common.context.EnterpriseContextHolder;
import com.huicai.common.exception.BusinessException;
import org.springframework.stereotype.Service;

import java.util.List;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Service
/**
 * 类级事务：P102/M5b —— 走租户表的读写路径必须有事务，否则切面不触发、
 * app.enterprise_id 设不进去，非超级用户下 RLS 会把本企业数据也过滤掉（读 0 行）。
 */
@Transactional
public class PeriodServiceImpl extends ServiceImpl<PeriodMapper, PeriodEntity> implements PeriodService {

    /**
     * SPEC-P122：显式覆盖 MyBatis-Plus 的 default 方法以确保事务开启。
     *
     * <p><b>为什么类级 {@code @Transactional} 不够</b>：Spring AOP 与事务拦截器求值时
     * 先看<b>被调用方法的声明类型</b>。{@code list(Wrapper)} 是 {@code IService} 的
     * <b>default 接口方法</b>，{@code ServiceImpl} 也不覆盖它 —— 尽管本类有类级
     * {@code @Transactional}，事务<b>不会被开启</b>。
     *
     * <p><b>为什么必须开事务</b>：企业隔离 GUC 用的是 {@code SET LOCAL}，
     * 而 {@code SET LOCAL} 仅在事务块内有效。无事务 ⇒ GUC 设不上 ⇒
     * 非超级用户下 RLS 谓词取到 NULL ⇒ <b>静默返回 0 行且无任何报错</b>
     * （2026-10-12 实测 {@code GET /api/v1/periods/all} 返回空数组）。
     *
     * <p>覆盖后该方法变为本类<b>自身声明</b>，方法级 {@code @Transactional} 被采信，
     * {@code TenantRlsInitializer} 切面亦随之触发。
     */
    @Override
    @Transactional
    public List<PeriodEntity> list(Wrapper<PeriodEntity> queryWrapper) {
        return super.list(queryWrapper);
    }

    /** 同上：无参重载亦为 {@code IService} 的 default 方法。 */
    @Override
    @Transactional
    public List<PeriodEntity> list() {
        return super.list();
    }

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