package com.huicai.sme.arap.dto.vo;

import com.huicai.sme.arap.entity.ReconciliationLogEntity;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 核销操作日志 VO — 对应 t_reconciliation_log
 * <p>
 * 剔除服务端托管字段（deleted / enterpriseId / version / createdBy / updatedBy）
 * 与内部元数据（tenantId），只保留业务可见字段。
 */
@Data
public class ReconciliationLogVO {

    private Long id;
    private String sourceDocType;
    private Long sourceDocId;
    private String targetDocType;
    private Long targetDocId;
    private Long targetBusinessDocId;
    private BigDecimal allocatedAmount;
    private BigDecimal discountAmount;
    private BigDecimal matchScore;
    private String matchMethod;
    private String status;
    private String operationType;
    private String ruleId;
    private String remark;
    private LocalDateTime createdAt;

    public static ReconciliationLogVO from(ReconciliationLogEntity e) {
        ReconciliationLogVO vo = new ReconciliationLogVO();
        vo.setId(e.getId());
        vo.setSourceDocType(e.getSourceDocType());
        vo.setSourceDocId(e.getSourceDocId());
        vo.setTargetDocType(e.getTargetDocType());
        vo.setTargetDocId(e.getTargetDocId());
        vo.setTargetBusinessDocId(e.getTargetBusinessDocId());
        vo.setAllocatedAmount(e.getAllocatedAmount());
        vo.setDiscountAmount(e.getDiscountAmount());
        vo.setMatchScore(e.getMatchScore());
        vo.setMatchMethod(e.getMatchMethod());
        vo.setStatus(e.getStatus());
        vo.setOperationType(e.getOperationType());
        vo.setRuleId(e.getRuleId());
        vo.setRemark(e.getRemark());
        vo.setCreatedAt(e.getCreatedAt());
        return vo;
    }
}
