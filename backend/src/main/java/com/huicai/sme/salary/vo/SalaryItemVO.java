package com.huicai.sme.salary.vo;

import lombok.Data;

import java.math.BigDecimal;

/** SPEC-P143（D-4）：薪资项目出参（OutParamEntityStructureTest 零容忍，禁直出 Entity）。 */
@Data
public class SalaryItemVO {

    private Long id;

    private String itemCode;

    private String itemName;

    /** EARN / SOCIAL_INS / SPECIAL_ADD / OTHER_DEDUCT */
    private String category;

    private BigDecimal defaultAmount;

    private Boolean isTaxable;

    private Integer sortOrder;

    private String remark;
}
