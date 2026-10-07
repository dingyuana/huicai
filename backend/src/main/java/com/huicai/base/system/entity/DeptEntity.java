package com.huicai.base.system.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;

@Data
@TableName("t_dept")
public class DeptEntity {
    @TableId(type = IdType.AUTO)
    private Long id;

    /**
     * 租户/企业隔离列（P106 批次 1b''，V171 补）。
     *
     * <p><b>为什么必须显式声明</b>：{@code DeptEntity} <b>不继承 {@code BaseEntity}</b>，
     * 若只在 DB 侧加列而 Entity 不声明，MyBatis-Plus 不会把它纳入 INSERT
     * ⇒ 新建部门落 {@code enterprise_id = DEFAULT 1}
     * ⇒ 非企业 1 的用户建部门会静默落进企业 1（跨租户写入），
     * 且改唯一约束后两个企业建同名部门仍会撞键（实测 DuplicateKeyException）。
     *
     * <p>{@code fill = INSERT} 是必需的：{@code MyMetaObjectHandler.insertFill} 依据字段名
     * {@code enterpriseId} 无条件覆盖为当前上下文企业（P102 加固）；
     * 本类已有 {@code createdAt} 等 fill 字段，故 insertFill 回调本就会触发。
     */
    @TableField(fill = FieldFill.INSERT)
    private Long enterpriseId;

    @TableField("dept_name")
    private String name;

    /**
     * 部门编码（t_dept.dept_code，NOT NULL 无默认值）.
     *
     * <p>⚠️ 历史缺口（REQ-2026-121 修复）：本字段此前<b>完全不存在</b>，
     * 而 DB 列是 NOT NULL，导致 {@code DeptMapper.insert()} 必然报
     * {@code null value in column "dept_code"} —— 生产环境新建部门直接失败。
     */
    @TableField("dept_code")
    private String deptCode;

    private Long parentId;
    @TableField("sort_order")
    private Integer sortOrder;

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
    private String status;
    @TableField(exist = false)
    private String leader;
    @TableField(exist = false)
    private String phone;
    @TableField(exist = false)
    private String email;

    @TableField(exist = false)
    private List<DeptEntity> children;
}