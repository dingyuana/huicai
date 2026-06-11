package com.huicai.module.system.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 系统参数实体
 */
@Data
@TableName("t_sys_config")
public class SysConfigEntity {

    @TableId(type = IdType.ASSIGN_ID)
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

    /** 创建人 */
    private Long createdBy;

    /** 创建时间 */
    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;

    /** 更新人 */
    private Long updatedBy;

    /** 更新时间 */
    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updatedAt;

    /** 逻辑删除(0-未删,1-已删) */
    @TableLogic
    private Integer deleted;
}
