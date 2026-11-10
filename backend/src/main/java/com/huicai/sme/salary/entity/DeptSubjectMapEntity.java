package com.huicai.sme.salary.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.huicai.common.entity.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * SPEC-P155 R2：部门→费用科目映射 —— SALC 计提分录按员工 dept_id 查本表
 * 得费用科目（销售部→6601 销售费用 / 管理部门→6602 管理费用）；
 * 无映射或员工无部门时回落 6602。同科目金额合并一行分录。
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("t_dept_subject_map")
public class DeptSubjectMapEntity extends BaseEntity {

    private Long deptId;
    /** 费用科目（t_subject.id） */
    private Long subjectId;
    private String remark;
}
