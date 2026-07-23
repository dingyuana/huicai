package com.huicai.base.report.entity;

import com.baomidou.mybatisplus.annotation.*;
import com.huicai.common.entity.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDateTime;

@Data
@EqualsAndHashCode(callSuper = true)
@TableName("t_report_template")
public class ReportTemplateEntity extends BaseEntity {

    private String templateCode;
    private String templateName;
    private String reportType;
    private String config;
    private Boolean isSystem;
    /** 创建人 — DB 无此列 */
    @TableField(exist = false)
    private Long createdBy;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;
    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updatedAt;

    @TableLogic
    private Integer deleted;
}
