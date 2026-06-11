package com.huicai.module.finance.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("t_voucher_template")
public class VoucherTemplateEntity {

    @TableId(type = IdType.ASSIGN_ID)
    private Long id;

    private String templateCode;

    private String templateName;

    private String docType;

    private String summary;

    /** 分录模板 JSON */
    private String entries;

    private Boolean isActive;

    private String remark;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;
    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updatedAt;

    @TableLogic
    private Integer deleted;
}
