package com.huicai.base.system.model.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "Excel 导入结果")
public class ImportResult {

    @Schema(description = "总行数（不含表头）")
    private int total;

    @Schema(description = "成功导入数")
    private int success;

    @Schema(description = "错误列表")
    private List<ErrorItem> errors;

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @Schema(description = "导入错误项")
    public static class ErrorItem {

        @Schema(description = "Excel 行号（从1开始，含表头）")
        private int row;

        @Schema(description = "错误信息")
        private String message;
    }
}