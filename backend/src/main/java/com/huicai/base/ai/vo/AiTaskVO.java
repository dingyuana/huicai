package com.huicai.base.ai.vo;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.huicai.base.ai.entity.AiTaskEntity;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.Collectors;
import lombok.Data;

/**
 * AiTaskVO —— 由 t_ai_task 的真实列生成（批次 5）。
 *
 * <p>字段集 = 前端 ai.ts#AiTask 已声明的 14 个字段，逐个核对：
 * ① 前端要而 DB 无列 → 直接停（本批已确保无）；② 前端要而 Entity 无字段 → 直接停。
 * 故本 VO 既不会漏前端字段，也不会凭空暴露前端不用的列。
 *
 * <p>刻意不外露：reviewed_at, reviewed_by, started_at
 */
@Data
public class AiTaskVO {

    private Long id;
    private String taskNo;
    private String taskType;
    private String bizType;
    private Long bizId;
    private String status;
    private String inputData;
    private String outputData;
    private BigDecimal confidence;
    private Boolean reviewed;
    private String applyStatus;
    private String errorMessage;
    private LocalDateTime createdAt;
    private LocalDateTime completedAt;

    public static AiTaskVO from(AiTaskEntity e) {
        if (e == null) {
            return null;
        }
        AiTaskVO vo = new AiTaskVO();
        vo.setId(e.getId());
        if (e.getId() != null) vo.setId(e.getId());
        if (e.getTaskNo() != null) vo.setTaskNo(e.getTaskNo());
        if (e.getTaskType() != null) vo.setTaskType(e.getTaskType());
        if (e.getBizType() != null) vo.setBizType(e.getBizType());
        if (e.getBizId() != null) vo.setBizId(e.getBizId());
        if (e.getStatus() != null) vo.setStatus(e.getStatus());
        if (e.getInputData() != null) vo.setInputData(e.getInputData());
        if (e.getOutputData() != null) vo.setOutputData(e.getOutputData());
        if (e.getConfidence() != null) vo.setConfidence(e.getConfidence());
        if (e.getReviewed() != null) vo.setReviewed(e.getReviewed());
        if (e.getApplyStatus() != null) vo.setApplyStatus(e.getApplyStatus());
        if (e.getErrorMessage() != null) vo.setErrorMessage(e.getErrorMessage());
        if (e.getCreatedAt() != null) vo.setCreatedAt(e.getCreatedAt());
        if (e.getCompletedAt() != null) vo.setCompletedAt(e.getCompletedAt());
        return vo;
    }

    public static List<AiTaskVO> from(List<AiTaskEntity> list) {
        return list == null ? null : list.stream().map(AiTaskVO::from).collect(Collectors.toList());
    }

    /** Entity 分页 → VO 分页；只换 records 元素类型，分页元信息原样透传 */
    public static IPage<AiTaskVO> from(IPage<AiTaskEntity> page) {
        if (page == null) {
            return null;
        }
        com.baomidou.mybatisplus.extension.plugins.pagination.Page<AiTaskVO> out =
                new com.baomidou.mybatisplus.extension.plugins.pagination.Page<>(
                        page.getCurrent(), page.getSize(), page.getTotal());
        out.setRecords(from(page.getRecords()));
        return out;
    }
}
