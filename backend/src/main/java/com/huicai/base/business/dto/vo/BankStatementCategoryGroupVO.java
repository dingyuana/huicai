package com.huicai.base.business.dto.vo;

import com.huicai.base.business.entity.BankStatementEntity;
import lombok.Data;

import java.math.BigDecimal;
import java.util.List;

/**
 * 银行流水按<b>业务分类</b>分组 VO（SPEC-P151 分类视图）。
 *
 * <p>与 {@link BankStatementMonthGroupVO}（已制证归档按月折叠）同形：
 * 组头给笔数与收/支合计，明细由前端沿用同一张表渲染。</p>
 */
@Data
public class BankStatementCategoryGroupVO {

    /** 分类码（BankClassification 的取值）；未分类归到 other_unknown */
    private String classification;

    /** 该分类流水条数 */
    private Integer count;

    /** 该分类收入合计 */
    private BigDecimal income;

    /** 该分类支出合计 */
    private BigDecimal expense;

    /** 该分类明细，按 tx_date DESC */
    private List<BankStatementEntity> items;
}
