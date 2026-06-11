package com.huicai.module.finance.service;

import java.util.List;
import java.util.Map;

/**
 * 期末结账服务
 */
public interface PeriodCloseService {

    /**
     * 结账前检查
     */
    Map<String, Object> checkBeforeClose(String period);

    /**
     * 生成结转损益凭证（汇总损益类科目本期发生额，生成红冲凭证）
     */
    Long generateProfitCarryOver(String period, Long userId);

    /**
     * 执行结账：检查 + 自动损益结转 + 锁定期间
     */
    void closePeriod(String period, Long userId);

    /**
     * 反结账：恢复期间为 OPEN，删除自动结转凭证
     */
    void reopenPeriod(String period, Long userId);

    /**
     * 结账日志列表
     */
    List<Map<String, Object>> listCloseLog(String period);
}
