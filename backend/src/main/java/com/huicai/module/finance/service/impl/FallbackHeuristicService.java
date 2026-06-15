package com.huicai.module.finance.service.impl;

import cn.hutool.core.util.StrUtil;
import lombok.Getter;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * 兜底启发式分类 — 三层分类架构的第三层.
 * <p>
 * 当规则引擎 (第一层) 和 AI 语义 (第二层) 均未命中时,
 * 按 9 级关键词分组 100% 给出 classification, 永不返回 null.
 * <p>
 * 优先级 (1 最高, 9 关键词兜底, 10 方向兜底):
 * <pre>
 *   1  bank_fee         out    手续费/工本费/年费/账户管理费
 *   2  interest_income  in     利息/结息/存款利息
 *   3  tax_payment      out    税/税务/缴税/税金/增值税/所得税/城建税/教育费附加/国库/印花
 *   4  social_security  out    社保/公积金/养老/医疗/失业/工伤/生育
 *   5  insurance_fee    out    保险/保费/投保
 *   6  salary_payment   out    工资/薪资/薪酬/劳务费/奖金/津贴
 *   7  business_receipt in     货款/收款/销售/回款/客户/应收/收入
 *   8  business_payment out    货款/付款/采购/支付/供应商/应付/支出
 *   9  internal_transfer (不限) 转账/转存/调拨/上划/下拨/内部
 *  10  方向兜底 (低置信度)    in→business_receipt, out→business_payment, null→pending
 * </pre>
 * <p>
 * <b>方向兜底 (priority=10)</b>：当摘要中无任何关键词命中时, 按金额方向推断业务类别,
 * 标记为低置信度 (aiConfidence 需配合 < 60 处理, 由调用方决定).
 * 仍无方向信息时返回 pending, 交由人工确认.
 */
@Service
public class FallbackHeuristicService {

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

    /** 一条兜底规则的定义 */
    private static class FallbackRule {
        final int priority;
        final String classification;
        final String[] keywords;
        final String direction;           // null = 不限方向

        FallbackRule(int priority, String classification, String keywordsStr, String direction) {
            this.priority = priority;
            this.classification = classification;
            this.keywords = keywordsStr.split("\\|");
            this.direction = direction;
        }
    }

    private static final List<FallbackRule> RULES = buildRules();

    private static List<FallbackRule> buildRules() {
        List<FallbackRule> list = new ArrayList<>(9);
        list.add(new FallbackRule(1, "bank_fee",          "手续费|工本费|年费|账户管理费",      "out"));
        list.add(new FallbackRule(2, "interest_income",   "利息|结息|存款利息",                "in"));
        list.add(new FallbackRule(3, "tax_payment",       "税|税务|缴税|税金|增值税|所得税" +
                "|城建税|教育费附加|国库|金库|印花|国家金库", "out"));
        list.add(new FallbackRule(4, "social_security",   "社保|公积金|养老|医疗|失业|工伤|生育", "out"));
        list.add(new FallbackRule(5, "insurance_fee",     "保险|保费|投保",                     "out"));
        list.add(new FallbackRule(6, "salary_payment",    "工资|薪资|薪酬|劳务费|奖金|津贴",    "out"));
        list.add(new FallbackRule(7, "business_receipt",  "货款|收款|销售|回款|客户|应收|收入",  "in"));
        list.add(new FallbackRule(8, "business_payment",  "货款|付款|采购|支付|供应商" +
                "|应付|支出",           "out"));
        list.add(new FallbackRule(9, "internal_transfer", "转账|转存|调拨|上划|下拨|内部",      null));
        // 第 10 级是「方向兜底」, 不参与循环匹配 — 由 classify() 末尾的 direction-based fallback 处理
        return List.copyOf(list);
    }

    /**
     * 兜底启发式分类.
     *
     * <p>匹配顺序:
     * <ol>
     *   <li>9 级关键词规则 (1-9 优先)</li>
     *   <li>方向兜底 (priority=10): 按 amount direction 推断业务收/付, 标记低置信度 (aiConfidence=50)</li>
     *   <li>最终兜底: pending (priority=10, 也标记低置信度, 等人工确认)</li>
     * </ol>
     *
     * @param description 流水摘要, 为空时直接进入方向兜底
     * @param direction   业务方向 in/out, 可为 null
     * @return Result, classification 永不返回 null
     */
    public Result classify(String description, String direction) {
        if (StrUtil.isNotBlank(description)) {
            for (FallbackRule rule : RULES) {
                // 方向过滤: 规则限定方向时需匹配
                if (rule.direction != null && !rule.direction.equalsIgnoreCase(direction)) {
                    continue;
                }
                // 关键词匹配
                for (String kw : rule.keywords) {
                    if (StrUtil.isNotBlank(kw) && description.contains(kw.trim())) {
                        return new Result(rule.classification, rule.priority, kw.trim());
                    }
                }
            }
        }

        // 方向兜底: 摘要中无任何关键词命中 (或摘要为空), 按方向推断业务收/付
        // in  → business_receipt
        // out → business_payment
        // null → pending (等人工确认)
        if ("in".equalsIgnoreCase(direction)) {
            return new Result("business_receipt", 10, "[direction:in]");
        }
        if ("out".equalsIgnoreCase(direction)) {
            return new Result("business_payment", 10, "[direction:out]");
        }
        return new Result("pending", 10, null);
    }
}
