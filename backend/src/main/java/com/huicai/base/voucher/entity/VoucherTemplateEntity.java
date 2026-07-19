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

    /** 模板名称, 如 "银行手续费" */
    private String name;

    /** 模板描述 */
    private String description;

    /** 绑定的分类: bank_fee / interest_income / tax_payment / ... */
    private String classification;

    /** 来源: BANK_STMT / BUSINESS_DOC / INVOICE / PERIOD_CLOSE */
    private String source;

    /** 业务类型: RECEIPT / PAYMENT / EXPENSE / INVOICE_OUT / ... */
    private String businessType;

    /** 方向: in(收/入) / out(付/出) / 空(双向) */
    private String direction;

    /** 匹配优先级（越小越优先，默认 0） */
    private Integer matchPriority;

    /** 凭证前缀, 如 JZ / CD / FPS */
    private String numberPrefix;

    /** 是否激活 (每个分类最多 1 个激活模板) */
    private Boolean isActive;

    /** 制单人 */
    private Long createdBy;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;

    /** 更新人 */
    private Long updatedBy;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updatedAt;

    @TableLogic
    private Integer deleted;
}
