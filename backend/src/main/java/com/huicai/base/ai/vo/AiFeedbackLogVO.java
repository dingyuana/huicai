package com.huicai.base.ai.vo;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.huicai.base.ai.entity.AiFeedbackLogEntity;
import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.Collectors;
import lombok.Data;

/**
 * AiFeedbackLogVO —— 由 t_ai_feedback_log 的真实列生成（P102 批次 7）。
 *
 * <p>字段集 = 前端 ai.ts#AiFeedbackLog 已声明的 8 个字段，逐个核对：
 * ① DB 有该列；② Entity 有该字段 —— 缺一即停，不会漏前端字段也不会凭空暴露。
 *
 * <p>⚠️ 前端此前全是 <code>Promise&lt;any&gt;</code>（无任何契约），本轮补类型后由
 * MasterDataVoContractTest 逐字段锁死 VO↔interface。
 */
@Data
public class AiFeedbackLogVO {

    private Long id;
    private Long bankTxnId;
    private String aiSuggestedAction;
    private Integer aiConfidence;
    private String aiBusinessScene;
    private String humanAction;
    private String humanModifiedFields;
    private LocalDateTime createdAt;

    public static AiFeedbackLogVO from(AiFeedbackLogEntity e) {
        if (e == null) {
            return null;
        }
        AiFeedbackLogVO vo = new AiFeedbackLogVO();
        vo.setId(e.getId());
        if (e.getId() != null) vo.setId(e.getId());
        if (e.getBankTxnId() != null) vo.setBankTxnId(e.getBankTxnId());
        if (e.getAiSuggestedAction() != null) vo.setAiSuggestedAction(e.getAiSuggestedAction());
        if (e.getAiConfidence() != null) vo.setAiConfidence(e.getAiConfidence());
        if (e.getAiBusinessScene() != null) vo.setAiBusinessScene(e.getAiBusinessScene());
        if (e.getHumanAction() != null) vo.setHumanAction(e.getHumanAction());
        if (e.getHumanModifiedFields() != null) vo.setHumanModifiedFields(e.getHumanModifiedFields());
        if (e.getCreatedAt() != null) vo.setCreatedAt(e.getCreatedAt());
        return vo;
    }

    public static List<AiFeedbackLogVO> from(List<AiFeedbackLogEntity> list) {
        if (list == null) {
            return null;
        }
        return list.stream().map(AiFeedbackLogVO::from).collect(Collectors.toList());
    }

    /** Entity 分页 → VO 分页；只换 records 元素类型，分页元信息原样透传 */
    public static IPage<AiFeedbackLogVO> from(IPage<AiFeedbackLogEntity> page) {
        if (page == null) {
            return null;
        }
        com.baomidou.mybatisplus.extension.plugins.pagination.Page<AiFeedbackLogVO> out =
                new com.baomidou.mybatisplus.extension.plugins.pagination.Page<>(
                        page.getCurrent(), page.getSize(), page.getTotal());
        out.setRecords(from(page.getRecords()));
        return out;
    }
}
