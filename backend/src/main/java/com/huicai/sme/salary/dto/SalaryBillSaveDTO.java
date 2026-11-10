package com.huicai.sme.salary.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Pattern;
import lombok.Data;

import java.math.BigDecimal;
import java.util.List;

/** SPEC-P143：工资单保存入参（整单替换语义：items 全量覆盖）。 */
@Data
public class SalaryBillSaveDTO {

    @NotBlank(message = "期间不能为空")
    @Pattern(regexp = "^\\d{6}$", message = "期间格式必须为 yyyyMM")
    private String period;

    private String remark;

    @NotEmpty(message = "工资明细不能为空")
    @Valid
    private List<ItemDTO> items;

    @Data
    public static class ItemDTO {

        private Long employeeId;

        private Long itemId;

        private BigDecimal amount;
    }
}
