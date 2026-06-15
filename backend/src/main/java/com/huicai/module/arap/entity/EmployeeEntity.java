package com.huicai.module.arap.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 员工档案 - P11-1
 */
@Data
@TableName("t_employee")
public class EmployeeEntity {

    @TableId(type = IdType.AUTO)
    private Long id;

    /** 工号 */
    private String code;

    /** 姓名 */
    private String name;

    /** 部门ID（关联 system/dept） */
    private Long deptId;

    private String phone;
    private String email;

    /** 工资卡银行 */
    private String bankName;
    /** 工资卡号 */
    private String bankAccount;

    /** 身份证号 */
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
