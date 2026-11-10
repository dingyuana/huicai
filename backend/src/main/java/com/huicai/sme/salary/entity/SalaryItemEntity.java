package com.huicai.sme.salary.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.huicai.common.entity.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * SPEC-P143（D-4）：薪资项目 —— 可配置的工资结构项。
 *
 * <p>{@code category} 四类（chk_salary_item_category）：
 * {@code EARN} 收入项（基本工资/绩效/津贴）、{@code SOCIAL_INS} 个人社保公积金
 * （个税「专项扣除」口径，发放时扣钱）、{@code SPECIAL_ADD} 专项附加扣除
 * （子女教育/房贷利息等，**只进个税公式不扣钱**）、{@code OTHER_DEDUCT} 其他扣款
 * （考勤扣款等，发放时扣钱）。</p>
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("t_salary_item")
public class SalaryItemEntity extends BaseEntity {

    private String itemCode;
    private String itemName;
    private String category;
    private java.math.BigDecimal defaultAmount;
    private Boolean isTaxable;
    private Integer sortOrder;
    private String remark;
}
