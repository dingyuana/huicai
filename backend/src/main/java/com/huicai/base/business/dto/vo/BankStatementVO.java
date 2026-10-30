package com.huicai.base.business.dto.vo;

import com.huicai.base.business.entity.BankStatementEntity;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * 银行流水出参 VO（P140 出参面 VO 化批次17）。
 * 字段集与前端 {@code BankStatementVO} interface 严格对齐。
 * 剔除服务端托管字段（deleted / enterpriseId / version / createdBy / updatedBy / updatedAt）
 * 与非展示内部字段（purpose / transactionRemark / rawData）。
 */
@Data
public class BankStatementVO {

    private Long id;
    /** DB 列 accountId(Long)，前端 interface 类型为 string，序列化后统一转字符串 */
    private String accountId;
    private LocalDate txDate;
    private String txType;
    private String counterAccount;
    private BigDecimal amount;
    private String summary;
    private String externalNo;
    private Long matchedJournalId;
    private String matchStatus;
    private LocalDateTime importedAt;
    private String direction;
    private String batchId;
    private String classification;
    private Long ruleId;
    private Integer aiConfidence;
    private String aiSuggestedAction;
    private String aiBusinessScene;
    private String subjectLevel1;
    private String subjectLevel2;
    private String subjectLevel3;
    private String reviewStatus;
    private Long reviewedBy;
    private LocalDateTime reviewedAt;
    private Long generatedDocId;
    private Long generatedVoucherId;
    private String generatedDocNo;
    private String generatedVoucherNo;
    private LocalDateTime generatedAt;

    public static BankStatementVO from(BankStatementEntity e) {
        BankStatementVO vo = new BankStatementVO();
        vo.setId(e.getId());
        vo.setAccountId(e.getAccountId() != null ? e.getAccountId().toString() : null);
        vo.setTxDate(e.getTxDate());
        vo.setTxType(e.getTxType());
        vo.setCounterAccount(e.getCounterAccount());
        vo.setAmount(e.getAmount());
        vo.setSummary(e.getSummary());
        vo.setExternalNo(e.getExternalNo());
        vo.setMatchedJournalId(e.getMatchedJournalId());
        vo.setMatchStatus(e.getMatchStatus());
        vo.setImportedAt(e.getImportedAt());
        vo.setDirection(e.getDirection());
        vo.setBatchId(e.getBatchId());
        vo.setClassification(e.getClassification());
        vo.setRuleId(e.getRuleId());
        vo.setAiConfidence(e.getAiConfidence());
        vo.setAiSuggestedAction(e.getAiSuggestedAction());
        vo.setAiBusinessScene(e.getAiBusinessScene());
        vo.setSubjectLevel1(e.getSubjectLevel1());
        vo.setSubjectLevel2(e.getSubjectLevel2());
        vo.setSubjectLevel3(e.getSubjectLevel3());
        vo.setReviewStatus(e.getReviewStatus());
        vo.setReviewedBy(e.getReviewedBy());
        vo.setReviewedAt(e.getReviewedAt());
        vo.setGeneratedDocId(e.getGeneratedDocId());
        vo.setGeneratedVoucherId(e.getGeneratedVoucherId());
        vo.setGeneratedDocNo(e.getGeneratedDocNo());
        vo.setGeneratedVoucherNo(e.getGeneratedVoucherNo());
        vo.setGeneratedAt(e.getGeneratedAt());
        return vo;
    }
}
