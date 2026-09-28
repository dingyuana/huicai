package com.huicai.base.balance.dto;

import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 科目余额视图对象 — 含科目编码/名称/方向，供前端列表展示
 */
@Data
public class SubjectBalanceVO {

    private Long id;

    /** 科目ID */
    private Long subjectId;

    /** 科目编码 */
    private String subjectCode;

    /** 科目名称 */
    private String subjectName;

    /** 借贷方向: debit-借方, credit-贷方 */
    private String direction;

    /** 父科目ID：P97/REQ-097 组树用；父科目不在结果集时前端把本行提升为根，不丢行 */
    private Long parentId;

    /** 科目层级(1-一级,2-二级…)：此前前端「层级」列绑定该字段但后端从未回填，恒为空 */
    private Integer level;

    /** 是否末级科目：前端据此判断能否展开 */
    private Boolean isLeaf;

    /** 辅助核算类型(customer/vendor/department/project/employee)：辅助核算明细列据此分组 */
    private String auxCalcType;

    /** 会计年度 */
    private Integer year;

    /** 会计期间(YYYYMM) */
    private String period;

    /** 期初余额 */
    private BigDecimal beginBalance;

    /** 本期借方发生额 */
    private BigDecimal debitTotal;

    /** 本期贷方发生额 */
    private BigDecimal creditTotal;

    /** 期末余额 */
    private BigDecimal endBalance;

    /** 创建时间 */
    private LocalDateTime createdAt;

    /** 更新时间 */
    private LocalDateTime updatedAt;
}
