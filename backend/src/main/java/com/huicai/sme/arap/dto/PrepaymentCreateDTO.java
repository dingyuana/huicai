package com.huicai.sme.arap.dto;

import com.huicai.sme.arap.entity.PrepaymentEntity;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * 预付款创建入参（REQ-2026-129 / P102 DTO 隔离 批次①）。
 *
 * <p><b>为何要有这个 DTO</b>：原端点直接 {@code @RequestBody PrepaymentEntity}，
 * 违反铁律 #13，且该表<b>没有任何 status CHECK 约束</b>（实测 CHECK 数为 0）——
 * 即 DB 层零兜底，连 {@code "__GARBAGE__"} 这种乱码都能原样落库。客户端 POST
 * {@code status=APPLIED/SETTLED} 可一步跳过 {@code confirm()}（DRAFT→CONFIRMED）
 * 这次人工确认（铁律 #1）。Service 侧已改为无条件强制 DRAFT，但**根因是入参面**，
 * DTO 让越权值无法绑定。
 *
 * <p>同样剔除 {@code settledAmount} / {@code unsettledAmount}（Service 按 amount 推导）、
 * {@code tenantId}（Service 兜默认值）、以及 {@code docId/voucherId/sourceDoc*}
 * 等由业务流程后写入的字段。
 */
@Data
public class PrepaymentCreateDTO {

    private Long vendorId;

    private Long customerId;

    @NotNull(message = "所属期间不能为空")
    @Pattern(regexp = "^\\d{6}$", message = "期间格式须为 yyyyMM")
    private String period;

    /** 交易日期，缺省由 Service 取当前日期 */
    private LocalDate txDate;

    @NotNull(message = "金额不能为空")
    @DecimalMin(value = "0.01", message = "金额必须大于 0")
    private BigDecimal amount;

    @Size(max = 200, message = "摘要过长")
    private String summary;

    @Size(max = 500, message = "备注过长")
    private String remark;

    public PrepaymentEntity toEntity() {
        PrepaymentEntity e = new PrepaymentEntity();
        e.setVendorId(vendorId);
        e.setCustomerId(customerId);
        e.setPeriod(period);
        e.setTxDate(txDate);
        e.setAmount(amount);
        e.setSummary(summary);
        e.setRemark(remark);
        return e;
    }
}