package com.huicai.sme.cash.vo;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.huicai.sme.cash.entity.CashJournalEntity;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/**
 * CashJournalVO 出参 VO（P102 出参面 DTO 化 **批次 4/9**）
 *
 * 现金日记账出参。
 * 
 * ⚠️ 本 VO **替换**了前端 `cashJournal.ts` 里那份从未被页面使用的 `interface CashJournal`：
 * 那份接口声明 `docNo`/`docDate`/`subjectName`/`status` —— **四个字段在 `t_cash_journal` 上都不存在**
 * （真实列是 `journal_no`/`journal_date`，且无 subject_name / status 列）。
 * 而页面 `CashJournalList.vue` 的表格列用的是 `journalNo`/`journalDate`/`summary`/`debit`/`credit`/`balance`/`voucherId`，
 * **全部是真实列** ⇒ 那份 TS 接口是陈旧死接口，页面不受影响。
 * 本次按 Employee 的 `department` 同型处置，把 TS 接口改成页面真实在用的字段。
 *
 * <p><b>字段集与前端接口逐字段一致</b>，由 {@code MasterDataVoContractTest} 锁死。
 *
 * <p><b>刻意不外露</b>：{@code deleted}（逻辑删除位）、{@code enterpriseId}（租户内部维度）、
 * 以及审计列 {@code createdBy}/{@code updatedBy}。
 *
 * @see AGENTS.md §0「出参面 Entity 直出」待办
 */
@Data
public class CashJournalVO {

    /** DB 列 `id` */
    private Long id;
    /** DB 列 `period` */
    private String period;
    /** DB 列 `journal_date` */
    private LocalDate journalDate;
    /** DB 列 `journal_no` */
    private String journalNo;
    /** DB 列 `summary` */
    private String summary;
    /** DB 列 `debit` */
    private BigDecimal debit;
    /** DB 列 `credit` */
    private BigDecimal credit;
    /** DB 列 `balance` */
    private BigDecimal balance;
    /** DB 列 `subject_id` */
    private Long subjectId;
    /** DB 列 `opposite_subject_id` */
    private Long oppositeSubjectId;
    /** DB 列 `voucher_id` */
    private Long voucherId;
    /** DB 列 `source` */
    private String source;

    public static CashJournalVO from(CashJournalEntity e) {
        if (e == null) {
            return null;
        }
        CashJournalVO vo = new CashJournalVO();
        vo.setId(e.getId());
        vo.setPeriod(e.getPeriod());
        vo.setJournalDate(e.getJournalDate());
        vo.setJournalNo(e.getJournalNo());
        vo.setSummary(e.getSummary());
        vo.setDebit(e.getDebit());
        vo.setCredit(e.getCredit());
        vo.setBalance(e.getBalance());
        vo.setSubjectId(e.getSubjectId());
        vo.setOppositeSubjectId(e.getOppositeSubjectId());
        vo.setVoucherId(e.getVoucherId());
        vo.setSource(e.getSource());
        return vo;
    }

    public static List<CashJournalVO> from(List<CashJournalEntity> list) {
        if (list == null) {
            return null;
        }
        return list.stream().map(CashJournalVO::from).collect(java.util.stream.Collectors.toList());
    }

    /** Entity 分页 → VO 分页；只换 records 元素类型，分页元信息原样透传 */
    public static IPage<CashJournalVO> from(IPage<CashJournalEntity> page) {
        if (page == null) {
            return null;
        }
        com.baomidou.mybatisplus.extension.plugins.pagination.Page<CashJournalVO> out =
                new com.baomidou.mybatisplus.extension.plugins.pagination.Page<>(
                        page.getCurrent(), page.getSize(), page.getTotal());
        out.setRecords(from(page.getRecords()));
        return out;
    }
}