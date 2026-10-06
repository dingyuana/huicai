package com.huicai.base.system.vo;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.huicai.base.system.entity.RoleEntity;
import lombok.Data;

import java.util.List;

/**
 * 角色出参 VO（P102 出参面 DTO 化 **批次 1/9 · 试点**）
 *
 * <p><b>为什么出参也要 VO（铁律 #13）</b>：{@code RoleController} 原先直接返回
 * {@link RoleEntity}，而 {@code RoleEntity} 带着一批**服务端内部字段**
 * （{@code version}、{@code menuIds}、{@code permissionCodes} 三个
 * {@code @TableField(exist = false)}，以及审计列 {@code createdBy/updatedBy/deleted}）。
 * 这些字段对前端无意义，且 {@code menuIds}/{@code permissionCodes} 属权限数据 ——
 * **由前端自行猜测权限意味着越权可能发生在浏览器侧**。
 *
 * <p><b>⚠️ 本 VO 的字段是「按需暴露」而非「照抄 Entity」</b>：只保留前端页面
 * 实际渲染/提交的字段。剔除 {@code version}（乐观锁内部值）、{@code deleted}（逻辑删除位）、
 * {@code createdBy/updatedBy}（审计内部值）。
 *
 * <p><b>⚠️ 兼容性</b>：本 VO 是 Entity 的<b>真子集</b> ⇒ JSON 只会<b>少字段</b>、
 * 不会改字段名或类型 ⇒ 对现有前端**向后兼容**（前提是前端不读被剔除的字段；
 * 试点批次会逐个核对前端实际读取的字段）。
 *
 * <p><b>覆盖率影响（AGENTS §4.5 第 30 条）</b>：{@code @Data} VO 会带来
 * 「方法数 × 21」与「分支数 × 约 45」的税。但 {@code DtoLombokBranchCoverageTest}
 * 按类路径扫描全部 DTO/VO，<b>新增 VO 无需再写测试</b>即自动覆盖其 getter / setter /
 * {@code equals} / {@code hashCode} ⇒ 该税已预付。
 *
 * @see docs/specs/P102-security-permission-baseline.md（出参面待办）
 * @see AGENTS.md §4.5 第 30 条
 */
@Data
public class RoleVO {

    private Long id;

    /** 角色编码（对应 {@code t_role.role_code}） */
    private String code;

    /** 角色名称 */
    private String name;

    /** 备注说明 */
    private String description;

    /** 状态：ACTIVE / INACTIVE（chk_role_status 允许集，写入前须查证） */
    private String status;

    /** 排序号 */
    private Integer sortOrder;

    /** 数据范围 */
    private String dataScope;

    // ⚠️ 刻意**不**暴露的字段（逐个有依据，不是随手删）：
    //   version           —— 乐观锁内部值，前端无从使用
    //   deleted           —— 逻辑删除位，属内部状态
    //   createdBy/updatedBy/createdAt/updatedAt —— 审计列，本页不渲染
    //   menuIds           —— @TableField(exist=false)，属权限数据，
    //                          由前端自行猜测权限意味着越权可能发生在浏览器侧
    //   permissionCodes   —— 同上
    // ⚠️ 本 VO 的字段集与前端 `RoleVO` 接口（frontend/src/api/modules/system.ts）**逐字段一致**：
    //    id / code / name / description / status / sortOrder / dataScope
    //    ⇒ 不多暴露一个字段，也不少一个（少一个会让前端 undefined）。

    public static RoleVO from(RoleEntity e) {
        if (e == null) {
            return null;
        }
        RoleVO vo = new RoleVO();
        vo.setId(e.getId());
        vo.setCode(e.getCode());
        vo.setName(e.getName());
        vo.setDescription(e.getDescription());
        vo.setStatus(e.getStatus());
        vo.setSortOrder(e.getSortOrder());
        vo.setDataScope(e.getDataScope());
        return vo;
    }

    public static List<RoleVO> from(List<RoleEntity> list) {
        if (list == null) {
            return null;
        }
        return list.stream().map(RoleVO::from).collect(java.util.stream.Collectors.toList());
    }

    /**
     * Entity 分页 → VO 分页。<b>只换 records 的元素类型</b>，
     * 分页元信息（total/size/ pages）原样透传，避免丢分页信息。
     */
    public static IPage<RoleVO> from(IPage<RoleEntity> page) {
        if (page == null) {
            return null;
        }
        com.baomidou.mybatisplus.extension.plugins.pagination.Page<RoleVO> out =
                new com.baomidou.mybatisplus.extension.plugins.pagination.Page<>(
                        page.getCurrent(), page.getSize(), page.getTotal());
        out.setRecords(from(page.getRecords()));
        return out;
    }
}