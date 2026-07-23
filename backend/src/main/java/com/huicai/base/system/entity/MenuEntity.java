package com.huicai.base.system.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;

@Data
@TableName("t_menu")
public class MenuEntity {
    @TableId(type = IdType.AUTO)
    private Long id;

    @TableField("menu_name")
    private String name;
    @TableField("permission")
    private String permissionCode;
    @TableField("menu_type")
    private String type;
    private Long parentId;
    private String path;
    private String component;
    private String icon;
    private Integer sortOrder;
    private Boolean isActive;

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
    private Boolean isVisible;
    @TableField(exist = false)
    private Boolean keepAlive;
    @TableField(exist = false)
    private Boolean alwaysShow;

    @TableField(exist = false)
    private List<MenuEntity> children;
}
