package com.huicai.common.util;

import cn.hutool.core.util.StrUtil;

import java.math.BigDecimal;
import java.util.HashMap;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 凭证模板引擎 — 变量替换 + 金额表达式解析.
 *
 * <p>支持变量:
 * <ul>
 *   <li>{{amount}} / {{taxAmount}} / {{totalAmount}} — 金额取数</li>
 *   <li>{客户名称} / {供应商名称} / {员工姓名} — 摘要业务变量</li>
 *   <li>{月份} / {年度} — 期间变量</li>
 * </ul>
 * 金额表达式: "{{amount}}" / "{{amount}} - {{taxAmount}}" — 四则运算
 */
public final class TemplateEngine {

    private static final Pattern VAR_PATTERN = Pattern.compile("\\{([^}]+)\\}");

    private TemplateEngine() {}

    /**
     * 渲染摘要模板.
     * renderSummary("收{客户名称}货款", ctx) -> "收北京华联货款"
     */
    public static String renderSummary(String template, TemplateContext ctx) {
        if (StrUtil.isBlank(template)) {
            return ctx.getSummary() != null ? ctx.getSummary() : "";
        }
        String result = replaceAmountVars(template, ctx);
        result = replaceBizVars(result, ctx);
        return result;
    }

    /**
     * 渲染金额模板.
     * renderAmount("{{amount}}", ctx) -> ctx.amount
     * renderAmount("{{amount}}-{{taxAmount}}", ctx) -> amount - tax
     */
    public static BigDecimal renderAmount(String template, TemplateContext ctx) {
        if (StrUtil.isBlank(template)) return BigDecimal.ZERO;
        String expr = template.trim();
        if (expr.matches("^-?\\d+(\\.\\d+)?$")) return new BigDecimal(expr);
        if ("{{amount}}".equals(expr)) return nz(ctx.getAmount());
        if ("{{taxAmount}}".equals(expr)) return nz(ctx.getTaxAmount());
        if ("{{totalAmount}}".equals(expr)) return nz(ctx.getTotalAmount());
        Map<String, BigDecimal> vars = new HashMap<>();
        vars.put("amount", nz(ctx.getAmount()));
        vars.put("taxAmount", nz(ctx.getTaxAmount()));
        vars.put("totalAmount", nz(ctx.getTotalAmount()));
        return AmountExpressionResolver.evaluateTemplate(expr, vars);
    }

    // ====== 内部 ======

    private static String replaceAmountVars(String template, TemplateContext ctx) {
        String s = template;
        if (ctx.getAmount() != null) s = s.replace("{{amount}}", fmt(ctx.getAmount()));
        if (ctx.getTaxAmount() != null) s = s.replace("{{taxAmount}}", fmt(ctx.getTaxAmount()));
        if (ctx.getTotalAmount() != null) s = s.replace("{{totalAmount}}", fmt(ctx.getTotalAmount()));
        if (ctx.getPeriod() != null) s = s.replace("{{period}}", ctx.getPeriod());
        if (ctx.getSummary() != null) s = s.replace("{{summary}}", ctx.getSummary());
        return s;
    }

    private static String replaceBizVars(String template, TemplateContext ctx) {
        StringBuffer sb = new StringBuffer();
        Matcher m = VAR_PATTERN.matcher(template);
        while (m.find()) {
            String key = m.group(1);
            String val = lookupBizVar(key, ctx);
            m.appendReplacement(sb, Matcher.quoteReplacement(val != null ? val : ""));
        }
        m.appendTail(sb);
        return sb.toString();
    }

    private static String lookupBizVar(String key, TemplateContext ctx) {
        switch (key) {
            case "客户名称": return nn(ctx.getCustomerName());
            case "供应商名称": return nn(ctx.getVendorName());
            case "员工姓名": return nn(ctx.getEmployeeName());
            case "摘要": return nn(ctx.getSummary());
            case "银行名称": return nn(ctx.getCounterpartyName());
            case "对方户名": return nn(ctx.getCounterpartyName());
            case "月份": return ctx.getPeriod() != null ? ctx.getPeriod() : "";
            case "年度": return (ctx.getPeriod() != null && ctx.getPeriod().length() >= 4)
                    ? ctx.getPeriod().substring(0, 4) : "";
            default:
                if (ctx.getVariables() != null && ctx.getVariables().containsKey(key)) {
                    Object v = ctx.getVariables().get(key);
                    return v != null ? v.toString() : "";
                }
                return "";
        }
    }

    private static String nn(String s) { return s != null ? s : ""; }
    private static String fmt(BigDecimal d) { return d.setScale(2, java.math.RoundingMode.HALF_UP).toPlainString(); }
    private static BigDecimal nz(BigDecimal d) { return d != null ? d : BigDecimal.ZERO; }
}