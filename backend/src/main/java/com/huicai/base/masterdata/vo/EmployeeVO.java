package com.huicai.base.masterdata.vo;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.huicai.base.masterdata.entity.EmployeeEntity;
import lombok.Data;

import java.util.List;

/**
 * 员工出参 VO（P102 出参面 DTO 化 **批次 3/9**）
 *
 * <p><b>⚠️ 本 VO 与前端 {@code interface Employee} 的对齐，是一次「修正接口」而非「迁就接口」</b>：
 * <ul>
 *   <li>前端接口原先声明的是 {@code department?: string}，而
 *       <b>全仓没有任何代码读它</b>（死字段）；页面真实用的是 {@code deptName}；</li>
 *   <li>而 {@code deptName} <b>从来就不是后端返回过的字段</b> ——
 *       {@code EmployeeEntity} 只有 {@code deptId: Long}。所以页面的「部门」列与表单框
 *       <b>在今天就已经是空的</b>（输入框 placeholder 自己写着「仅展示，未关联部门档案」）；</li>
 *   <li>⇒ 本次把 TS 接口里的死字段 {@code department} 换成后端<b>真实存在</b>的
 *       {@code deptId?: number}，让接口反映真实用法。页面那套占位 UI 的去留
 *       属产品决策（接真部门档案 or 删列），<b>不在本 VO 范围内</b>。</li>
 * </ul>
 *
 * <p><b>刻意不外露</b>：{@code createdAt}/{@code updatedAt}（审计列）、
 * {@code deleted}（逻辑删除位）、{@code enterpriseId}（租户内部维度）；
 * 以及 {@code bankName}/{@code bankAccount}/{@code idCard} ——
 * 它们在 Entity 上标着 {@code exist=false} 且 {@code t_employee} 无对应列，
 * 属幽灵字段，读到的恒为 null（{@code EmployeeSaveDTO} 早已移除对应入参）。
 *
 * @see AGENTS.md §0「出参面 Entity 直出」待办
 */
@Data
public class EmployeeVO {

    private Long id;

    /** 工号（DB 列 {@code emp_code}，NOT NULL） */
    private String code;

    /** 姓名（DB 列 {@code emp_name}，NOT NULL） */
    private String name;

    /** 部门 ID（DB 列 {@code dept_id}，可空；<b>部门名称不由本 VO 提供</b>，见类注释） */
    private Long deptId;

    /** 职位（DB 列 {@code position}；2026-10-06 才补上映射，见 {@code EmployeePositionRealDBTest}） */
    private String position;

    private String phone;
    private String email;
    private Boolean isActive;
    private String remark;

    public static EmployeeVO from(EmployeeEntity e) {
        if (e == null) {
            return null;
        }
        EmployeeVO vo = new EmployeeVO();
        vo.setId(e.getId());
        vo.setCode(e.getCode());
        vo.setName(e.getName());
        vo.setDeptId(e.getDeptId());
        vo.setPosition(e.getPosition());
        vo.setPhone(e.getPhone());
        vo.setEmail(e.getEmail());
        vo.setIsActive(e.getIsActive());
        vo.setRemark(e.getRemark());
        return vo;
    }

    public static List<EmployeeVO> from(List<EmployeeEntity> list) {
        if (list == null) {
            return null;
        }
        return list.stream().map(EmployeeVO::from).collect(java.util.stream.Collectors.toList());
    }

    /** Entity 分页 → VO 分页；只换 records 元素类型，分页元信息原样透传 */
    public static IPage<EmployeeVO> from(IPage<EmployeeEntity> page) {
        if (page == null) {
            return null;
        }
        com.baomidou.mybatisplus.extension.plugins.pagination.Page<EmployeeVO> out =
                new com.baomidou.mybatisplus.extension.plugins.pagination.Page<>(
                        page.getCurrent(), page.getSize(), page.getTotal());
        out.setRecords(from(page.getRecords()));
        return out;
    }
}