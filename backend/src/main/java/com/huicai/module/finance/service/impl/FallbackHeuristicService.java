package com.huicai.module.finance.service.impl;

import cn.hutool.core.util.StrUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.huicai.module.finance.constant.BankClassification;
import com.huicai.module.finance.entity.ClassificationRuleEntity;
import com.huicai.module.finance.mapper.ClassificationRuleMapper;
import jakarta.annotation.PostConstruct;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

/**
 * 兜底启发式分类 — 三层分类架构的第三层.
 * <p>
 * 当规则引擎 (第一层) 和 AI 语义 (第二层) 均未命中时,
 * 从 DB 中读取系统内置规则 (is_system=true) 进行兜底分类, 永不返回 null.
 * <p>
 * 与 ClassificationRuleService.match() 的区别:
 * <ul>
 *   <li>match() 遍历所有用户规则 (is_system=false) + 系统兜底规则</li>
 *   <li>FallbackHeuristicService 仅读取系统兜底规则 (is_system=true)</li>
 *   <li>match() 由分类触发, FallbackHeuristicService 在规则引擎未命中时调用</li>
 * </ul>
 */
@Service
@RequiredArgsConstructor
public class FallbackHeuristicService {

    private final ClassificationRuleMapper ruleMapper;

    @Getter
    public static class Result {
        private final String classification;
        private final int priority;
        private final String matchedKeyword;

        Result(String classification, int priority, String matchedKeyword) {
            this.classification = classification;
            this.priority = priority;
            this.matchedKeyword = matchedKeyword;
        }
    }

    /**
     * 兜底启发式分类.
     *
     * <p>匹配顺序:
     * <ol>
     *   <li>DB 中系统兜底规则 (is_system=true, priority 90-98, 不含方向兜底)</li>
     *   <li>方向兜底 (priority=10): 按 amount direction 推断业务收/付</li>
     *   <li>最终兜底: pending (等人工确认)</li>
     * </ol>
     *
     * @param description 流水摘要, 为空时直接进入方向兜底
     * @param direction   业务方向 in/out/空串, 可为 null
     * @return Result, classification 永不返回 null
     */
    public Result classify(String description, String direction) {
        // 1. DB 系统兜底规则
        if (StrUtil.isNotBlank(description)) {
            List<ClassificationRuleEntity> rules = loadSystemRules();
            for (ClassificationRuleEntity rule : rules) {
                if (!matchDirection(rule, direction)) continue;
                String matched = matchPattern(description, rule);
                if (matched != null) {
                    return new Result(rule.getClassification(), rule.getPriority(), matched);
                }
            }
        }

        // 2. 方向兜底: 按方向推断业务收/付
        if ("in".equalsIgnoreCase(direction)) {
            return new Result(BankClassification.BUSINESS_RECEIPT, 10, "[direction:in]");
        }
        if ("out".equalsIgnoreCase(direction)) {
            return new Result(BankClassification.BUSINESS_PAYMENT, 10, "[direction:out]");
        }

        // 3. 最终兜底: other_unknown
        return new Result(BankClassification.OTHER_UNKNOWN, 10, null);
    }

    private boolean matchDirection(ClassificationRuleEntity rule, String direction) {
        if (StrUtil.isBlank(rule.getDirection())) return true;
        return rule.getDirection().equalsIgnoreCase(direction);
    }

    /**
     * 按规则的 ruleType 执行匹配.
     * keyword_regex 用 | 分隔; keyword/counterparty_match 做包含匹配.
     *
     * @return 匹配到的关键词, 未命中返回 null
     */
    private String matchPattern(String text, ClassificationRuleEntity rule) {
        if (StrUtil.isBlank(rule.getPattern())) return null;

        switch (rule.getRuleType()) {
            case "keyword":
            case "counterparty_match":
                if (text.contains(rule.getPattern().trim())) {
                    return rule.getPattern().trim();
                }
                break;
            default: // keyword_regex
                String[] keywords = rule.getPattern().split("\\|");
                for (String kw : keywords) {
                    if (StrUtil.isNotBlank(kw) && text.contains(kw.trim())) {
                        return kw.trim();
                    }
                }
                break;
        }
        return null;
    }

    /** 缓存系统兜底规则 (启动时加载 + 惰性刷新) */
    private volatile List<ClassificationRuleEntity> systemRuleCache = null;
    private volatile long cacheLoadedAt = 0;
    private static final long CACHE_TTL_MS = 60_000; // 1 分钟

    private List<ClassificationRuleEntity> loadSystemRules() {
        long now = System.currentTimeMillis();
        if (systemRuleCache != null && (now - cacheLoadedAt) < CACHE_TTL_MS) {
            return systemRuleCache;
        }
        List<ClassificationRuleEntity> rules = ruleMapper.selectList(
                new LambdaQueryWrapper<ClassificationRuleEntity>()
                        .eq(ClassificationRuleEntity::getIsSystem, true)
                        .eq(ClassificationRuleEntity::getIsActive, true)
                        .eq(ClassificationRuleEntity::getDeleted, 0)
                        .orderByAsc(ClassificationRuleEntity::getPriority)
        );
        systemRuleCache = rules != null ? rules : new ArrayList<>();
        cacheLoadedAt = now;
        return systemRuleCache;
    }
}
