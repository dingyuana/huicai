package com.huicai.base.system.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 科目表实体
 */
@Data
@TableName("t_subject")
public class Subject {

    @TableId(type = IdType.AUTO)
    private Long id;

    /** 科目编码 */
    private String code;

    /** 科目名称 */
    private String name;

    /** 父科目ID */
    private Long parentId;

    /** 科目层级(1-一级,2-二级...) */
    private Integer level;

    /** 借贷方向: debit-借方, credit-贷方 */
    private String direction;

    /** 是否末级科目 */
    private Boolean isLeaf;

    /** 辅助核算类型: customer/vendor/department/project/employee */
    private String auxCalcType;

    /** 是否启用 */
    private Boolean isActive;

    /** 所属会计制度编码: CAS(企业会计准则)/SME(小企业会计准则)/NPO(民间非营利)/NULL(自定义) */
    private String accountingStandard;

    /**
     * 资产/负债流动分类（P92-B 资产负债表分类小计）。
     * CURRENT_ASSET / NON_CURRENT_ASSET / CURRENT_LIABILITY / NON_CURRENT_LIABILITY。
     * 权益(4x)、成本(5x)、收入(6x) 不适用流动分类，为 NULL。
     * 报表端对未分类的资产/负债科目走科目段兜底，不静默丢弃（见 ReportServiceImpl.balanceSheet）。
     */
    private String accountType;

    /** 备注 */
    private String remark;

    /** 企业ID（多租户隔离） */
    private Long enterpriseId;

    @TableField(fill = FieldFill.INSERT)
    private Long createdBy;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private Long updatedBy;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updatedAt;

    /** 逻辑删除(0-未删,1-已删) */
    @TableLogic
    private Integer deleted;
}