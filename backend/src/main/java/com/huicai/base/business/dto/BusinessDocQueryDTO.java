package com.huicai.base.business.dto;

import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

@Data
public class BusinessDocQueryDTO {
    private String docType;
    private List<String> docTypes;
    private String status;
    private String period;
    private String keyword;

    /** 凭证号过滤 */
    private String voucherNo;
    /** 单据日期范围（覆盖 period，优先使用） */
    private LocalDate startDate;
    private LocalDate endDate;
    /** 金额区间 */
    private BigDecimal amountMin;
    private BigDecimal amountMax;
    /** 视图范围: pending=待处理(流程未终结), completed=已完成(已制证/已终结), 空=全部 */
    private String scope;
    private Integer current = 1;
    private Integer size = 20;
}
