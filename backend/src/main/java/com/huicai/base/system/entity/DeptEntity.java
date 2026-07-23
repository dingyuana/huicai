package com.huicai.base.system.entity;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import com.huicai.common.entity.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.util.List;

@Data
@EqualsAndHashCode(callSuper = true)
@TableName("t_dept")
public class DeptEntity extends BaseEntity {
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

    @TableField(exist = false)
    private List<DeptEntity> children;
}
