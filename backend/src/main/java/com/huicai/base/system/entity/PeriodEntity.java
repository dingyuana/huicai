package com.huicai.base.system.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.huicai.common.annotation.StatusChangeable;
import com.huicai.common.entity.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDate;

/**
 * 会计期间实体
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("t_period")
public class PeriodEntity extends BaseEntity {

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
}
