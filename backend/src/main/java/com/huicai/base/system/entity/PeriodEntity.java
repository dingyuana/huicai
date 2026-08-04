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

    /**
     * 期初建账状态:
     * <ul>
     *   <li>none - 未建账（期间存在但尚未录入期初余额）</li>
     *   <li>entered - 已录入未锁定（允许清空重录）</li>
     *   <li>locked - 已锁定（不可修改、不可清空）</li>
     * </ul>
     * 独立于 {@link #status}，避免锁定同时锁住凭证过账。
     */
    private String openingStatus;
}
