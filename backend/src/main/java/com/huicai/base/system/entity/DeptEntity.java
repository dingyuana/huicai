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
    @Version
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
