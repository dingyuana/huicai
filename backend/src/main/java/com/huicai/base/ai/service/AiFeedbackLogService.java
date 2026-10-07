package com.huicai.base.ai.service;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.huicai.base.ai.entity.AiFeedbackLogEntity;

import java.util.List;
import java.util.Map;

/**
 * AI 分类反馈日志 Service 接口
 */
public interface AiFeedbackLogService {

    /**
     * 反馈日志分页（支持 bankTxnId / humanAction 过滤）。
     *
     * <p>P106 批次 1a-3（D-1）：已移除 {@code tenantId} 入参，改按当前上下文企业的
     * {@code enterprise_id} 过滤；无企业上下文时抛 {@code BusinessException}（fail-closed）。
     */
    IPage<AiFeedbackLogEntity> page(Long bankTxnId, String humanAction, Integer current, Integer size);

    /**
     * 反馈日志详情
     */
    AiFeedbackLogEntity getById(Long id);

    /**
     * 记录反馈
     */
    AiFeedbackLogEntity create(AiFeedbackLogEntity entity);

    /**
     * 按当前企业统计：每种 human_action 的次数 + 平均 ai_confidence
     *
     * <p>P106 批次 1a-3（D-1）：已移除 {@code tenantId} 入参，改按
     * {@code EnterpriseContextHolder.get()} 取当前上下文企业；无上下文时抛
     * {@code BusinessException}（fail-closed）。</p>
     */
    List<Map<String, Object>> summary();

    /**
     * 查询某流水的所有反馈（按创建时间倒序，最多 10 条）
     */
    List<Map<String, Object>> recentByBankTxn(Long bankTxnId);

    /**
     * 删除某流水的所有反馈
     */
    void deleteByBankTxn(Long bankTxnId);
}
