package com.huicai.module.system.entity;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;

@Data
@TableName("t_menu")
public class MenuEntity {
    @TableId(type = IdType.AUTO)
    private Long id;
    private String name;
    private String permissionCode;
    private String type;
    private Long parentId;
    private String path;
    private String component;
    private String icon;
    private Integer sortOrder;
    private Boolean isActive;
    private Boolean isVisible;
    private Boolean keepAlive;
    private Boolean alwaysShow;
    private Long createdBy;
    private Long updatedBy;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    private Integer deleted;

    @TableField(exist = false)
    private List<MenuEntity> children;
}
