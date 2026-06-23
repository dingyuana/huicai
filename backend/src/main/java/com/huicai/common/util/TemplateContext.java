package com.huicai.common.util;

import lombok.Data;
import lombok.experimental.Accessors;

import java.math.BigDecimal;
import java.util.HashMap;
import java.util.Map;

/**
 * 凭证模板匹配与渲染上下文.
 * <p>
 * 承载匹配维度、金额数据、业务变量和辅助核算 ID，
 * 由调用方在制证前构建，传递给 {@link TemplateEngine} 和 TemplateMatcher。
 *
 * @see TemplateEngine
 */
@Data
@Accessors(chain = true)
public class TemplateContext {

    // ====== 匹配维度 ======

    /** 来源: BANK_STMT / BUSINESS_DOC / INVOICE / PERIOD_CLOSE */
    private String source;

    /** 业务类型: RECEIPT / PAYMENT / EXPENSE / INVOICE_OUT / ... */
    private String businessType;

    /** 方向: in(收/入) / out(付/出) */
    private String direction;

    /** 分类（兼容现有银行流水 classification） */
    private String classification;

    // ====== 金额数据 ======

    private BigDecimal amount;
    private BigDecimal taxAmount;
    private BigDecimal totalAmount;

    // ====== 业务变量 ======

    /** 会计期间 YYYYMM */
    private String period;

    /** 摘要文本 */
    private String summary;

    /** 对方户名（银行流水） */
    private String counterpartyName;

    private String customerName;
    private String vendorName;
    private String employeeName;

    // ====== 辅助核算 ID ======

    private Long customerId;
    private Long vendorId;
    private Long deptId;
    private Long employeeId;
    private Long projectId;

    // ====== 扩展变量 ======

    private Map<String, Object> variables = new HashMap<>();
}