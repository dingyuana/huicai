package com.huicai.sme.tax.dto;

import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.util.List;

/**
 * 销项发票批量操作 DTO（P56）
 *
 * <p>用途：批量提交审核/审核通过/驳回/回退/作废/生成凭证/红冲
 * <p>依据 P56 任务书 §2.1：单次最多 100 张发票
 */
@Data
public class OutputInvoiceBatchDTO {

    @NotEmpty(message = "发票ID列表不能为空")
    @Size(max = 100, message = "单次批量最多 100 张发票")
    private List<Long> ids;

    /** 驳回/作废/红冲原因（其它操作可为空） */
    private String reason;
}
