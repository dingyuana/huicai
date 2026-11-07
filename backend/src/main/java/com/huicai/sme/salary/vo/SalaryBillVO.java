package com.huicai.sme.salary.vo;

import lombok.Data;

import java.math.BigDecimal;
import java.util.List;

/** SPEC-P143：工资单列表行出参（凭证号查询回填）。 */
@Data
public class SalaryBillVO {

    private Long id;
    private String period;
    private String status;

    private BigDecimal totalGross;
    private BigDecimal totalSocial;
    private BigDecimal totalSpecialAdd;
    private BigDecimal totalOtherDeduct;
    private BigDecimal totalTax;
    private BigDecimal totalNet;

    private Long voucherId;
    /** 查询回填：计提凭证号 */
    private String voucherNo;
    private Long payVoucherId;
    /** 查询回填：发放凭证号 */
    private String payVoucherNo;

    /** SPEC-P155：单位社保合计（confirm 回填） */
    private BigDecimal totalCompanySocial;
    /** 社保是否已缴纳（SALJ 幂等标记） */
    private Boolean socialPaid;
    /** 查询回填：缴纳社保凭证号 */
    private String socialVoucherNo;

    private String remark;
}
