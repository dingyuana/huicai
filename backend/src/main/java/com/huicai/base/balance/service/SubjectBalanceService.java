package com.huicai.base.balance.service;

import com.huicai.base.balance.entity.SubjectBalanceEntity;
import com.huicai.base.balance.dto.SubjectBalanceVO;
import com.huicai.base.system.entity.PeriodEntity;
import com.huicai.base.voucher.entity.VoucherEntity;
import com.huicai.base.voucher.entity.VoucherEntryEntity;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

/**
 * 科目余额更新服务
 */
public interface SubjectBalanceService {

    void updateBalanceOnPost(VoucherEntity voucher, List<VoucherEntryEntity> entries);

    /**
     * 期初建账：将传入科目余额写入 t_subject_balance，并标记 t_period.opening_status = 'entered'。
     * <p>balances 为空时表示"确认期初全为 0"，仍标记为 entered（支持零余额企业）。</p>
     * <p>前置条件：期间存在、status=open、opening_status != 'locked'。</p>
     */
    void initOpeningBalances(String period, Map<Long, BigDecimal> balances);

    /**
     * 期初建账（P58）：支持任意指定录入时间（建账日期）。
     * 建账成功后写入 t_period.opened_at/opened_by/opened_by_name，opening_status='entered'。
     * openedAt 为 null 时取当前时间（向前兼容）。
     */
    void initOpeningBalances(String period, java.time.LocalDateTime openedAt, Map<Long, BigDecimal> balances);

    List<SubjectBalanceEntity> queryByPeriod(String period);

    /**
     * 按期间查询科目余额，并注入科目编码/名称/方向（供列表展示）
     */
    List<SubjectBalanceVO> queryByPeriodWithSubject(String period);

    Map<String, Object> checkTrialBalance(String period);

    /**
     * 锁定期初：校验试算平衡通过后，将 t_period.opening_status 置为 'locked'。
     * 锁定后凭证业务仍可正常过账（不影响 t_period.status）。
     */
    void lockOpeningBalances(String period);

    /**
     * 解锁期初：将 t_period.opening_status 从 'locked' 置回 'entered'。
     */
    void unlockOpeningBalances(String period);

    /**
     * 清空期初余额：物理删除该期间所有 t_subject_balance 记录，opening_status 置为 'none'。
     * 前置条件：opening_status != 'locked'、期间无 POSTED 凭证。
     */
    void clearOpeningBalances(String period);

    /**
     * 凭证过账前置校验：若目标期间是企业最早业务期间且 opening_status='none'，则拒绝过账。
     * 其他场景（如非最早期、或 opening_status=entered/locked）放行。
     * 设计目的：防止"跳过期初直接过账"导致 findOrCreate 用 begin=0 占位、期初数据无法补录的死锁。
     */
    void validateOpeningBeforePost(String period);

    /**
     * 查询期间实体（含 opening_status）。企业隔离由拦截器自动注入。
     */
    PeriodEntity getPeriodEntity(String period);
}
