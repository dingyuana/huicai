package com.huicai.sme.tax.dto.vo;

import com.huicai.sme.tax.entity.TaxTypeEntity;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 税种配置出参 VO（P140 出参面 VO 化批次17）。
 * 字段集与前端 {@code TaxType} interface 对齐。
 */
@Data
public class TaxTypeVO {

    private Long id;
    private String code;
    private String name;
    private String taxCategory;
    private BigDecimal rate;
    private Boolean isActive;
    private String remark;
    private LocalDateTime createdAt;

    public static TaxTypeVO from(TaxTypeEntity e) {
        TaxTypeVO vo = new TaxTypeVO();
        vo.setId(e.getId());
        vo.setCode(e.getCode());
        vo.setName(e.getName());
        vo.setTaxCategory(e.getTaxCategory());
        vo.setRate(e.getRate());
        vo.setIsActive(e.getIsActive());
        vo.setRemark(e.getRemark());
        vo.setCreatedAt(e.getCreatedAt());
        return vo;
    }
}
