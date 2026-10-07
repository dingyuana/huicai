package com.huicai.base.ai.service.impl;

import cn.hutool.core.util.StrUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.huicai.common.context.EnterpriseContextHolder;
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

    /**
     * 取当前企业上下文；缺失即抛错（fail-closed）。
     *
     * <p>同 {@code ClassificationRuleServiceImpl#requireEnterpriseContext}：D-1 初版
     * {@code if (ctx != null) eq(...)} 在无上下文时是「不过滤 = 返回全表」，与注释
     * 声称的「读空集合」相反，属 fail-open。
     */
    private Long requireEnterpriseContext(String scene) {
        Long ctx = EnterpriseContextHolder.get();
        if (ctx == null) {
            throw new BusinessException("无当前企业上下文，无法" + scene);
        }
        return ctx;
    }

    @Override
    public IPage<AiFeedbackLogEntity> page(Long bankTxnId, String humanAction,
                                           Integer current, Integer size) {
        Page<AiFeedbackLogEntity> page = new Page<>(
                current == null ? 1 : current,
                size == null ? 20 : size
        );
        // P106 批次 1a-3（D-1）：废弃客户端传 tenantId，按当前企业上下文的 enterprise_id 过滤。
        Long ctx = requireEnterpriseContext("查询 AI 反馈日志");
        LambdaQueryWrapper<AiFeedbackLogEntity> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(AiFeedbackLogEntity::getEnterpriseId, ctx);
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
    public List<Map<String, Object>> summary() {
        Long ctx = requireEnterpriseContext("汇总 AI 反馈日志");
        LambdaQueryWrapper<AiFeedbackLogEntity> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(AiFeedbackLogEntity::getEnterpriseId, ctx);
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
