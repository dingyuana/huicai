package com.huicai.sme.tax.dto.vo;

import com.huicai.sme.tax.entity.TaxBurdenConfigEntity;
import io.swagger.v3.oas.annotations.media.Schema;

import java.math.BigDecimal;

/**
 * 税负预警阈值 VO（SPEC-P156 D-3）。
 * 剔除服务端托管字段；未配置时由服务层构造系统默认 3% 的虚拟 VO。
 */
@Schema(description = "税负预警阈值")
public class TaxBurdenConfigVO {

    private String taxType;
    private BigDecimal thresholdRate;
    private Boolean enabled;
    private String remark;

    public static TaxBurdenConfigVO from(TaxBurdenConfigEntity e) {
        TaxBurdenConfigVO vo = new TaxBurdenConfigVO();
        vo.taxType = e.getTaxType();
        vo.thresholdRate = e.getThresholdRate();
        vo.enabled = e.getEnabled();
        vo.remark = e.getRemark();
        return vo;
    }

    public String getTaxType() { return taxType; }
    public void setTaxType(String v) { this.taxType = v; }
    public BigDecimal getThresholdRate() { return thresholdRate; }
    public void setThresholdRate(BigDecimal v) { this.thresholdRate = v; }
    public Boolean getEnabled() { return enabled; }
    public void setEnabled(Boolean v) { this.enabled = v; }
    public String getRemark() { return remark; }
    public void setRemark(String v) { this.remark = v; }
}
