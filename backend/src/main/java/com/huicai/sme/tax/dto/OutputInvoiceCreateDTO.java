package com.huicai.sme.tax.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * 销项发票创建入参（REQ-2026-129 / P102 DTO 隔离）
 *
 * <p><b>为何要有这个 DTO</b>：原端点直接接 {@code @RequestBody OutputInvoiceEntity}，
 * 违反铁律 #13，且造成一个**实打实的越权缺陷**（已由
 * {@code OutputInvoiceCreateStatusRealDBTest#clientSuppliedStatusIsIgnored} 复现）：
 * Entity 直入意味着 {@code status} 可被客户端任意指定，POST
 * {@code {"status":"VOUCHERED"}} 即可直接创建「已制证」发票，
 * **绕过 PENDING_CONFIRM→PENDING_REVIEW→CONFIRMED 的人工审核链**（铁律 #1）。
 *
 * <p><b>本 DTO 刻意不含 status 字段</b> —— 让越权值<b>根本无法绑定</b>，
 * 而不是在 Service 里「忽略」它。后者依赖开发者记得写判断，前者由类型系统保证。
 *
 * <p>不含 {@code id}/{@code deleted}/{@code enterpriseId}/{@code createdBy} 等
 * 服务端托管字段：这些一律由 {@code MyMetaObjectHandler} 与 Service 决定，
 * 接受客户端传值等于交出越权面。
 */
@Data
public class OutputInvoiceCreateDTO {

    @NotNull(message = "开票日期不能为空")
    private LocalDate invoiceDate;

    /** 不含税金额。createOutput 按 amount * taxRate / 100 反算税额 */
    @NotNull(message = "金额不能为空")
    @DecimalMin(value = "0.01", message = "金额必须大于 0")
    private BigDecimal amount;

    @NotNull(message = "税率不能为空")
    @DecimalMin(value = "0", message = "税率不能为负")
    private BigDecimal taxRate;

    /** 发票类型：chk_output_invoice_type 仅允许 SPECIAL / PLAIN / CUSTOMS */
    @NotNull(message = "发票类型不能为空")
    @Pattern(regexp = "SPECIAL|PLAIN|CUSTOMS", message = "发票类型仅支持 SPECIAL / PLAIN / CUSTOMS")
    private String invoiceType;

    @Size(max = 64, message = "发票号码长度不能超过 64")
    private String invoiceNo;

    /** 所属期间，缺省按开票日期推导 */
    @Pattern(regexp = "^$|^\\d{6}$", message = "期间格式须为 yyyyMM")
    private String period;

    private Long customerId;

    @Size(max = 128, message = "客户名称过长")
    private String customerName;

    @Size(max = 500, message = "备注过长")
    private String remark;
}