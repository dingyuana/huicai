package com.huicai.base.system.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.huicai.common.entity.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 常用摘要库实体
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("t_summary_lib")
public class SummaryLibEntity extends BaseEntity {

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
}
