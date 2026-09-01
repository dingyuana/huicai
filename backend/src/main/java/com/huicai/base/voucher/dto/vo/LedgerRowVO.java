package com.huicai.base.voucher.dto.vo;

import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * 总账/明细账行视图对象（T10-VO化，铁律#13）。
 * 单类复用两个账簿：总账行（type/OPENING|ENTRY|CLOSING|YEAR_TOTAL + debit/credit/running/voucherNo/voucherDate）
 * 与明细账行（type/OPENING|ENTRY + 科目信息），明细账相对总账多 subjectCode/subjectName 字段，多余字段置 null。
 */
@Data
public class LedgerRowVO {

    /** 行类型: OPENING-期初 / ENTRY-分录 / CLOSING-本期合计 / YEAR_TOTAL-本年累计 */
    private String type;

    /** 摘要 */
    private String summary;

    /** 借方发生额 */
    private BigDecimal debit;

    /** 贷方发生额 */
    private BigDecimal credit;

    /** 滚动余额 */
    private BigDecimal running;

    /** 凭证ID */
    private Long voucherId;

    /** 凭证号（T6） */
    private String voucherNo;

    /** 凭证日期（T6） */
    private LocalDate voucherDate;

    /** 科目ID（明细账行） */
    private Long subjectId;

    /** 科目编码（明细账行） */
    private String subjectCode;

    /** 科目名称（明细账行） */
    private String subjectName;
}