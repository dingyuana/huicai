package com.huicai.base.system.dto;

import com.huicai.base.system.entity.MenuEntity;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * 菜单新增/修改入参（REQ-2026-129 / P102 DTO 隔离 批次④）。
 *
 * <p><b>三个必须记住的坑（都在本 DTO 上体现）</b>：
 * <ol>
 *   <li>{@code menuName}/{@code menuCode} 是 {@code t_menu} 上 NOT NULL 且无默认值的列
 *       —— 历史上 {@code MenuEntity} <b>缺 menuCode 字段</b>导致任何 insert 必挂
 *       （REQ-2026-121），现两侧都补齐并设为必填；</li>
 *   <li>{@code type} 映射到列 {@code menu_type}，{@code chk_menu_type} 只允许
 *       <b>大写</b> {@code MENU / BUTTON / DIR}；历史上因代码写小写 {@code "menu"}
 *       导致「菜单恒过滤不掉、用户路由恒空」（AGENTS §4.3 第 9 条）⇒ 这里用
 *       {@code @Pattern} 把大小写错误挡在入口；</li>
 *   <li>{@code children}/{@code isVisible}/{@code keepAlive}/{@code alwaysShow}/
 *       {@code version} 都是 {@code exist = false} 的幽灵字段（树形结构与展示位
 *       由查询侧组装）⇒ 不接收。</li>
 * </ol>
 */
@Data
public class MenuSaveDTO {

    @NotBlank(message = "菜单名称不能为空")
    @Size(max = 64, message = "菜单名称长度不能超过 64")
    private String name;

    @NotBlank(message = "菜单编码不能为空")
    @Size(max = 64, message = "菜单编码长度不能超过 64")
    private String menuCode;

    @Size(max = 128, message = "权限码长度不能超过 128")
    private String permissionCode;

    /** 对应列 menu_type；chk_menu_type 允许集为大写 MENU / BUTTON / DIR */
    @Pattern(regexp = "^$|MENU|BUTTON|DIR", message = "菜单类型仅支持 MENU / BUTTON / DIR（大写）")
    private String type;

    private Long parentId;

    @Size(max = 255, message = "路由路径过长")
    private String path;

    @Size(max = 255, message = "组件路径过长")
    private String component;

    @Size(max = 64, message = "图标过长")
    private String icon;

    private Integer sortOrder;

    private Boolean isActive;

    public MenuEntity toEntity() {
        MenuEntity e = new MenuEntity();
        e.setName(name);
        e.setMenuCode(menuCode);
        e.setPermissionCode(permissionCode);
        e.setType(type);
        e.setParentId(parentId);
        e.setPath(path);
        e.setComponent(component);
        e.setIcon(icon);
        e.setSortOrder(sortOrder);
        e.setIsActive(isActive);
        return e;
    }
}
