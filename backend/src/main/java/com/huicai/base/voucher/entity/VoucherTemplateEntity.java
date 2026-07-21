package com.huicai.base.voucher.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 凭证模板 — 配置驱动的科目映射 (替代 AutoGenerationService 中的硬编码).
 * 每个模板绑定一个 classification, 用于银行流水 A 类自动制证.
 */
@Data
@TableName("t_voucher_template")
public class VoucherTemplateEntity {

    @TableId(type = IdType.AUTO)
    private Long id;

    @TableField("template_name")
    private String name;

    @TableField(exist = false)
    private String description;

    @TableField(exist = false)
    private String classification;

    @TableField(exist = false)
    private String source;

    @TableField("doc_type")
    private String businessType;

    @TableField(exist = false)
    private String direction;

    @TableField(exist = false)
    private Integer matchPriority;

    @TableField(exist = false)
    private String numberPrefix;

    private Boolean isActive;

    @TableField(exist = false)
    private Long createdBy;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;

    @TableField(exist = false)
    private Long updatedBy;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updatedAt;

    @TableLogic
    private Integer deleted;
}
