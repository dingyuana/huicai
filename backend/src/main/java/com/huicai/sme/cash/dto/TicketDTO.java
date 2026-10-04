package com.huicai.sme.cash.dto;

import com.huicai.sme.cash.entity.TicketEntity;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * 票据（ticket）新增/修改入参（REQ-2026-129 / P102 DTO 隔离 批次①）。
 *
 * <p><b>为何要有这个 DTO</b>：原端点直接 {@code @RequestBody TicketEntity}，
 * 违反铁律 #13。{@code t_ticket} 有 {@code chk_ticket_status}，允许
 * {@code IN_STOCK / ISSUED / ENDORSED / CASHED / VOIDED} ⇒ 客户端 POST
 * {@code status=CASHED} 可一步创建「已兑付」票据，绕过入库→签发→背书→兑付的
 * 人工流程（铁律 #1）。
 *
 * <p>同时剔除 {@code createdBy}（Service 取当前用户）、{@code updatedBy} 与
 * {@code createdAt/updatedAt/deleted} 等审计字段。
 */
@Data
public class TicketDTO {

    @NotBlank(message = "票据号不能为空")
    @Size(max = 64, message = "票据号长度不能超过 64")
    private String ticketNo;

    /** chk_ticket_type 仅允许 CHECK / DRAFT / CASHIER_CHECK / BANK_ACCEPTANCE */
    @NotBlank(message = "票据类型不能为空")
    @Pattern(regexp = "CHECK|DRAFT|CASHIER_CHECK|BANK_ACCEPTANCE",
            message = "票据类型仅支持 CHECK / DRAFT / CASHIER_CHECK / BANK_ACCEPTANCE")
    private String ticketType;

    @NotNull(message = "票据金额不能为空")
    @DecimalMin(value = "0.01", message = "票据金额必须大于 0")
    private BigDecimal amount;

    private Long bankId;

    @Size(max = 128, message = "收款人过长")
    private String payee;

    @Size(max = 128, message = "出票人过长")
    private String drawer;

    private LocalDate issueDate;

    private LocalDate expireDate;

    @Size(max = 500, message = "备注过长")
    private String remark;

    public TicketEntity toEntity() {
        TicketEntity e = new TicketEntity();
        e.setTicketNo(ticketNo);
        e.setTicketType(ticketType);
        e.setAmount(amount);
        e.setBankId(bankId);
        e.setPayee(payee);
        e.setDrawer(drawer);
        e.setIssueDate(issueDate);
        e.setExpireDate(expireDate);
        e.setRemark(remark);
        return e;
    }
}