package com.huicai.base.business.dto;

import lombok.Data;

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
    private LocalDate startDate;
    private LocalDate endDate;
    private Integer current = 1;
    private Integer size = 20;
}
