package com.huicai.base.voucher.service;

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
     * 生成利润分配凭证（按本年利润期末余额提取盈余公积）
     */
    Long generateProfitDistribution(String period, Long userId);

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

    /**
     * 一键人工审核记账（P84 结账工作台第 2 步）：同事务完成 DRAFT→SUBMITTED→AUDITED→POSTED。
     *
     * <p>用户在 Drawer 勾选凭证后<b>主动触发</b>，属人工操作，符合"审核必须人工完成"铁律
     * —— 本方法不会在任何自动生成后自动执行。
     *
     * <p>原子性：四步状态链在同一事务内完成，任一步失败则整体回滚，不会留下
     * "部分已审核未记账"的中间态。实现通过 REQUIRED 传播嵌套调用
     * {@code batchSubmit}/{@code batchAudit}/{@code batchPost} 的校验逻辑，
     * 复用以避免状态机规则重复。
     *
     * @param voucherIds 待处理凭证 id（须均为 DRAFT 且期间未结账）
     * @param userId     操作人 id
     * @throws com.huicai.common.exception.BusinessException 列表为空/凭证不存在/状态不符/期间已结账
     */
    void batchReviewPost(List<Long> voucherIds, Long userId);
}
