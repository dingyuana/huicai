package com.huicai.base.voucher.entity;

import com.baomidou.mybatisplus.annotation.*;
import com.huicai.base.system.handler.JsonbTypeHandler;
import com.huicai.common.entity.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 凭证分录表实体
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("t_voucher_entry")
public class VoucherEntryEntity extends BaseEntity {

    /** 凭证ID */
    private Long voucherId;

    /** 科目ID */
    private Long subjectId;

    /** 借方金额 */
    private BigDecimal debit;

    /** 贷方金额 */
    private BigDecimal credit;

    /** 分录摘要 */
    private String summary;

    /** 辅助核算信息(JSON) */
    @TableField(typeHandler = JsonbTypeHandler.class)
    private String assistJson;

    /** 排序号 */
    private Integer sortOrder;

    /** 创建时间 */
    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;
}
