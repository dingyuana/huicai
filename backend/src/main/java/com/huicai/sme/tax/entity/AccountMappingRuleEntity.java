package com.huicai.sme.tax.entity;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import com.huicai.module.system.handler.JsonbTypeHandler;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 发票品名→会计科目映射规则（以票定账用）
 */
@Data
@TableName("t_account_mapping_rule")
public class AccountMappingRuleEntity {

    private Long id;

    /** 商品/费用名称关键字（支持 LIKE 匹配） */
    @TableField("item_keyword")
    private String itemKeyword;

    /** 目标会计科目编码 */
    @TableField("account_code")
    private String accountCode;

    /** 科目名称（冗余） */
    @TableField("account_name")
    private String accountName;

    /** 适用方向: INPUT(进项)/OUTPUT(销项)/BOTH */
    @TableField("direction")
    private String direction;

    /** 辅助核算维度 */
    @TableField(value = "aux_dimension", typeHandler = JsonbTypeHandler.class)
    private String auxDimension;

    /** 匹配优先级 */
    @TableField("priority")
    private Integer priority;

    /** 是否启用 */
    @TableField("is_active")
    private Boolean isActive;

    @TableField("created_at")
    private LocalDateTime createdAt;
}