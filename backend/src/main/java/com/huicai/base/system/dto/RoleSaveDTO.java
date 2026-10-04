package com.huicai.base.system.dto;

import com.huicai.base.system.entity.RoleEntity;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;


/**
 * 角色新增/修改入参（REQ-2026-129 / P102 DTO 隔离 批次③）。
 *
 * <p>剔除 {@code status}（启用/停用应走 {@code PUT /{id}/status}）、
 * {@code version}（乐观锁，由 Service 维护）、{@code permissionCodes} 与
 * {@code menuIds}（菜单授权走 {@code PUT /{id}/menus} 专端点）。
 *
 * <p><b>不接收 menuIds 是有意为之</b>：实测 {@code RoleServiceImpl#create/update}
 * <b>从不</b>读 {@code RoleEntity.menuIds}（全仓仅 {@code assignMenus} 用到），
 * 故原 Entity 入参里的 menuIds 是<b>静默丢弃</b>的字段 —— 既然从未生效，
 * 就在 DTO 里显式移除，避免给调用方「建角色时可一并勾菜单」的错觉。
 *
 * <p>注：{@code t_role} 上有 {@code chk_role_type} 约束，但对应列
 * {@code role_type} <b>不在 RoleEntity 里</b>，本 DTO 自然也无法提交该值。
 */
@Data
public class RoleSaveDTO {

    /** 角色编码：新增必填 */
    @Size(max = 64, message = "角色编码长度不能超过 64")
    private String code;

    @NotBlank(message = "角色名称不能为空")
    @Size(max = 64, message = "角色名称长度不能超过 64")
    private String name;

    @Size(max = 255, message = "描述过长")
    private String description;

    private Integer sortOrder;

    /** 数据范围：见权限设计（ALL / DEPT / SELF 等），留空由 Service/DB 默认 */
    @Size(max = 32, message = "数据范围长度不能超过 32")
    private String dataScope;

    /** 保留：便于「建角色时一并勾菜单」的常见用法；服务端只取 menuIds 的并集写入关联表 */

    public RoleEntity toEntity() {
        RoleEntity e = new RoleEntity();
        e.setCode(code);
        e.setName(name);
        e.setDescription(description);
        e.setSortOrder(sortOrder);
        e.setDataScope(dataScope);
        return e;
    }
}
