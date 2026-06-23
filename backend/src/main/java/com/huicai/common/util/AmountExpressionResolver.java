package com.huicai.common.util;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * 金额表达式解析器.
 * <p>
 * 支持四则运算: + - * / ( )
 * 使用递归下降解析，不含外部依赖.
 */
public final class AmountExpressionResolver {

    private static final String SAFE_PATTERN = "^[0-9+\\-*/().\\s]+$";

    private AmountExpressionResolver() {}

    /**
     * 解析表达式，返回 BigDecimal（保留 2 位小数，HALF_UP）.
     */
    public static BigDecimal evaluate(String expression) {
        if (expression == null || expression.trim().isEmpty()) return BigDecimal.ZERO;
        String expr = expression.trim();
        if (!expr.matches(SAFE_PATTERN)) {
            throw new IllegalArgumentException("不安全的表达式: " + expr);
        }
        try {
            double result = new Parser(expr).parse();
            return BigDecimal.valueOf(result).setScale(2, RoundingMode.HALF_UP);
        } catch (Exception e) {
            throw new IllegalArgumentException("表达式计算失败: " + expr, e);
        }
    }

    /**
     * 将模板表达式中的变量替换为数值后求值.
     */
    public static BigDecimal evaluateTemplate(String template, Map<String, BigDecimal> variables) {
        if (template == null || template.trim().isEmpty()) return BigDecimal.ZERO;
        String expr = template;
        for (Map.Entry<String, BigDecimal> entry : variables.entrySet()) {
            if (entry.getKey() != null && entry.getValue() != null) {
                expr = expr.replace("{{" + entry.getKey() + "}}", entry.getValue().toPlainString());
            } else if (entry.getKey() != null) {
                expr = expr.replace("{{" + entry.getKey() + "}}", "0");
            }
        }
        return evaluate(expr);
    }

    // ====== 递归下降解析器 ======

    private static class Parser {
        private final List<String> tokens;
        private int pos;

        Parser(String expr) {
            this.tokens = tokenize(expr);
            this.pos = 0;
        }

        double parse() {
            double result = parseAddSub();
            if (pos < tokens.size()) throw new IllegalArgumentException("多余token: " + tokens.get(pos));
            return result;
        }

        // + -
        private double parseAddSub() {
            double left = parseMulDiv();
            while (pos < tokens.size()) {
                String op = tokens.get(pos);
                if ("+".equals(op)) { pos++; left += parseMulDiv(); }
                else if ("-".equals(op)) { pos++; left -= parseMulDiv(); }
                else break;
            }
            return left;
        }

        // * /
        private double parseMulDiv() {
            double left = parseUnary();
            while (pos < tokens.size()) {
                String op = tokens.get(pos);
                if ("*".equals(op)) { pos++; left *= parseUnary(); }
                else if ("/".equals(op)) { pos++; double right = parseUnary(); if (right == 0) throw new ArithmeticException("除零"); left /= right; }
                else break;
            }
            return left;
        }

        // 负号 + ( )
        private double parseUnary() {
            if (pos >= tokens.size()) throw new IllegalArgumentException("表达式不完整");
            String tok = tokens.get(pos);
            if ("-".equals(tok)) {
                pos++;
                return -parseUnary();
            }
            if ("+".equals(tok)) {
                pos++;
                return parseUnary();
            }
            if ("(".equals(tok)) {
                pos++;
                double val = parseAddSub();
                if (pos >= tokens.size() || !")".equals(tokens.get(pos))) throw new IllegalArgumentException("缺右括号");
                pos++;
                return val;
            }
            return parseNumber();
        }

        private double parseNumber() {
            String tok = tokens.get(pos++);
            return Double.parseDouble(tok);
        }

        private static List<String> tokenize(String expr) {
            List<String> result = new ArrayList<>();
            StringBuilder num = new StringBuilder();
            for (int i = 0; i < expr.length(); i++) {
                char c = expr.charAt(i);
                if (Character.isWhitespace(c)) {
                    flushNumber(result, num);
                    continue;
                }
                if ("+-*/()".indexOf(c) >= 0) {
                    flushNumber(result, num);
                    result.add(String.valueOf(c));
                } else if (Character.isDigit(c) || c == '.') {
                    num.append(c);
                } else {
                    throw new IllegalArgumentException("非法字符: " + c);
                }
            }
            flushNumber(result, num);
            return result;
        }

        private static void flushNumber(List<String> tokens, StringBuilder num) {
            if (num.length() > 0) {
                tokens.add(num.toString());
                num.setLength(0);
            }
        }
    }
}