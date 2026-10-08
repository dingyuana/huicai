package com.huicai.sme.cash.service;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.huicai.base.business.entity.ClassificationRuleEntity;

import java.util.List;

/**
 * 分类规则 Service 接口
 */
public interface ClassificationRuleService {

    /**
     * 规则列表分页。
     *
     * <p>P106 批次 1a-3（D-1）：已移除 {@code tenantId} 入参，改按当前上下文企业的
     * {@code enterprise_id} 过滤；无企业上下文时抛 {@code BusinessException}（fail-closed）。
     */
    IPage<ClassificationRuleEntity> page(Integer current, Integer size);

    /**
     * 规则详情
     */
    ClassificationRuleEntity getById(Long id);

    /**
     * 新增规则
     */
    ClassificationRuleEntity create(ClassificationRuleEntity entity);

    /**
     * 更新规则
     */
    ClassificationRuleEntity update(Long id, ClassificationRuleEntity entity);

    /**
     * 删除规则（逻辑删除）
     */
    void delete(Long id);

    /**
     * 拖拽排序（按 ids 顺序设 priority 1,2,3...）
     */
    void reorder(List<Long> ids);

    /**
     * P106 批次 1a-3（D-1）：废弃 {@code tenantId} 请求参数，改用当前上下文企业。
     * 种子幂等只看 {@code enterprise_id = 当前上下文企业}。
     *
     * @return 插入条数
     */
    int seedForCurrentEnterprise();

    /**
     * 单笔测试匹配
     *
     * @param description 流水摘要
     * @param direction   业务方向 in/out
     * @param counterparty 对方户名 (可为 null)
     * @return 命中的第一条规则, 无匹配返回 null
     */
    ClassificationRuleEntity match(String description, String direction, String counterparty);
}
