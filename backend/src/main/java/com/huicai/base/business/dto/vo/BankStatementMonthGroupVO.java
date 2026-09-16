package com.huicai.base.business.dto.vo;

import com.huicai.base.business.entity.BankStatementEntity;
import lombok.Data;

import java.math.BigDecimal;
import java.util.List;

/**
 * 银行流水按月分组 VO，用于已制证归档视图.
 */
@Data
public class BankStatementMonthGroupVO {

    /** 月份，格式 YYYY-MM */
    private String month;

    /** 该月流水条数 */
    private Integer count;

    /** 该月流水金额合计（绝对值） */
    private BigDecimal totalAmount;

    /** 该月流水明细，按 tx_date DESC */
    private List<BankStatementEntity> items;
}
