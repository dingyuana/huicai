package com.huicai.base.system.entity;

import com.baomidou.mybatisplus.annotation.*;
import com.huicai.common.annotation.StatusChangeable;
import lombok.Data;

import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * 会计期间实体
 */
@Data
@TableName("t_period")
public class PeriodEntity {

    @TableId(type = IdType.AUTO)
    private Long id;

    /** 会计年度 */
    private Integer year;

    /** 会计月份(1-12) */
    private Integer month;

    /** 期间编码(YYYYMM) */
    private String periodCode;

    /** 开始日期 */
    private LocalDate startDate;

    /** 结束日期 */
    private LocalDate endDate;

    /** 状态: open-开启, closed-已结账, locked-已锁定 */
    @StatusChangeable(entity = "PERIOD", fieldName = "status")
    private String status;

    /** 创建人 */
    private Long createdBy;

    /** 创建时间 */
    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;

    /** 更新人 */
    private Long updatedBy;

    /** 更新时间 */
    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updatedAt;

    /** 逻辑删除(0-未删,1-已删) */
    @TableLogic
    private Integer deleted;
}
