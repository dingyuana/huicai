package com.huicai.sme.tax.dto;

import com.huicai.base.business.entity.InputInvoiceEntity;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * 进项发票创建入参（REQ-2026-129 / P102 DTO 隔离 批次①）。
 *
 * <p><b>为何要有这个 DTO</b>：原端点直接 {@code @RequestBody InputInvoiceEntity}，
 * 违反铁律 #13。危害已由 {@code InputInvoiceCreateStatusRealDBTest} 实测复现 ——
 * {@code t_input_invoice} 上有<b>两个独立的状态列</b>：
 * {@code status}（{@code chk_input_invoice_status}）与
 * {@code certification_status}（{@code chk_cert_status}）。Entity 直入意味着两者
 * <b>都能被客户端指定</b>：POST {@code status=VOUCHERED} 可一步跳过
 * PENDING_CONFIRM→PENDING_REVIEW→CONFIRMED 的人工审核链（铁律 #1），
 * POST {@code certificationStatus=CERTIFIED} 可一步把发票标成「已认证」
 * （同样是税务合规动作，铁律 #1）。
 *
 * <p><b>本 DTO 刻意不含 status / certificationStatus</b> —— 让越权值
 * <b>根本无法绑定</b>，而不是在 Service 里「忽略」它（后者依赖开发者记得写判断）。
 *
 * <p>同样剔除的还有 Service 负责计算的字段：{@code taxAmount}、{@code totalAmount}
 * （{@code TaxServiceImpl#createInput} 按 {@code amount × taxRate / 100} 反算）、
 * 以及一批「只存在于 Entity、DB 无对应列」的幽灵字段（{@code amountExTax}、
 * {@code aiRiskTag}、{@code processStatus}、{@code aiMappingResult}、{@code docNo}、
 * {@code voucherNo} 等，见 AGENTS §4.2 第 8/15 条）—— 收它们进来等于凭空扩大入参面。
 */
@Data
public class InputInvoiceCreateDTO {

    @NotBlank(message = "发票号码不能为空")
    @Size(max = 64, message = "发票号码长度不能超过 64")
    private String invoiceNo;

    @NotNull(message = "开票日期不能为空")
    private LocalDate invoiceDate;

    /** 所属期间，缺省由 Service 按开票日期推算 */
    @Pattern(regexp = "^$|^\\d{6}$", message = "期间格式须为 yyyyMM")
    private String period;

    private Long vendorId;

    @Size(max = 128, message = "供应商名称过长")
    private String vendorName;

    /** 不含税金额 */
    @NotNull(message = "金额不能为空")
    @DecimalMin(value = "0.01", message = "金额必须大于 0")
    private BigDecimal amount;

    /** 税率，存储为百分数整数（13 表示 13%） */
    @NotNull(message = "税率不能为空")
    @DecimalMin(value = "0", message = "税率不能为负")
    private BigDecimal taxRate;

    /** chk_invoice_type 仅允许 SPECIAL / PLAIN / CUSTOMS / TRANSPORT */
    @NotBlank(message = "发票类型不能为空")
    @Pattern(regexp = "SPECIAL|PLAIN|CUSTOMS|TRANSPORT",
            message = "发票类型仅支持 SPECIAL / PLAIN / CUSTOMS / TRANSPORT")
    private String invoiceType;

    @Size(max = 500, message = "备注过长")
    private String remark;

    /** 转成 Entity：只搬运本 DTO 声明的字段，其余一律由服务端决定 */
    public InputInvoiceEntity toEntity() {
        InputInvoiceEntity e = new InputInvoiceEntity();
        e.setInvoiceNo(invoiceNo);
        e.setInvoiceDate(invoiceDate);
        e.setPeriod(period);
        e.setVendorId(vendorId);
        e.setVendorName(vendorName);
        e.setAmount(amount);
        e.setTaxRate(taxRate);
        e.setInvoiceType(invoiceType);
        e.setRemark(remark);
        return e;
    }
}