package com.huicai.sme.arap.dto.vo;

import com.huicai.sme.arap.entity.ReconciliationExceptionEntity;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 核销异常池出参 VO（P140 出参面 VO 化批次17）。
 * 字段集与前端 {@code ReconciliationException} interface 对齐。
 * 剔除服务端托管字段（deleted / enterpriseId / version / createdBy / updatedAt / updatedBy）。
 */
@Data
public class ReconciliationExceptionVO {

    private Long id;
    private Long tenantId;
    private String sourceDocType;
    private Long sourceDocId;
    private String targetDocType;
    private Long targetDocId;
    private Long partyId;
    private String partyType;
    private BigDecimal amount;
    private BigDecimal unsettledAmount;
    private String exceptionType;
    private String exceptionReason;
    private String matchSuggestion;
    private String status;
    private Integer retryCount;
    private Long assignedTo;
    private Long resolvedBy;
    private LocalDateTime resolvedAt;
    private String remark;
    private Long createdBy;

    public static ReconciliationExceptionVO from(ReconciliationExceptionEntity e) {
        ReconciliationExceptionVO vo = new ReconciliationExceptionVO();
        vo.setId(e.getId());
        vo.setTenantId(e.getTenantId());
        vo.setSourceDocType(e.getSourceDocType());
        vo.setSourceDocId(e.getSourceDocId());
        vo.setTargetDocType(e.getTargetDocType());
        vo.setTargetDocId(e.getTargetDocId());
        vo.setPartyId(e.getPartyId());
        vo.setPartyType(e.getPartyType());
        vo.setAmount(e.getAmount());
        vo.setUnsettledAmount(e.getUnsettledAmount());
        vo.setExceptionType(e.getExceptionType());
        vo.setExceptionReason(e.getExceptionReason());
        vo.setMatchSuggestion(e.getMatchSuggestion());
        vo.setStatus(e.getStatus());
        vo.setRetryCount(e.getRetryCount());
        vo.setAssignedTo(e.getAssignedTo());
        vo.setResolvedBy(e.getResolvedBy());
        vo.setResolvedAt(e.getResolvedAt());
        vo.setRemark(e.getRemark());
        vo.setCreatedBy(e.getCreatedBy());
        return vo;
    }
}
