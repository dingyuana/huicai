package com.huicai.base.masterdata.entity;

import com.baomidou.mybatisplus.annotation.*;
import com.huicai.common.entity.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDateTime;

/**
 * 员工档案 - P11-1
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("t_employee")
public class EmployeeEntity extends BaseEntity {

    /** 工号 */
    private String code;

    /** 姓名 */
    private String name;

    /** 部门ID（关联 system/dept） */
    private Long deptId;

    private String phone;
    private String email;

    /** 工资卡银行 — DB 无此列 */
    @TableField(exist = false)
    private String bankName;
    /** 工资卡号 — DB 无此列 */
    @TableField(exist = false)
    private String bankAccount;

    /** 身份证号 — DB 无此列 */
    @TableField(exist = false)
    private String idCard;

    private Boolean isActive;
    private String remark;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;
    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updatedAt;

    @TableLogic
    private Integer deleted;
}
