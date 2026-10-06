package com.huicai.base.masterdata.dto;

import com.huicai.base.masterdata.entity.EmployeeEntity;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * 员工新增/修改入参（REQ-2026-129 / P102 DTO 隔离 批次④）。
 *
 * <p><b>顺带清掉 3 个幽灵字段入参</b>：{@code bankName}/{@code bankAccount}/
 * {@code idCard} 在 {@code EmployeeEntity} 上标着 {@code exist = false}，
 * <b>完全不参与 SQL</b>（{@code t_employee} 里也没有对应列）⇒
 * {@code EmployeeServiceImpl#update} 虽然逐个读它们，但读到的<b>恒为 null</b>，
 * 客户端改了「一直生效」纯属错觉。本 DTO 显式移除。
 *
 * <p>{@code code}/{@code name} 映射到 {@code emp_code}/{@code emp_name}，
 * 均 NOT NULL 且无默认值 ⇒ 必填（AGENTS §4.2 第 13 条）。
 */
@Data
public class EmployeeSaveDTO {

    @NotBlank(message = "工号不能为空")
    @Size(max = 64, message = "工号长度不能超过 64")
    private String code;

    @NotBlank(message = "姓名不能为空")
    @Size(max = 64, message = "姓名长度不能超过 64")
    private String name;

    private Long deptId;

    /**
     * 职位 —— 2026-10-06 补入（P102 出参面批次 3）。
     *
     * <p><b>为什么必须补</b>：{@code t_employee.position} 是真实列（V1 baseline），
     * 前端「职位」输入框一直在提交它，但本 DTO 原先<b>没有</b>该字段 ⇒
     * Jackson 绑定时静默丢弃 ⇒ <b>用户填的职位永远存不进去</b>，且不报任何错。
     * 对应 AGENTS §4.3 的「静默忽略」反模式。
     *
     * <p>上限按 DB 列宽 {@code VARCHAR(100)} 取 100（AGENTS §4.2 第 13 条：
     * 必填/长度约束以 DB 为准，不凭感觉）。
     */
    @Size(max = 100, message = "职位长度不能超过 100")
    private String position;

    @Size(max = 32, message = "电话长度不能超过 32")
    private String phone;

    @Email(message = "邮箱格式不正确")
    private String email;

    private Boolean isActive;

    @Size(max = 500, message = "备注过长")
    private String remark;

    public EmployeeEntity toEntity() {
        EmployeeEntity e = new EmployeeEntity();
        e.setCode(code);
        e.setName(name);
        e.setDeptId(deptId);
        e.setPosition(position);
        e.setPhone(phone);
        e.setEmail(email);
        e.setIsActive(isActive);
        e.setRemark(remark);
        return e;
    }
}
