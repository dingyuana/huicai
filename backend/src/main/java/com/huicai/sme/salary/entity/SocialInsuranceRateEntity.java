package com.huicai.sme.salary.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.huicai.common.entity.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.math.BigDecimal;

/**
 * SPEC-P155 R1：单位社保险种比例配置 —— 挂在 {@code t_salary_item}
 * （category=COMPANY_SOCIAL）项目行上，每项目一条生效比例。
 *
 * <p>金额来源按用户裁定=【按比例自动算】：保存工资单时按
 * 基数（当期应发 clamp [baseFloor, baseCap]）× {@code companyRate}
 * 计算并落 {@code t_salary_bill_item} 明细（计算结果落库留痕）。
 * 🔴 基数口径 = 当期应发合计，与社保局合规申报基数可能不同（登记遗留）。</p>
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("t_social_insurance_rate")
public class SocialInsuranceRateEntity extends BaseEntity {

    /** 对应 t_salary_item(id)，该行 category 必须 = COMPANY_SOCIAL */
    private Long itemId;
    /** 单位承担比例，如 0.160000 = 16%（0 < rate ≤ 1） */
    private BigDecimal companyRate;
    /** 缴费基数下限（NULL = 不设下限） */
    private BigDecimal baseFloor;
    /** 缴费基数上限（NULL = 不设上限） */
    private BigDecimal baseCap;
    private String remark;
}
