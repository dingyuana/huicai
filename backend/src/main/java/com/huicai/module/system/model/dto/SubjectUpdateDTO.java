package com.huicai.module.system.model.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
@Schema(description = "科目更新参数")
public class SubjectUpdateDTO {

    @NotBlank(message = "科目编码不能为空")
    @Size(max = 50, message = "科目编码最长50个字符")
    @Schema(description = "科目编码")
    private String code;

    @NotBlank(message = "科目名称不能为空")
    @Size(max = 200, message = "科目名称最长200个字符")
    @Schema(description = "科目名称")
    private String name;

    @NotBlank(message = "借贷方向不能为空")
    @Schema(description = "借贷方向: debit-借方, credit-贷方")
    private String direction;

    @Schema(description = "辅助核算类型: customer/vendor/department/project/employee")
    private String auxCalcType;

    @Schema(description = "是否启用")
    private Boolean isActive;

    @Size(max = 500, message = "备注最长500个字符")
    @Schema(description = "备注")
    private String remark;
}