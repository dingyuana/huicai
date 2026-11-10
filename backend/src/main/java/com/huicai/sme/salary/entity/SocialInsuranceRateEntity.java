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
 * 基数（按 {@code baseMode} 取值，V203 SPEC-P159）× {@code companyRate}
 * 计算并落 {@code t_salary_bill_item} 明细（计算结果落库留痕）。
 * baseMode=GROSS（默认，历史口径）基数=该员工当期应发合计；
 * baseMode=EMP_DECL 基数=该员工社保核定基数（t_employee.social_base_declared，
 * 员工未核定 fail-loud 拒存——静默回落会把核定基数错算成应发）。</p>
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
    /**
     * SPEC-P159（V203）：基数口径 GROSS=当期应发（默认，兼容存量行）；
     * EMP_DECL=员工社保核定基数（t_employee.social_base_declared）。
     */
    private String baseMode;
    private String remark;
}
