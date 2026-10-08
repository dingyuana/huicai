package com.huicai.base.masterdata.entity;

import com.baomidou.mybatisplus.annotation.*;
import com.huicai.common.entity.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDateTime;

/**
 * 员工档案 - P11-1
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("t_employee")
public class EmployeeEntity extends BaseEntity {

    /** 工号 */
    @TableField(value = "emp_code")
    private String code;

    /** 姓名 */
    @TableField(value = "emp_name")
    private String name;

    /** 部门ID（关联 system/dept） */
    private Long deptId;

    /**
     * 职位 —— <b>2026-10-06 补映射（P102 出参面批次 3 顺带修的反向缺口）</b>。
     *
     * <p>{@code t_employee.position VARCHAR(100)} <b>自 V1 baseline 建表起就真实存在</b>，
     * 此后从未被 ALTER 或删除；但本 Entity <b>从未声明过该字段</b>，{@code EmployeeSaveDTO}
     * 也没有 ⇒ 该列<b>永不写入、读回恒 null</b>（AGENTS §4.2 第 16 条「反向缺口」：
     * 真实列存在而 Entity 未声明，比幽灵字段更隐蔽，因为 {@code \d} 里一眼可见）。
     *
     * <p><b>可见后果（实测）</b>：前端 {@code EmployeeList.vue} 有「职位」输入框
     * （{@code form.position}），提交时被 DTO <b>静默丢弃</b>；列表列
     * {@code prop="position"} 于是恒为空。症状是「用户填了职位、转头就丢」，且不报任何错。
     *
     * <p>⚠️ 同表另有一处<b>尚未修</b>的同类缺口：{@code subject_id}（V1 baseline 有列 +
     * FK→{@code t_subject}）同样未被本 Entity 声明。本次不擅自扩大范围，已单独登记。
     */
    private String position;

    private String phone;
    private String email;

    /** 工资卡银行 — DB 无此列 */
    @TableField(exist = false)
    private String bankName;
    /** 工资卡号 — DB 无此列 */
    @TableField(exist = false)
    private String bankAccount;

    /** 身份证号 — DB 无此列 */
    @TableField(exist = false)
    private String idCard;

    private Boolean isActive;
    private String remark;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;
    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updatedAt;

    @TableLogic
    private Integer deleted;
}
