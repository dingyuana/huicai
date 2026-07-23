package com.huicai.base.system.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.huicai.common.entity.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 系统参数实体
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("t_sys_config")
public class SysConfigEntity extends BaseEntity {

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
}
