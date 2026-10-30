package com.huicai.sme.tax.dto.vo;

import com.huicai.base.business.entity.OutputInvoiceEntity;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * 销项发票出参 VO（P140 出参面 VO 化批次17）。
 * 字段集与前端 {@code OutputInvoice} interface 对齐。
 * 剔除服务端托管字段（deleted / enterpriseId / version / createdBy / updatedBy / updatedAt）。
 */
@Data
public class OutputInvoiceVO {

    private Long id;
    private String invoiceNo;
    private LocalDate invoiceDate;
    private String period;
    private Long customerId;
    private String customerName;
    private BigDecimal amount;
    private BigDecimal taxRate;
    private BigDecimal taxAmount;
    private BigDecimal totalAmount;
    private String invoiceType;
    private String status;
    private String remark;
    private String aiRiskTag;
    private String originalInvoiceNo;
    private Long originalInvoiceId;
    private Long reversedByInvoiceId;
    private String reversedByInvoiceNo;
    private String docNo;
    private String receivableNo;
    private String voucherNo;

    public static OutputInvoiceVO from(OutputInvoiceEntity e) {
        OutputInvoiceVO vo = new OutputInvoiceVO();
        vo.setId(e.getId());
        vo.setInvoiceNo(e.getInvoiceNo());
        vo.setInvoiceDate(e.getInvoiceDate());
        vo.setPeriod(e.getPeriod());
        vo.setCustomerId(e.getCustomerId());
        vo.setCustomerName(e.getCustomerName());
        vo.setAmount(e.getAmount());
        vo.setTaxRate(e.getTaxRate());
        vo.setTaxAmount(e.getTaxAmount());
        vo.setTotalAmount(e.getTotalAmount());
        vo.setInvoiceType(e.getInvoiceType());
        vo.setStatus(e.getStatus());
        vo.setRemark(e.getRemark());
        vo.setAiRiskTag(e.getAiRiskTag());
        vo.setOriginalInvoiceNo(e.getOriginalInvoiceNo());
        vo.setOriginalInvoiceId(e.getOriginalInvoiceId());
        vo.setReversedByInvoiceId(e.getReversedByInvoiceId());
        vo.setReversedByInvoiceNo(e.getReversedByInvoiceNo());
        vo.setDocNo(e.getDocNo());
        vo.setReceivableNo(e.getReceivableNo());
        vo.setVoucherNo(e.getVoucherNo());
        return vo;
    }
}
