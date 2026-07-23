package com.huicai.base.voucher.entity;

import com.baomidou.mybatisplus.annotation.*;
import com.huicai.common.entity.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDateTime;

/**
 * 凭证模板分录行 — 对应凭证的一条借/贷分录.
 * 通过 template 表达式 (如 {{amount}}, {{summary}}) 实现动态填充.
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("t_voucher_template_line")
public class VoucherTemplateLineEntity extends BaseEntity {

    /** 所属模板 ID */
    private Long templateId;

    /** 科目 ID (关联 t_subject) */
    private Long subjectId;

    /** 借方金额模板表达式: "{{amount}}", "{{taxAmount}}", 或具体数字 */
    private String drAmountTemplate;

    /** 贷方金额模板表达式 */
    private String crAmountTemplate;

    /** 摘要模板: "银行手续费: {{summary}}" */
    private String summaryTemplate;

    /** 方向: debit / credit */
    private String direction;

    /** 辅助核算类型: CUSTOMER / VENDOR / DEPT / EMPLOYEE / PROJECT */
    private String assistType;

    /** 是否必填辅助核算（强校验）*/
    private Boolean assistRequired;

    /** 排序号 */
    private Integer lineOrder;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;

    @TableLogic
    private Integer deleted;
}
