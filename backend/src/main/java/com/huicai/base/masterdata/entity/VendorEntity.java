package com.huicai.base.masterdata.entity;

import com.baomidou.mybatisplus.annotation.*;
import com.huicai.common.entity.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@EqualsAndHashCode(callSuper = true)
@TableName("t_vendor")
public class VendorEntity extends BaseEntity {

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
