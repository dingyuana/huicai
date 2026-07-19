package com.huicai.sme.arap.dto.vo;

import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

@Data
public class ReconciliationTraceVO {

    private String traceId;

    private SettlementInfo settlement;

    private UpstreamInfo upstream;

    private DownstreamInfo downstream;

    private List<OperationTrail> operationTrail;

    private VoucherInfo voucher;

    @Data
    public static class SettlementInfo {
        private Long id;
        private String settlementNo;
        private BigDecimal amount;
        private String status;
        private LocalDateTime createdAt;
    }

    @Data
    public static class UpstreamInfo {
        private BankTransaction bankTransaction;
        private ReceiptInfo receipt;
    }

    @Data
    public static class BankTransaction {
        private Long id;
        private String transactionNo;
        private BigDecimal amount;
        private String counterAccount;
    }

    @Data
    public static class ReceiptInfo {
        private Long id;
        private String docNo;
        private BigDecimal amount;
        private String status;
    }

    @Data
    public static class DownstreamInfo {
        private List<BusinessDocInfo> businessDocs;
        private List<InvoiceInfo> invoices;
    }

    @Data
    public static class BusinessDocInfo {
        private Long id;
        private String docNo;
        private String docType;
        private BigDecimal amount;
        private BigDecimal settledAmount;
        private BigDecimal unsettledAmount;
    }

    @Data
    public static class InvoiceInfo {
        private Long id;
        private String invoiceNo;
        private BigDecimal amount;
        private String status;
    }

    @Data
    public static class OperationTrail {
        private String operationType;
        private String operator;
        private LocalDateTime time;
        private String remark;
    }

    @Data
    public static class VoucherInfo {
        private Long id;
        private String voucherNo;
        private String status;
    }
}
