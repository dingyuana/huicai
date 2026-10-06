package com.huicai.base.ai.vo;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.huicai.base.ai.entity.AiAnomalyTagEntity;
import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.Collectors;
import lombok.Data;

/**
 * AiAnomalyTagVO —— 由 t_ai_anomaly_tag 的真实列生成（批次 5）。
 *
 * <p>字段集 = 前端 ai.ts#AiAnomalyTag 已声明的 11 个字段，逐个核对：
 * ① 前端要而 DB 无列 → 直接停（本批已确保无）；② 前端要而 Entity 无字段 → 直接停。
 * 故本 VO 既不会漏前端字段，也不会凭空暴露前端不用的列。
 *
 * <p>刻意不外露：（无）
 */
@Data
public class AiAnomalyTagVO {

    private Long id;
    private String bizType;
    private Long bizId;
    private String anomalyType;
    private String severity;
    private String description;
    private Long aiTaskId;
    private Boolean resolved;
    private Long resolvedBy;
    private LocalDateTime resolvedAt;
    private LocalDateTime createdAt;

    public static AiAnomalyTagVO from(AiAnomalyTagEntity e) {
        if (e == null) {
            return null;
        }
        AiAnomalyTagVO vo = new AiAnomalyTagVO();
        vo.setId(e.getId());
        if (e.getId() != null) vo.setId(e.getId());
        if (e.getBizType() != null) vo.setBizType(e.getBizType());
        if (e.getBizId() != null) vo.setBizId(e.getBizId());
        if (e.getAnomalyType() != null) vo.setAnomalyType(e.getAnomalyType());
        if (e.getSeverity() != null) vo.setSeverity(e.getSeverity());
        if (e.getDescription() != null) vo.setDescription(e.getDescription());
        if (e.getAiTaskId() != null) vo.setAiTaskId(e.getAiTaskId());
        if (e.getResolved() != null) vo.setResolved(e.getResolved());
        if (e.getResolvedBy() != null) vo.setResolvedBy(e.getResolvedBy());
        if (e.getResolvedAt() != null) vo.setResolvedAt(e.getResolvedAt());
        if (e.getCreatedAt() != null) vo.setCreatedAt(e.getCreatedAt());
        return vo;
    }

    public static List<AiAnomalyTagVO> from(List<AiAnomalyTagEntity> list) {
        return list == null ? null : list.stream().map(AiAnomalyTagVO::from).collect(Collectors.toList());
    }

    /** Entity 分页 → VO 分页；只换 records 元素类型，分页元信息原样透传 */
    public static IPage<AiAnomalyTagVO> from(IPage<AiAnomalyTagEntity> page) {
        if (page == null) {
            return null;
        }
        com.baomidou.mybatisplus.extension.plugins.pagination.Page<AiAnomalyTagVO> out =
                new com.baomidou.mybatisplus.extension.plugins.pagination.Page<>(
                        page.getCurrent(), page.getSize(), page.getTotal());
        out.setRecords(from(page.getRecords()));
        return out;
    }
}
