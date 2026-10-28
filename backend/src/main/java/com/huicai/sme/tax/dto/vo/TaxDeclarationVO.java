package com.huicai.sme.tax.dto.vo;

import com.huicai.sme.tax.entity.TaxDeclarationEntity;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * 税务申报出参 VO（P140 出参面 VO 化批次17）。
 * 剔除服务端托管字段（deleted / enterpriseId / version / updatedBy / updatedAt）。
 */
@Data
public class TaxDeclarationVO {

    private Long id;
    private String declarationNo;
    private String period;
    private String taxType;
    private LocalDate declaredDate;
    private BigDecimal payableAmount;
    private String status;
    private Long voucherId;
    private String remark;
    private Long createdBy;
    private LocalDateTime createdAt;

    public static TaxDeclarationVO from(TaxDeclarationEntity e) {
        TaxDeclarationVO vo = new TaxDeclarationVO();
        vo.setId(e.getId());
        vo.setDeclarationNo(e.getDeclarationNo());
        vo.setPeriod(e.getPeriod());
        vo.setTaxType(e.getTaxType());
        vo.setDeclaredDate(e.getDeclaredDate());
        vo.setPayableAmount(e.getPayableAmount());
        vo.setStatus(e.getStatus());
        vo.setVoucherId(e.getVoucherId());
        vo.setRemark(e.getRemark());
        vo.setCreatedBy(e.getCreatedBy());
        vo.setCreatedAt(e.getCreatedAt());
        return vo;
    }
}
