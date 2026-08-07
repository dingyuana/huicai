package com.huicai.sme.tax.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * 批量操作结果（best-effort 模式，P56）
 *
 * <p>单条失败不影响其他，成功与失败明细一并返回给前端
 */
@Data
@AllArgsConstructor
@NoArgsConstructor
public class BatchOperationResult {

    /** 成功 ID 列表 */
    private List<Long> success;

    /** 失败明细（每条含 id + 失败原因） */
    private List<FailureDetail> failure;

    @Data
    @AllArgsConstructor
    @NoArgsConstructor
    public static class FailureDetail {
        private Long id;
        private String reason;
    }
}
