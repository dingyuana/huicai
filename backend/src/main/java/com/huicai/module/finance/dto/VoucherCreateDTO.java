package com.huicai.module.finance.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.math.BigDecimal;
import java.util.List;

/**
 * 凭证创建/更新请求 DTO
 */
@Data
public class VoucherCreateDTO {

    /** 凭证ID(更新时传) */
    private Long id;

    /** 会计期间(YYYYMM) */
    @NotBlank(message = "会计期间不能为空")
    private String period;

    /** 凭证类型ID */
    @NotNull(message = "凭证类型不能为空")
    private Long voucherTypeId;

    /** 摘要 */
    @Size(max = 500, message = "摘要不能超过500字符")
    private String summary;

    /** 附件ID列表(逗号分隔) */
    private String attachmentIds;

    /** 分录列表 */
    @NotEmpty(message = "分录不能为空")
    @Valid
    private List<EntryDTO> entries;

    /**
     * 分录 DTO
     */
    @Data
    public static class EntryDTO {

        /** 分录ID(更新时传) */
        private Long id;

        /** 科目ID */
        @NotNull(message = "科目不能为空")
        private Long subjectId;

        /** 借方金额 */
        @NotNull(message = "借方金额不能为空")
        private BigDecimal debit;

        /** 贷方金额 */
        @NotNull(message = "贷方金额不能为空")
        private BigDecimal credit;

        /** 分录摘要 */
        @Size(max = 500, message = "分录摘要不能超过500字符")
        private String summary;

        /** 辅助核算信息(JSON) */
        private String assistJson;

        /** 排序号 */
        private Integer sortOrder;
    }
}
