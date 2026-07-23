package com.huicai.base.system.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.huicai.common.entity.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 凭证类型实体
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("t_voucher_type")
public class VoucherTypeEntity extends BaseEntity {

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
}
