package com.huicai.common.util;

/**
 * 手写 JSON 字符串字段值的安全构造（SPEC-P152）。
 *
 * <p>审计/错误留痕走 JSONB 列，after_data 常由「异常 message / 前端堆栈」拼出。
 * 这些文本里出现引号、反斜杠、换行是常态（Java 堆栈全是），转义漏一项
 * 整列写入失败（AGENTS §4.5 第 66 条同款风险）。集中一处转义实现，
 * GlobalExceptionHandler 与 ClientLogController 共用，杜绝各写一份漂移。</p>
 */
public final class JsonSafe {

    private JsonSafe() {
    }

    /** 转义 JSON 字符串<b>值</b>（不含首尾引号）。null → 空串。 */
    public static String escape(String s) {
        if (s == null) return "";
        StringBuilder sb = new StringBuilder(s.length() + 16);
        for (char c : s.toCharArray()) {
            switch (c) {
                case '"' -> sb.append("\\\"");
                case '\\' -> sb.append("\\\\");
                case '\n' -> sb.append("\\n");
                case '\r' -> sb.append("\\r");
                case '\t' -> sb.append("\\t");
                default -> {
                    if (c < 0x20) sb.append(String.format("\\u%04x", (int) c));
                    else sb.append(c);
                }
            }
        }
        return sb.toString();
    }

    public static String truncate(String s, int max) {
        if (s == null) return null;
        return s.length() <= max ? s : s.substring(0, max) + "...(truncated)";
    }
}
