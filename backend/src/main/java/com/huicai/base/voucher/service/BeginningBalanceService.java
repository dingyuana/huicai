package com.huicai.base.voucher.service;

import com.huicai.base.balance.entity.SubjectBalanceEntity;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

/**
 * 期初建账服务.
 * <p>
 * 封装期初余额录入、试算平衡校验、辅助核算期初等业务逻辑。
 * 核心 ORM 操作委托 {@link SubjectBalanceService}。
 * </p>
 */
public interface BeginningBalanceService {

    /**
     * 批量录入期初余额.
     *
     * @param period   会计期间 (YYYYMM)
     * @param balances 科目ID → 期初余额
     * @throws com.huicai.common.exception.BusinessException 如果试算不平衡或数据非法
     */
    void batchInput(String period, Map<Long, BigDecimal> balances);

    /**
     * 查询期初余额列表.
     *
     * @param period 会计期间 (YYYYMM)
     * @return 期初余额列表
     */
    List<SubjectBalanceEntity> listByPeriod(String period);

    /**
     * 试算平衡检查.
     *
     * @param period 会计期间 (YYYYMM)
     * @return 检查结果 (balanced/issues/detail)
     */
    Map<String, Object> checkTrialBalance(String period);

    /**
     * 清空指定期间的期初余额.
     * 仅允许清空未发生业务的期间。
     *
     * @param period 会计期间 (YYYYMM)
     * @throws com.huicai.common.exception.BusinessException 如果期间已有业务发生
     */
    void clearByPeriod(String period);
}