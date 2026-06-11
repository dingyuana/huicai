package com.huicai.module.system.model.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;

@Data
@Schema(description = "科目树节点")
public class SubjectTreeVO {

    @Schema(description = "主键")
    private Long id;

    @Schema(description = "科目编码")
    private String code;

    @Schema(description = "科目名称")
    private String name;

    @Schema(description = "父科目ID")
    private Long parentId;

    @Schema(description = "科目层级")
    private Integer level;

    @Schema(description = "借贷方向: debit-借方, credit-贷方")
    private String direction;

    @Schema(description = "是否末级科目")
    private Boolean isLeaf;

    @Schema(description = "辅助核算类型")
    private String auxCalcType;

    @Schema(description = "是否启用")
    private Boolean isActive;

    @Schema(description = "备注")
    private String remark;

    @Schema(description = "创建时间")
    private LocalDateTime createdAt;

    @Schema(description = "子科目列表")
    private List<SubjectTreeVO> children;
}