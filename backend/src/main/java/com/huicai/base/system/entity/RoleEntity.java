package com.huicai.base.system.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;

@Data
@TableName("t_role")
public class RoleEntity {
    @TableId(type = IdType.AUTO)
    private Long id;

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

    private Long createdBy;
    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;
    private Long updatedBy;
    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updatedAt;
    @TableLogic
    private Integer deleted;
    @Version
    private Integer version;

    @TableField(exist = false)
    private List<Long> menuIds;

    @TableField(exist = false)
    private List<String> permissionCodes;
}
