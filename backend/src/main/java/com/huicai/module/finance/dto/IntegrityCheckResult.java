package com.huicai.module.finance.dto;

import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 数据一致性检查结果
 */
@Data
public class IntegrityCheckResult {

    private Integer totalChecks;
    private Integer passed;
    private Integer failed;
    private List<CheckItemResult> checkResults;
    private LocalDateTime checkTime;
    private Long durationMs;

    @Data
    public static class CheckItemResult {
        private String checkId;
        private String checkName;
        private String status; // PASSED / FAILED / ERROR
        private Integer affectedRows;
        private String severity; // P0 / P1 / P2
        private String errorMessage;
        private List<?> details;
    }
}
