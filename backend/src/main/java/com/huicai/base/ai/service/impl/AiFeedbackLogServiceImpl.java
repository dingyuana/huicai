package com.huicai.base.ai.service.impl;

import cn.hutool.core.util.StrUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.huicai.common.exception.BusinessException;
import com.huicai.base.ai.entity.AiFeedbackLogEntity;
import com.huicai.base.ai.mapper.AiFeedbackLogMapper;
import com.huicai.base.ai.service.AiFeedbackLogService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;
import java.util.stream.Collectors;

/**
 * AI 分类反馈日志 Service 实现
 */
@Service
@RequiredArgsConstructor
public class AiFeedbackLogServiceImpl implements AiFeedbackLogService {

    private static final Set<String> VALID_HUMAN_ACTIONS = Set.of(
            "CONFIRM_AI", "MANUAL_RECLASSIFY", "IGNORE_AI", "BATCH_CONFIRM"
    );

    private final AiFeedbackLogMapper mapper;

    @Override
    public IPage<AiFeedbackLogEntity> page(Long tenantId, Long bankTxnId, String humanAction,
                                           Integer current, Integer size) {
        Page<AiFeedbackLogEntity> page = new Page<>(
                current == null ? 1 : current,
                size == null ? 20 : size
        );
        LambdaQueryWrapper<AiFeedbackLogEntity> wrapper = new LambdaQueryWrapper<>();
        if (tenantId != null) {
            wrapper.eq(AiFeedbackLogEntity::getTenantId, tenantId);
        }
        if (bankTxnId != null) {
            wrapper.eq(AiFeedbackLogEntity::getBankTxnId, bankTxnId);
        }
        if (StrUtil.isNotBlank(humanAction)) {
            wrapper.eq(AiFeedbackLogEntity::getHumanAction, humanAction);
        }
        wrapper.orderByDesc(AiFeedbackLogEntity::getCreatedAt);
        return mapper.selectPage(page, wrapper);
    }

    @Override
    public AiFeedbackLogEntity getById(Long id) {
        return mapper.selectById(id);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public AiFeedbackLogEntity create(AiFeedbackLogEntity entity) {
        // humanAction 入参校验
        if (entity.getHumanAction() == null || !VALID_HUMAN_ACTIONS.contains(entity.getHumanAction())) {
            throw BusinessException.badRequest(
                    "humanAction 必须为: " + String.join(", ", VALID_HUMAN_ACTIONS)
            );
        }
        if (entity.getCreatedBy() == null) {
            entity.setCreatedBy(1L);
        }
        mapper.insert(entity);
        return entity;
    }

    @Override
    public List<Map<String, Object>> summaryByTenant(Long tenantId) {
        LambdaQueryWrapper<AiFeedbackLogEntity> wrapper = new LambdaQueryWrapper<>();
        if (tenantId != null) {
            wrapper.eq(AiFeedbackLogEntity::getTenantId, tenantId);
        }
        List<AiFeedbackLogEntity> list = mapper.selectList(wrapper);

        // 按 humanAction 分组统计
        Map<String, List<AiFeedbackLogEntity>> grouped = list.stream()
                .collect(Collectors.groupingBy(AiFeedbackLogEntity::getHumanAction));

        List<Map<String, Object>> result = new ArrayList<>();
        for (Map.Entry<String, List<AiFeedbackLogEntity>> entry : grouped.entrySet()) {
            Map<String, Object> stat = new LinkedHashMap<>();
            stat.put("humanAction", entry.getKey());
            stat.put("count", entry.getValue().size());
            // 平均置信度
            double avgConfidence = entry.getValue().stream()
                    .filter(e -> e.getAiConfidence() != null)
                    .mapToInt(AiFeedbackLogEntity::getAiConfidence)
                    .average()
                    .orElse(0.0);
            stat.put("avgConfidence", Math.round(avgConfidence * 100.0) / 100.0);
            result.add(stat);
        }
        result.sort(Comparator.comparing(m -> (String) m.get("humanAction")));
        return result;
    }

    @Override
    public List<Map<String, Object>> recentByBankTxn(Long bankTxnId) {
        LambdaQueryWrapper<AiFeedbackLogEntity> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(AiFeedbackLogEntity::getBankTxnId, bankTxnId);
        wrapper.orderByDesc(AiFeedbackLogEntity::getCreatedAt);
        wrapper.last("LIMIT 10");

        List<AiFeedbackLogEntity> list = mapper.selectList(wrapper);
        List<Map<String, Object>> result = new ArrayList<>();
        for (AiFeedbackLogEntity entity : list) {
            Map<String, Object> map = new LinkedHashMap<>();
            map.put("id", entity.getId());
            map.put("humanAction", entity.getHumanAction());
            map.put("aiSuggestedAction", entity.getAiSuggestedAction());
            map.put("aiConfidence", entity.getAiConfidence());
            map.put("humanModifiedFields", entity.getHumanModifiedFields());
            map.put("createdAt", entity.getCreatedAt());
            map.put("createdBy", entity.getCreatedBy());
            result.add(map);
        }
        return result;
    }

    @Override
    public void deleteByBankTxn(Long bankTxnId) {
        LambdaQueryWrapper<AiFeedbackLogEntity> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(AiFeedbackLogEntity::getBankTxnId, bankTxnId);
        mapper.delete(wrapper);
    }
}
