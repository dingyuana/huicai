package com.huicai.module.arap.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@TableName("t_vendor")
public class VendorEntity {

    @TableId(type = IdType.AUTO)
    private Long id;

    private String code;
    private String name;
    private String contactPerson;
    private String phone;
    private String email;
    private String address;
    private String taxNo;
    private String bankName;
    private String bankAccount;
    private BigDecimal creditLimit;
    private Integer creditDays;
    private Long subjectId;
    private Boolean isActive;
    private String remark;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;
    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updatedAt;

    @TableLogic
    private Integer deleted;
}
