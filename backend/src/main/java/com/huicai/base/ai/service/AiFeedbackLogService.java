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
     * 反馈日志分页（支持 tenantId / bankTxnId / humanAction 过滤）
     */
    IPage<AiFeedbackLogEntity> page(Long tenantId, Long bankTxnId, String humanAction, Integer current, Integer size);

    /**
     * 反馈日志详情
     */
    AiFeedbackLogEntity getById(Long id);

    /**
     * 记录反馈
     */
    AiFeedbackLogEntity create(AiFeedbackLogEntity entity);

    /**
     * 按租户统计：每种 human_action 的次数 + 平均 ai_confidence
     */
    List<Map<String, Object>> summaryByTenant(Long tenantId);

    /**
     * 查询某流水的所有反馈（按创建时间倒序，最多 10 条）
     */
    List<Map<String, Object>> recentByBankTxn(Long bankTxnId);

    /**
     * 删除某流水的所有反馈
     */
    void deleteByBankTxn(Long bankTxnId);
}
