package com.huicai.sme.cash.service;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.huicai.base.business.entity.ClassificationRuleEntity;

import java.util.List;

/**
 * 分类规则 Service 接口
 */
public interface ClassificationRuleService {

    /**
     * 规则列表分页
     */
    IPage<ClassificationRuleEntity> page(Long tenantId, Integer current, Integer size);

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
     * 为新租户初始化种子规则（幂等）
     *
     * @return 插入条数
     */
    int seedForNewTenant(Long tenantId);

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
