package com.huicai.base.voucher.dto;

import lombok.Data;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * P142 D-7A：凭证过账端点的出参 VO——携带预算提示（WARN / REQUIRE_APPROVE）。
 *
 * <p>原先过账返回 {@code R<Void>}，结构上无处携带 warnings；本 VO 替换后
 * 前端可对 {@code budgetWarnings} 做 toast。BLOCK 超额仍走 BusinessException
 * （不落本 VO——拒绝 = 失败响应）。
 *
 * <p>出参面铁律 #13：以 {@code VO} 结尾，非 Entity。
 */
@Data
public class VoucherPostResultVO {

    private Long voucherId;
    /** 过账后恒为 POSTED */
    private String status;
    /** 凭证期间（与预算期间同格式 VARCHAR(6)） */
    private String period;
    /** 红冲凭证 id（正常凭证为 null；红冲场景本批不做预算检查） */
    private Long reversedFrom;

    private List<BudgetWarningDTO> budgetWarnings = new ArrayList<>();

    public List<BudgetWarningDTO> getBudgetWarnings() {
        return budgetWarnings == null ? Collections.emptyList() : budgetWarnings;
    }
}
