package com.huicai.sme.cash.util;

import java.util.regex.Pattern;

/**
 * 对手方名正则识别 — 移植自 Go 版 {@code extractCounterpartyName}.
 * <p>
 * 用于 B 类业务单据 (business_receipt / business_payment) 自动从银行流水摘要
 * (counterAccount) 中提取交易对手方名称, 供后续查 t_customer / t_vendor 表使用.
 * <p>
 * 4 级优先级 (高→低):
 * <ol>
 *   <li>税务局 (国家税务总局/地方税务局)</li>
 *   <li>政府部门 (社保局/公积金中心/海关等)</li>
 *   <li>公司类 (有限公司/集团/股份公司等)</li>
 *   <li>短组织 (公司/厂/店/商行/银行/医院等)</li>
 * </ol>
 * 同时排除纯数字 bankCode (如 "10086") 以避免假对手方.
 *
 * <p>参考: Go 版 {@code huihua-finance/internal/service/bank_transaction_service.go:645-669}
 */
public final class CounterpartyExtractor {

    /** 税务局：国家税务总局开头 或 "X(2-20)税务局". */
    private static final Pattern TAX_BUREAU = Pattern.compile(
            "(?:国家税务总局[\\u4e00-\\u9fa5]{0,15}税务局|[\\u4e00-\\u9fa5]{2,20}税务局)");

    /** 政府部门：X(2-20)(社保局|公积金中心|社保中心|海关). */
    private static final Pattern GOV_DEPT = Pattern.compile(
            "[\\u4e00-\\u9fa5]{2,20}(?:社保局|公积金中心|社保中心|海关)");

    /** 公司类：X(2-30)(有限公司|股份有限公司|集团|有限责任公司|股份公司|总公司|分公司|子公司|集团公司). */
    private static final Pattern COMPANY = Pattern.compile(
            "[\\u4e00-\\u9fa5]{2,30}(?:有限公司|股份有限公司|集团|有限责任公司|股份公司|总公司|分公司|子公司|集团公司)");

    /**
     * 短组织：X(2-20)(公司|厂|店|商行|银行|事务所|医院|学校|中心).
     * <p>
     * 注: Go 版 prefix 是 {4,20}, 但 P3-H3 任务书的 10 个单测 (testExtract_短公司
     * "向万达公司付款"→"万达公司", testExtract_银行 "工商银行手续费"→"工商银行")
     * 都需要 2-3 个汉字前缀即可匹配, 故本端口取 {2,20} 以满足验收用例. 待后续
     * 与 Go 版对齐可改回 {4,20} 并相应调整测试输入.
     */
    private static final Pattern SHORT_ORG = Pattern.compile(
            "[\\u4e00-\\u9fa5]{2,20}(?:公司|厂|店|商行|银行|事务所|医院|学校|中心)");

    /** 纯数字 bankCode：4-20 位数字. */
    private static final Pattern BANK_CODE = Pattern.compile("^\\d{4,20}$");

    /**
     * 银行流水摘要常见的"方向/动作"前缀词, 位于对手方名前面 (如 "支付", "收到", "向").
     * 命中后从匹配结果开头剥除, 避免 "向国家税务总局..." 误把 "向" 包进对手方名.
     * 这是固定词表的归一化, 不属于模糊匹配.
     */
    private static final String[] LEADING_DIR_PREFIXES = {
            "支付", "收到", "向", "付", "收", "缴", "从", "给", "于", "转", "退", "代"
    };

    private CounterpartyExtractor() {
    }

    /**
     * 从文本中提取对手方名. 文本为空或纯数字 bankCode 时返回空串.
     *
     * @param description 银行流水摘要 / 对方账户备注
     * @return 提取的对手方名, 无匹配返回 ""
     */
    public static String extract(String description) {
        if (description == null || description.isBlank()) {
            return "";
        }
        String trimmed = description.trim();
        if (BANK_CODE.matcher(trimmed).matches()) {
            return "";
        }
        String m = firstMatch(TAX_BUREAU, description);
        if (!m.isEmpty()) {
            return stripDirPrefix(m);
        }
        m = firstMatch(GOV_DEPT, description);
        if (!m.isEmpty()) {
            return stripDirPrefix(m);
        }
        m = firstMatch(COMPANY, description);
        if (!m.isEmpty()) {
            return stripDirPrefix(m);
        }
        m = firstMatch(SHORT_ORG, description);
        if (m.isEmpty()) {
            return m;
        }
        return stripDirPrefix(m);
    }

    private static String firstMatch(Pattern p, String s) {
        var m = p.matcher(s);
        return m.find() ? m.group().trim() : "";
    }

    /**
     * 若结果以"方向/动作"前缀词开头则剥除 (取最长命中).
     * 例: "向国家税务总局山东税务局" → "国家税务总局山东税务局"
     *     "支付济南市社保局" → "济南市社保局"
     */
    private static String stripDirPrefix(String matched) {
        for (String prefix : LEADING_DIR_PREFIXES) {
            if (matched.startsWith(prefix) && matched.length() > prefix.length()) {
                return matched.substring(prefix.length());
            }
        }
        return matched;
    }
}

