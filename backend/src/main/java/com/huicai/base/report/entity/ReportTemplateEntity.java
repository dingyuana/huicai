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
    /** 是否系统模板 — DB 无此列（SPEC-P153 复核：真库列集无 is_system，且全仓无写方） */
    @TableField(exist = false)
    private Boolean isSystem;
    /* SPEC-P153：created_by 真实列存在（原注释「DB 无此列」不实），
     * 删除 exist=false 覆盖，由 BaseEntity 映射。当前无写方，转正为
     * 「后续 Service 补 setCreatedBy 时即落库」铺路，行为无回归。 */

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;
    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updatedAt;

    @TableLogic
    private Integer deleted;
}
