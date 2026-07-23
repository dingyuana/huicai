package com.huicai.base.system.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 系统参数实体
 */
@Data
@TableName("t_sys_config")
public class SysConfigEntity {
    @TableId(type = IdType.AUTO)
    private Long id;

    /** 参数键 */
    private String configKey;
    /** 参数值 */
    private String configValue;
    /** 参数类型: system-系统, business-业务, accounting-财务 */
    private String configType;
    /** 参数说明 */
    private String description;
    /** 是否启用 */
    private Boolean isActive;

    private Long createdBy;
    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;
    private Long updatedBy;
    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updatedAt;
    @TableLogic
    private Integer deleted;
    @TableField(exist = false)
    private Integer version;
}
