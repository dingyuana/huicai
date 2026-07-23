package com.huicai.base.system.entity;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import com.huicai.common.entity.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.util.List;

@Data
@EqualsAndHashCode(callSuper = true)
@TableName("t_menu")
public class MenuEntity extends BaseEntity {
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
    @TableField(exist = false)
    private Boolean isVisible;
    @TableField(exist = false)
    private Boolean keepAlive;
    @TableField(exist = false)
    private Boolean alwaysShow;

    @TableField(exist = false)
    private List<MenuEntity> children;
}
