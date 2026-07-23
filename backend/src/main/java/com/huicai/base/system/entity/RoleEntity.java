package com.huicai.base.system.entity;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import com.huicai.common.entity.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.util.List;

@Data
@EqualsAndHashCode(callSuper = true)
@TableName("t_role")
public class RoleEntity extends BaseEntity {
    @TableField("role_code")
    private String code;
    @TableField("role_name")
    private String name;
    private String description;
    private String status;
    @TableField("sort_order")
    private Integer sortOrder;
    @TableField("data_scope")
    private String dataScope;

    @TableField(exist = false)
    private List<Long> menuIds;

    @TableField(exist = false)
    private List<String> permissionCodes;
}
