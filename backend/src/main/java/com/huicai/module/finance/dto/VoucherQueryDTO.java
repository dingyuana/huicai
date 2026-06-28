package com.huicai.module.finance.dto;

import lombok.Data;

/**
 * 凭证查询请求 DTO
 */
@Data
public class VoucherQueryDTO {

    /** 会计期间(YYYYMM) */
    private String period;

    /** 状态 */
    private String status;

    /** 凭证类型ID */
    private Long voucherTypeId;

    /** 关键字(凭证号/摘要/溯源单号) */
    private String keyword;

    /** 凭证号精确过滤 */
    private String voucherNo;

    /** 溯源单据号过滤 */
    private String sourceDocNo;

    /** 当前页 */
    private Integer current = 1;

    /** 每页条数 */
    private Integer size = 20;
}
