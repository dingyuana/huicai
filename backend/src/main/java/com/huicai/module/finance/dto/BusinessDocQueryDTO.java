package com.huicai.module.finance.dto;

import lombok.Data;

import java.time.LocalDate;

@Data
public class BusinessDocQueryDTO {
    private String docType;
    private String status;
    private String period;
    private String keyword;

    /** 凭证号过滤 */
    private String voucherNo;
    private LocalDate startDate;
    private LocalDate endDate;
    private Integer current = 1;
    private Integer size = 20;
}
