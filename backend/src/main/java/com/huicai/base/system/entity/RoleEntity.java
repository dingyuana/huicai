package com.huicai.base.system.entity;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
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
    private Long updatedBy;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    private Integer deleted;

    @TableField(exist = false)
    private List<Long> menuIds;

    @TableField(exist = false)
    private List<String> permissionCodes;
}
