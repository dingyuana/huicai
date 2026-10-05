package com.huicai.base.system.dto;

import com.huicai.base.system.entity.SummaryLibEntity;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * 凭证摘要新增/修改入参（REQ-2026-129 / P102 DTO 隔离 批次④）。
 *
 * <p>{@code summaryText} 是 {@code t_summary_lib} 上唯一 NOT NULL 且无默认值的列
 * ⇒ 必填（{@code summaryCode} 有默认值，故非必填）。
 */
@Data
public class SummaryLibSaveDTO {

    @Size(max = 64, message = "摘要编码长度不能超过 64")
    private String summaryCode;

    @NotBlank(message = "摘要内容不能为空")
    @Size(max = 200, message = "摘要内容过长")
    private String summaryText;

    @Size(max = 64, message = "分类长度不能超过 64")
    private String category;

    private Integer sortOrder;

    private Boolean isActive;

    public SummaryLibEntity toEntity() {
        SummaryLibEntity e = new SummaryLibEntity();
        e.setSummaryCode(summaryCode);
        e.setSummaryText(summaryText);
        e.setCategory(category);
        e.setSortOrder(sortOrder);
        e.setIsActive(isActive);
        return e;
    }
}
