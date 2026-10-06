package com.huicai.sme.tax.entity;

import com.baomidou.mybatisplus.annotation.*;
import com.huicai.base.system.handler.JsonbTypeHandler;
import com.huicai.common.entity.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 发票品名→会计科目映射规则（以票定账用）。
 *
 * <p><b>P110 Phase 2（2026-10-06）对齐修复</b>：旧 Entity 声明了 6 个
 * {@code @TableField(exist = false)} 野列（itemKeyword/accountCode/accountName/direction/
 * auxDimension/priority），而真实 DB 只有 8 个业务列（rule_code/rule_name/source_type/
 * target_subject_id/match_pattern/ai_result/is_active/remark）——修复后以 DB 为唯一事实来源。
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("t_account_mapping_rule")
public class AccountMappingRuleEntity extends BaseEntity {

    /** 规则代码（每企业唯一） */
    @TableField("rule_code")
    private String ruleCode;

    /** 规则名称 */
    @TableField("rule_name")
    private String ruleName;

    /** 源单据类型（如 INVOICE_IN/INVOICE_OUT） */
    @TableField("source_type")
    private String sourceType;

    /** 目标会计科目 ID（FK→t_subject） */
    @TableField("target_subject_id")
    private Long targetSubjectId;

    /** 品名/摘要匹配关键字（支持 LIKE） */
    @TableField("match_pattern")
    private String matchPattern;

    /** AI 识别结果 (JSONB) */
    @TableField(value = "ai_result", typeHandler = JsonbTypeHandler.class)
    private String aiResult;

    /** 是否启用 */
    @TableField("is_active")
    private Boolean isActive;

    /** 备注 */
    private String remark;
}
