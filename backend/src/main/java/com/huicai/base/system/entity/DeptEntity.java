package com.huicai.base.system.entity;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
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
    @TableField(exist = false)
    private String status;
    @TableField(exist = false)
    private String leader;
    @TableField(exist = false)
    private String phone;
    @TableField(exist = false)
    private String email;
    private Long createdBy;
    private Long updatedBy;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    private Integer deleted;

    @TableField(exist = false)
    private List<DeptEntity> children;
}
