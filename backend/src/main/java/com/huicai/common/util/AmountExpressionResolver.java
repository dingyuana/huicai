package com.huicai.common.util;

import javax.script.ScriptEngineManager;
import javax.script.ScriptEngine;
import javax.script.ScriptException;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Map;

/**
 * 金额表达式解析器.
 * <p>
 * 支持四则运算: + - * / ( )
 * 输入安全校验: 仅允许数字、运算符、括号、小数点、空格.
 * 使用 Java ScriptEngine 轻量求值，不做任意代码执行.
 */
public final class AmountExpressionResolver {

    private static final ScriptEngine ENGINE = new ScriptEngineManager().getEngineByName("JavaScript");
    private static final String SAFE_PATTERN = "^[0-9+\\-*/().\\s]+$";

    private AmountExpressionResolver() {}

    /**
     * 解析表达式，返回 BigDecimal（保留 2 位小数，HALF_UP）.
     *
     * @param expression 表达式字符串，如 "1000.50 + 200.30"
     * @return 计算结果
     * @throws IllegalArgumentException 表达式无效或计算异常
     */
    public static BigDecimal evaluate(String expression) {
        if (expression == null || expression.trim().isEmpty()) {
            return BigDecimal.ZERO;
        }
        String expr = expression.trim();
        if (!expr.matches(SAFE_PATTERN)) {
            throw new IllegalArgumentException("不安全的表达式: " + expr);
        }
        try {
            Object result = ENGINE.eval(expr);
            return new BigDecimal(result.toString()).setScale(2, RoundingMode.HALF_UP);
        } catch (ScriptException e) {
            throw new IllegalArgumentException("表达式计算失败: " + expr, e);
        }
    }

    /**
     * 将模板表达式中的变量替换为数值后求值.
     *
     * @param template 模板表达式，如 "{{amount}} - {{taxAmount}}"
     * @param variables 变量名→数值映射
     * @return 计算结果
     */
    public static BigDecimal evaluateTemplate(String template, Map<String, BigDecimal> variables) {
        if (template == null || template.trim().isEmpty()) {
            return BigDecimal.ZERO;
        }
        String expr = template;
        for (Map.Entry<String, BigDecimal> entry : variables.entrySet()) {
            String key = entry.getKey();
            BigDecimal val = entry.getValue();
            if (key != null && val != null) {
                expr = expr.replace("{{" + key + "}}", val.toPlainString());
            } else if (key != null) {
                expr = expr.replace("{{" + key + "}}", "0");
            }
        }
        return evaluate(expr);
    }
}