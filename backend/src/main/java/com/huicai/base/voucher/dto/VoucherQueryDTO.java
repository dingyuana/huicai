package com.huicai.base.voucher.dto;

import lombok.Data;
import java.time.LocalDate;

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

    /** 视图范围: pending=待处理(流程未终结), completed=已完成(已制证), 空=全部 */
    private String scope;

    /** 单据日期范围（覆盖 period，优先使用） */
    private LocalDate startDate;
    private LocalDate endDate;

    /** 当前页 */
    private Integer current = 1;

    /** 每页条数 */
    private Integer size = 20;
}
