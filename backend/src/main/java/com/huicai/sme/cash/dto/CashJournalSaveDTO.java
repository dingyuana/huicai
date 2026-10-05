package com.huicai.sme.cash.dto;

import com.huicai.sme.cash.entity.CashJournalEntity;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * 现金日记账新增/修改入参（REQ-2026-129 / P102 DTO 隔离 批次④）。
 *
 * <p><b>剔除 {@code balance}</b>：它<b>必然</b>被
 * {@code CashJournalServiceImpl#create} 用「上期余额 + 借方 - 贷方」覆盖
 * ⇒ 客户端传什么都会被丢弃（静默忽略）。这是本批最典型的
 * 「派生字段不该进 DTO」实例。
 *
 * <p>同时剔除 {@code voucherId}（制证后写）、{@code source}（Service 默认
 * {@code MANUAL}）、{@code version}（乐观锁）与审计/逻辑删除字段。
 */
@Data
public class CashJournalSaveDTO {

    @NotBlank(message = "会计期间不能为空")
    @Size(max = 16, message = "期间长度不能超过 16")
    private String period;

    @NotNull(message = "日记账日期不能为空")
    private LocalDate journalDate;

    @NotBlank(message = "日记账号不能为空")
    @Size(max = 64, message = "日记账号长度不能超过 64")
    private String journalNo;

    @Size(max = 255, message = "摘要过长")
    private String summary;

    private BigDecimal debit;

    private BigDecimal credit;

    private Long subjectId;

    private Long oppositeSubjectId;

    public CashJournalEntity toEntity() {
        CashJournalEntity e = new CashJournalEntity();
        e.setPeriod(period);
        e.setJournalDate(journalDate);
        e.setJournalNo(journalNo);
        e.setSummary(summary);
        e.setDebit(debit);
        e.setCredit(credit);
        e.setSubjectId(subjectId);
        e.setOppositeSubjectId(oppositeSubjectId);
        return e;
    }
}
