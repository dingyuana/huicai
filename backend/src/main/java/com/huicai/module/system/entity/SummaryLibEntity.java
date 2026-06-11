package com.huicai.module.system.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 常用摘要库实体
 */
@Data
@TableName("t_summary_lib")
public class SummaryLibEntity {

    @TableId(type = IdType.ASSIGN_ID)
    private Long id;

    /** 摘要编码 */
    private String summaryCode;

    /** 摘要内容 */
    private String summaryText;

    /** 分类(费用/收入/往来/转账等) */
    private String category;

    /** 排序号 */
    private Integer sortOrder;

    /** 是否启用 */
    private Boolean isActive;

    /** 创建人 */
    private Long createdBy;

    /** 创建时间 */
    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;

    /** 更新人 */
    private Long updatedBy;

    /** 更新时间 */
    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updatedAt;

    /** 逻辑删除(0-未删,1-已删) */
    @TableLogic
    private Integer deleted;
}
