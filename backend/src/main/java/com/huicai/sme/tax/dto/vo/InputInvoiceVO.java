package com.huicai.sme.tax.dto.vo;

import com.huicai.base.business.entity.InputInvoiceEntity;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * 进项发票出参 VO（P140 出参面 VO 化批次17）。
 * 字段集与前端 {@code InputInvoice} interface 对齐。
 * 剔除服务端托管字段（deleted / enterpriseId / version / createdBy / updatedBy / updatedAt）。
 */
@Data
public class InputInvoiceVO {

    private Long id;
    private String invoiceNo;
    private LocalDate invoiceDate;
    private String period;
    private Long vendorId;
    private String vendorName;
    private BigDecimal amount;
    private BigDecimal taxRate;
    private BigDecimal taxAmount;
    private BigDecimal totalAmount;
    private String invoiceType;
    private String certificationStatus;
    private String deductionPeriod;
    private BigDecimal deductionAmount;
    private String remark;

    public static InputInvoiceVO from(InputInvoiceEntity e) {
        InputInvoiceVO vo = new InputInvoiceVO();
        vo.setId(e.getId());
        vo.setInvoiceNo(e.getInvoiceNo());
        vo.setInvoiceDate(e.getInvoiceDate());
        vo.setPeriod(e.getPeriod());
        vo.setVendorId(e.getVendorId());
        vo.setVendorName(e.getVendorName());
        vo.setAmount(e.getAmount());
        vo.setTaxRate(e.getTaxRate());
        vo.setTaxAmount(e.getTaxAmount());
        vo.setTotalAmount(e.getTotalAmount());
        vo.setInvoiceType(e.getInvoiceType());
        vo.setCertificationStatus(e.getCertificationStatus());
        vo.setDeductionPeriod(e.getDeductionPeriod());
        vo.setDeductionAmount(e.getDeductionAmount());
        vo.setRemark(e.getRemark());
        return vo;
    }
}
