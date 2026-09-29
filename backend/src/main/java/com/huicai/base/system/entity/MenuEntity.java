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

    /**
     * 菜单编码（t_menu.menu_code，NOT NULL 无默认值）.
     *
     * <p>⚠️ 历史缺口（REQ-2026-121 修复）：本字段此前<b>完全不存在</b>，
     * 而 DB 列是 NOT NULL，导致 {@code MenuMapper.insert()} 必然报
     * {@code null value in column "menu_code"} —— 生产环境新建菜单直接失败。
     * 本项是 AGENTS §4.2 第 8 条的<b>镜像方向</b>：不是 Entity 指向不存在的列，
     * 而是 DB 的必填列在 Entity 里缺失。
     */
    @TableField("menu_code")
    private String menuCode;

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
    @TableField(exist = false)
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
