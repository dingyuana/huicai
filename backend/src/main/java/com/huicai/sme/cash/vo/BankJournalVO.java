package com.huicai.sme.cash.vo;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.huicai.sme.cash.entity.BankJournalEntity;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

/**
 * BankJournalVO 出参 VO（P102 出参面 DTO 化 **批次 4/9**）
 *
 * 银行日记账出参。对应前端 `bankJournal.ts` 的 `interface BankJournalVO`。
 *
 * <p><b>字段集与前端接口逐字段一致</b>，由 {@code MasterDataVoContractTest} 锁死。
 *
 * <p><b>刻意不外露</b>：{@code deleted}（逻辑删除位）、{@code enterpriseId}（租户内部维度）、
 * 以及审计列 {@code createdBy}/{@code updatedBy}。
 *
 * @see AGENTS.md §0「出参面 Entity 直出」待办
 */
@Data
public class BankJournalVO {

    /** DB 列 `id` */
    private Long id;
    /** DB 列 `account_id` */
    private Long accountId;
    /** DB 列 `tx_date` */
    private LocalDate txDate;
    /** DB 列 `period` */
    private String period;
    /** DB 列 `tx_type` */
    private String txType;
    /** DB 列 `counter_account` */
    private String counterAccount;
    /** DB 列 `amount` */
    private BigDecimal amount;
    /** DB 列 `summary` */
    private String summary;
    /** DB 列 `business_doc_id` */
    private Long businessDocId;
    /** DB 列 `voucher_id` */
    private Long voucherId;
    /** DB 列 `is_reconciled` */
    private Boolean isReconciled;
    /** DB 列 `created_at` */
    private LocalDateTime createdAt;

    public static BankJournalVO from(BankJournalEntity e) {
        if (e == null) {
            return null;
        }
        BankJournalVO vo = new BankJournalVO();
        vo.setId(e.getId());
        vo.setAccountId(e.getAccountId());
        vo.setTxDate(e.getTxDate());
        vo.setPeriod(e.getPeriod());
        vo.setTxType(e.getTxType());
        vo.setCounterAccount(e.getCounterAccount());
        vo.setAmount(e.getAmount());
        vo.setSummary(e.getSummary());
        vo.setBusinessDocId(e.getBusinessDocId());
        vo.setVoucherId(e.getVoucherId());
        vo.setIsReconciled(e.getIsReconciled());
        vo.setCreatedAt(e.getCreatedAt());
        return vo;
    }

    public static List<BankJournalVO> from(List<BankJournalEntity> list) {
        if (list == null) {
            return null;
        }
        return list.stream().map(BankJournalVO::from).collect(java.util.stream.Collectors.toList());
    }

    /** Entity 分页 → VO 分页；只换 records 元素类型，分页元信息原样透传 */
    public static IPage<BankJournalVO> from(IPage<BankJournalEntity> page) {
        if (page == null) {
            return null;
        }
        com.baomidou.mybatisplus.extension.plugins.pagination.Page<BankJournalVO> out =
                new com.baomidou.mybatisplus.extension.plugins.pagination.Page<>(
                        page.getCurrent(), page.getSize(), page.getTotal());
        out.setRecords(from(page.getRecords()));
        return out;
    }
}