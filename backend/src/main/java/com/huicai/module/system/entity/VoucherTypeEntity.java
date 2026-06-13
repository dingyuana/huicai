package com.huicai.module.system.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 凭证类型实体
 */
@Data
@TableName("t_voucher_type")
public class VoucherTypeEntity {

    @TableId(type = IdType.AUTO)
    private Long id;

    /** 类型编码 */
    private String code;

    /** 类型名称(记账凭证/收款凭证/付款凭证/转账凭证) */
    private String name;

    /** 排序号 */
    private Integer sortOrder;

    /** 编号规则 */
    private String numberingRule;

    /** 是否启用 */
    private Boolean isActive;

    /** 备注 */
    private String remark;

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
