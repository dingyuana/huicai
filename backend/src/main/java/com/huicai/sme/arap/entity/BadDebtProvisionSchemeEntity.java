package com.huicai.sme.arap.entity;

import com.baomidou.mybatisplus.annotation.*;
import com.huicai.common.entity.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDateTime;

/**
 * 坏账计提方案实体
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("t_bad_debt_provision_scheme")
public class BadDebtProvisionSchemeEntity extends BaseEntity {

    /** 方案名称 */
    private String name;

    /** 计提方法: AGING_RATIO / PERCENTAGE */
    private String method;

    /** 是否默认方案 */
    private Boolean isDefault;

    /** 是否启用 */
    private Boolean isActive;

    /** 备注 */
    private String remark;

    private Long createdBy;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updatedAt;

    @TableLogic
    private Integer deleted;
}