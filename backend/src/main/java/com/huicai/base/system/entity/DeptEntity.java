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