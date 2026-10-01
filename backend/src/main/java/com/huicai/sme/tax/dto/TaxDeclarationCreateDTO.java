package com.huicai.sme.tax.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * 纳税申报创建入参（REQ-2026-129 / P102 DTO 隔离）
 *
 * <p><b>为何要有这个 DTO</b>：原端点直接接 {@code @RequestBody TaxDeclarationEntity}，
 * 违反铁律 #13，并造成可利用的越权缺陷（已由
 * {@code TaxDeclarationCreateStatusRealDBTest} 复现）：Entity 直入意味着
 * {@code status} 可被客户端指定，POST {@code {"status":"APPROVED"}} 即可
 * <b>一步跳过</b> {@code submitDeclaration()} + {@code approveDeclaration()} 两次人工动作
 * （铁律 #1）。修复前它与 {@code createOutput} 是同一个写法。
 *
 * <p><b>刻意不含 status 字段</b> —— 让越权值根本无法绑定，而非在 Service 里忽略它。
 *
 * <p>必填字段依据 {@code t_tax_declaration} 的 NOT NULL 且无默认值列：
 * {@code declaration_no / period / tax_type / declared_date / payable_amount}。
 *
 * <p>⚠️ {@code tax_type} 只做非空与长度约束，<b>不猜枚举</b>：
 * 该列是否带 CHECK 未经查证，按 AGENTS §4.2 第 14 条不得凭业务语感假定允许集。
 * 若后续确认存在 CHECK，应在此补 {@code @Pattern} 并同步本文档。
 */
@Data
public class TaxDeclarationCreateDTO {

    @NotBlank(message = "申报单号不能为空")
    @Size(max = 64, message = "申报单号长度不能超过 64")
    private String declarationNo;

    /** 所属期间，格式 yyyyMM */
    @NotBlank(message = "所属期间不能为空")
    @Pattern(regexp = "^\\d{6}$", message = "期间格式须为 yyyyMM")
    private String period;

    @NotBlank(message = "税种不能为空")
    @Size(max = 32, message = "税种长度不能超过 32")
    private String taxType;

    @NotNull(message = "申报日期不能为空")
    private LocalDate declaredDate;

    @NotNull(message = "应纳税额不能为空")
    @DecimalMin(value = "0.00", message = "应纳税额不能为负")
    private BigDecimal payableAmount;

    @Size(max = 500, message = "备注过长")
    private String remark;
}