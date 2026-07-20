package com.huicai.base.business.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 分类规则实体
 * 对应 t_classification_rule 表（V18）
 */
@Data
@TableName("t_classification_rule")
public class ClassificationRuleEntity {

    /** 主键 */
    @TableId(type = IdType.AUTO)
    private Long id;

    /** 租户 ID */
    private Long tenantId;

    /** 规则名称, 如"银行手续费" */
    private String name;

    /** 匹配类型: keyword/keyword_regex/counterparty_match */
    private String ruleType;

    /** 匹配模式, keyword_regex 用 | 分隔 */
    private String pattern;

    /** 匹配字段: description/counterparty */
    private String matchField;

    /** 方向过滤: in/out/不限 */
    private String direction;

    /** 分类结果: bank_fee/interest_income/... */
    private String classification;

    /** 优先级, 数字越小越优先 */
    private Integer priority;

    /** 是否启用 */
    private Boolean isActive;

    /** A/B/C路由类型: A-直接制证, B-生单后制证, C-待人工 */
    private String routeType;

    /** 是否系统内置兜底规则 (前端只读) */
    private Boolean isSystem;

    /** 借方科目 ID (自动凭证) */
    private Long debitSubjectId;

    /** 贷方科目 ID (自动凭证) */
    private Long creditSubjectId;

    /** 创建时间 */
    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;

    /** 更新时间 */
    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updatedAt;

    /** 创建人 */
    private Long createdBy;

    /** 更新人 */
    private Long updatedBy;

    /** 逻辑删除(0-未删,1-已删) */
    @TableLogic
    private Integer deleted;
}
