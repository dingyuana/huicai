package com.huicai.module.report.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("t_report_template")
public class ReportTemplateEntity {

    @TableId(type = IdType.ASSIGN_ID)
    private Long id;

    private String templateCode;
    private String templateName;
    private String reportType;
    private String config;
    private Boolean isSystem;
    private Long createdBy;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;
    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updatedAt;

    @TableLogic
    private Integer deleted;
}
