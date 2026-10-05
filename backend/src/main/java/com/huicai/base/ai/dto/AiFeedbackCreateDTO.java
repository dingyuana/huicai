package com.huicai.base.ai.dto;

import com.huicai.base.ai.entity.AiFeedbackLogEntity;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * AI 建议的人工反馈入参（REQ-2026-129 / P102 DTO 隔离 批次④）。
 *
 * <p>剔除 {@code tenantId}（由 MyMetaObjectHandler 强制覆盖为当前企业）、
 * {@code createdBy}/{@code createdAt}（审计字段）。
 *
 * <p><b>⚠️ 保留 AI 侧字段的理由（易被误当成「越权值」）</b>：
 * {@code aiSuggestedAction}/{@code aiConfidence}/{@code aiBusinessScene}
 * 是「人工当时看到的 AI 建议」这一<b>事实记录</b>，不是让客户端指定 AI 结论 ——
 * {@code AiFeedbackLogServiceImpl#create} 虽不读它们，但 {@code mapper.insert}
 * 会<b>整行落库</b>，剔除即<b>丢历史数据</b>（反馈日志失去可追溯性）。
 * ⇒ 判定依据是「谁产生、落不落库」，不是「名字里有没有 ai」。
 */
@Data
public class AiFeedbackCreateDTO {

    /** 银行流水 ID */
    private Long bankTxnId;

    @Size(max = 64, message = "AI 建议分类长度不能超过 64")
    private String aiSuggestedAction;

    @Min(value = 0, message = "置信度必须在 0~100 之间")
    @Max(value = 100, message = "置信度必须在 0~100 之间")
    private Integer aiConfidence;

    @Size(max = 64, message = "业务场景长度不能超过 64")
    private String aiBusinessScene;

    /** 人工动作（Service 会校验取值，非法即 400） */
    @Pattern(regexp = "CONFIRM_AI|MANUAL_RECLASSIFY|IGNORE_AI|BATCH_CONFIRM",
            message = "人工动作取值不合法")
    private String humanAction;

    /** 人工修改字段（JSON 文本，服务端用 JsonbTypeHandler 落 jsonb 列） */
    private String humanModifiedFields;

    public AiFeedbackLogEntity toEntity() {
        AiFeedbackLogEntity e = new AiFeedbackLogEntity();
        e.setBankTxnId(bankTxnId);
        e.setAiSuggestedAction(aiSuggestedAction);
        e.setAiConfidence(aiConfidence);
        e.setAiBusinessScene(aiBusinessScene);
        e.setHumanAction(humanAction);
        e.setHumanModifiedFields(humanModifiedFields);
        return e;
    }
}
