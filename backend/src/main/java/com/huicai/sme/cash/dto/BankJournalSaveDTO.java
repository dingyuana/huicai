package com.huicai.sme.cash.dto;

import com.huicai.sme.cash.entity.BankJournalEntity;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * 银行日记账新增/修改入参（REQ-2026-129 / P102 DTO 隔离 批次④）。
 *
 * <p><b>剔除的关键字段</b>：{@code isReconciled}（对账状态，人工对账动作产生，
 * Service 在 create 里无条件置 false）、{@code voucherId}（生成凭证后写）、
 * {@code businessDocId}（业务单据关联，由自动生成流程回写）、
 * {@code createdBy/createdAt/deleted}（审计与逻辑删除）。
 *
 * <p>{@code accountId/txDate/period/txType/amount} 在 {@code t_bank_journal}
 * 上 NOT NULL 且无默认值 ⇒ 必填；{@code txType} 的允许集以
 * {@code chk_journal_type} 为准（{@code INCOME / EXPENSE / TRANSFER_IN /
 * TRANSFER_OUT}，均为大写）。
 */
@Data
public class BankJournalSaveDTO {

    @NotNull(message = "银行账户不能为空")
    private Long accountId;

    @NotNull(message = "交易日期不能为空")
    private LocalDate txDate;

    @NotBlank(message = "会计期间不能为空")
    @Size(max = 16, message = "期间长度不能超过 16")
    private String period;

    @NotBlank(message = "交易类型不能为空")
    @Pattern(regexp = "INCOME|EXPENSE|TRANSFER_IN|TRANSFER_OUT",
            message = "交易类型仅支持 INCOME / EXPENSE / TRANSFER_IN / TRANSFER_OUT")
    private String txType;

    @NotNull(message = "金额不能为空")
    @DecimalMin(value = "0.01", message = "金额必须大于 0")
    private BigDecimal amount;

    @Size(max = 128, message = "对方账户过长")
    private String counterAccount;

    @Size(max = 255, message = "摘要过长")
    private String summary;

    public BankJournalEntity toEntity() {
        BankJournalEntity e = new BankJournalEntity();
        e.setAccountId(accountId);
        e.setTxDate(txDate);
        e.setPeriod(period);
        e.setTxType(txType);
        e.setAmount(amount);
        e.setCounterAccount(counterAccount);
        e.setSummary(summary);
        return e;
    }
}
