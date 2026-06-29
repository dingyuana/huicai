package com.huicai.module.finance.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 编号关联追溯 VO
 * 用于全链路查询：从任意单据号/凭证号出发，查出上下游关联关系
 */
@Data
@Schema(description = "编号关联追溯结果")
public class NumberingTraceVO {

    /** 查询入口编号（传入的 docNo/invoiceNo/voucherNo） */
    @Schema(description = "查询入口编号")
    private String traceNo;

    /** 入口类型 */
    @Schema(description = "入口类型: INVOICE/DOC/SETTLEMENT/VOUCHER")
    private String traceType;

    /** 上游链路（追溯到最原始的单据） */
    @Schema(description = "上游链路")
    private List<TraceNode> upstream;

    /** 下游链路（追溯到最终凭证） */
    @Schema(description = "下游链路")
    private List<TraceNode> downstream;

    @Data
    @Schema(description = "追溯节点")
    public static class TraceNode {
        /** 节点类型 */
        @Schema(description = "节点类型: OUTPUT_INVOICE/INPUT_INVOICE/BUSINESS_DOC/RECEIVABLE/PAYABLE/SETTLEMENT/VOUCHER")
        private String nodeType;

        /** 节点编号 */
        @Schema(description = "节点编号")
        private String nodeNo;

        /** 节点摘要 */
        @Schema(description = "节点摘要")
        private String summary;

        /** 节点金额 */
        @Schema(description = "节点金额")
        private BigDecimal amount;

        /** 状态 */
        @Schema(description = "节点状态")
        private String status;

        /** 关联凭证编号（如有） */
        @Schema(description = "关联凭证编号")
        private String voucherNo;

        /** 发票编号 */
        @Schema(description = "发票编号")
        private String invoiceNo;

        /** 创建时间 */
        @Schema(description = "创建时间")
        private LocalDateTime createdAt;
    }
}
