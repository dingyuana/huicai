package com.huicai.module.system.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 科目表实体
 */
@Data
@TableName("t_subject")
public class Subject {

    @TableId(type = IdType.ASSIGN_ID)
    private Long id;

    /** 科目编码 */
    private String code;

    /** 科目名称 */
    private String name;

    /** 父科目ID */
    private Long parentId;

    /** 科目层级(1-一级,2-二级...) */
    private Integer level;

    /** 借贷方向: debit-借方, credit-贷方 */
    private String direction;

    /** 是否末级科目 */
    private Boolean isLeaf;

    /** 辅助核算类型: customer/vendor/department/project/employee */
    private String auxCalcType;

    /** 是否启用 */
    private Boolean isActive;

    /** 备注 */
    private String remark;

    @TableField(fill = FieldFill.INSERT)
    private Long createdBy;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private Long updatedBy;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updatedAt;

    /** 逻辑删除(0-未删,1-已删) */
    @TableLogic
    private Integer deleted;
}