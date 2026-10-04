package com.huicai.base.system.dto;

import com.huicai.base.system.entity.DeptEntity;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * 部门新增/修改入参（REQ-2026-129 / P102 DTO 隔离 批次③）。
 *
 * <p>剔除 {@code status}（启用/停用应走专用流程）、{@code version}（乐观锁）、
 * {@code children}（树形结构由查询侧组装，**不接受客户端提交子树**
 * —— 否则可凭一次 create 造出整棵树并绕过父部门校验）。
 *
 * <p>{@code deptName}/{@code deptCode} 在 DB 侧是 NOT NULL 且无默认值
 * （`t_dept.dept_code` 历史上曾缺失列，见 REQ-2026-121），故设为必填。
 */
@Data
public class DeptSaveDTO {

    @NotBlank(message = "部门名称不能为空")
    @Size(max = 64, message = "部门名称长度不能超过 64")
    private String name;

    @NotBlank(message = "部门编码不能为空")
    @Size(max = 64, message = "部门编码长度不能超过 64")
    private String deptCode;

    private Long parentId;

    private Integer sortOrder;

    @Size(max = 64, message = "负责人长度不能超过 64")
    private String leader;

    @Size(max = 32, message = "电话长度不能超过 32")
    private String phone;

    @Email(message = "邮箱格式不正确")
    @Size(max = 128, message = "邮箱长度不能超过 128")
    private String email;

    public DeptEntity toEntity() {
        DeptEntity e = new DeptEntity();
        e.setName(name);
        e.setDeptCode(deptCode);
        e.setParentId(parentId);
        e.setSortOrder(sortOrder);
        e.setLeader(leader);
        e.setPhone(phone);
        e.setEmail(email);
        return e;
    }
}
