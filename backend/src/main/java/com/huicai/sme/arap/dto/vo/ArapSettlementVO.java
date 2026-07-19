package com.huicai.sme.arap.dto.vo;

import com.huicai.sme.arap.entity.ArapSettlementEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 核销单 VO，扩展客户/供应商名称用于列表展示.
 */
@Data
@EqualsAndHashCode(callSuper = true)
public class ArapSettlementVO extends ArapSettlementEntity {

    /** 客户名称（partyType=CUSTOMER 时） */
    private String customerName;

    /** 供应商名称（partyType=VENDOR 时） */
    private String vendorName;
}