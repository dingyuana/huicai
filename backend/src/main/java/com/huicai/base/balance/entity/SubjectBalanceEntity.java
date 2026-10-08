package com.huicai.base.balance.entity;

import com.baomidou.mybatisplus.annotation.*;
import com.huicai.common.entity.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 科目余额表实体
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("t_subject_balance")
public class SubjectBalanceEntity extends BaseEntity {

    /** 科目ID */
    private Long subjectId;

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
    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;

    /** 更新时间 */
    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updatedAt;

    /* P110 Phase 3：删除本类对 deleted 字段的 exist=false shadow。
     *  DB 中 t_subject_balance.deleted 真实存在（NOT NULL DEFAULT 0），且
     *  BaseEntity 已声明 @TableLogic —— 旧的 exist=false 重声明会遮蔽它，
     *  导致本表不经过逻辑删除过滤。删除 shadow 后 MP 走标准 @TableLogic 覆盖。 */
}
