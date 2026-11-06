package com.huicai.sme.salary.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import lombok.Data;

import java.math.BigDecimal;

/** SPEC-P143（D-4）：薪资项目入参。 */
@Data
public class SalaryItemSaveDTO {

    @NotBlank(message = "项目编码不能为空")
    private String itemCode;

    @NotBlank(message = "项目名称不能为空")
    private String itemName;

    @NotBlank(message = "项目类别不能为空")
    @Pattern(regexp = "EARN|SOCIAL_INS|SPECIAL_ADD|OTHER_DEDUCT",
            message = "类别仅支持 EARN（收入）/ SOCIAL_INS（个人社保公积金）/ "
                    + "SPECIAL_ADD（专项附加扣除）/ OTHER_DEDUCT（其他扣款）")
    private String category;

    private BigDecimal defaultAmount;

    private Boolean isTaxable;

    private Integer sortOrder;

    private String remark;
}
