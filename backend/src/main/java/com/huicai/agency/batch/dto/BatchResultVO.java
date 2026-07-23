package com.huicai.agency.batch.dto;

import lombok.Data;

import java.util.List;

@Data
public class BatchResultVO {
    private int total;
    private int success;
    private int failed;
    private List<BatchItemResult> details;

    @Data
    public static class BatchItemResult {
        private Long id;
        private boolean success;
        private String message;
    }
}
