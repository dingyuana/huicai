package com.huicai.sme.tax.entity;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import com.huicai.base.system.handler.JsonbTypeHandler;
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
    @TableField(value = "item_keyword", exist = false)
    private String itemKeyword;

    /** 目标会计科目编码 */
    @TableField(value = "account_code", exist = false)
    private String accountCode;

    /** 科目名称（冗余） */
    @TableField(value = "account_name", exist = false)
    private String accountName;

    /** 适用方向: INPUT(进项)/OUTPUT(销项)/BOTH */
    @TableField(value = "direction", exist = false)
    private String direction;

    /** 辅助核算维度 */
    @TableField(value = "aux_dimension", typeHandler = JsonbTypeHandler.class, exist = false)
    private String auxDimension;

    /** 匹配优先级 */
    @TableField(value = "priority", exist = false)
    private Integer priority;

    /** 是否启用 */
    @TableField("is_active")
    private Boolean isActive;

    @TableField("created_at")
    private LocalDateTime createdAt;
}