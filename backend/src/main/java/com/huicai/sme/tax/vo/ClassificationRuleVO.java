package com.huicai.sme.tax.vo;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.huicai.base.business.entity.ClassificationRuleEntity;
import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.Collectors;
import lombok.Data;

/**
 * ClassificationRuleVO —— 由 t_classification_rule 的真实列生成（批次 5）。
 *
 * <p>字段集 = 前端 classificationRule.ts#ClassificationRule 已声明的 16 个字段，
 * 逐个核对「DB 有该列」+「Entity 有该字段」，故既不漏前端字段也不凭空暴露。
 *
 * <p>⚠️ 本次据守卫与页面实测**从契约里删掉了两个字段**，不是从 VO 里删，是前端 interface 本身陈旧：
 * ① <b>tenantId</b>：ClassificationRuleList.vue 根本不读；且 P106 D-1 已裁定<b>废弃 tenantId</b>，
 *    继续声明只会诱导后人往多账套方向用它。（请求参数 pageRules 里的 tenantId 不动，那是入参不是出参。）
 * ② <b>updatedAt</b>：审计列且页面不读，属内部状态 —— 与 createdAt 不同，createdAt 有 5 个页面在用。
 *
 * <p>DB 有但本 VO 刻意不暴露：is_system（页面不读，属系统内置标记）
 */
@Data
public class ClassificationRuleVO {

    private Long id;
    private String name;
    private String ruleType;
    private String pattern;
    private String matchField;
    private String direction;
    private String classification;
    private Integer priority;
    private Boolean isActive;
    private String routeType;
    private Long debitSubjectId;
    private Long creditSubjectId;
    private String subjectLevel1;
    private String subjectLevel2;
    private String subjectLevel3;
    private LocalDateTime createdAt;

    public static ClassificationRuleVO from(ClassificationRuleEntity e) {
        if (e == null) {
            return null;
        }
        ClassificationRuleVO vo = new ClassificationRuleVO();
        vo.setId(e.getId());
        if (e.getId() != null) vo.setId(e.getId());
        if (e.getName() != null) vo.setName(e.getName());
        if (e.getRuleType() != null) vo.setRuleType(e.getRuleType());
        if (e.getPattern() != null) vo.setPattern(e.getPattern());
        if (e.getMatchField() != null) vo.setMatchField(e.getMatchField());
        if (e.getDirection() != null) vo.setDirection(e.getDirection());
        if (e.getClassification() != null) vo.setClassification(e.getClassification());
        if (e.getPriority() != null) vo.setPriority(e.getPriority());
        if (e.getIsActive() != null) vo.setIsActive(e.getIsActive());
        if (e.getRouteType() != null) vo.setRouteType(e.getRouteType());
        if (e.getDebitSubjectId() != null) vo.setDebitSubjectId(e.getDebitSubjectId());
        if (e.getCreditSubjectId() != null) vo.setCreditSubjectId(e.getCreditSubjectId());
        if (e.getSubjectLevel1() != null) vo.setSubjectLevel1(e.getSubjectLevel1());
        if (e.getSubjectLevel2() != null) vo.setSubjectLevel2(e.getSubjectLevel2());
        if (e.getSubjectLevel3() != null) vo.setSubjectLevel3(e.getSubjectLevel3());
        if (e.getCreatedAt() != null) vo.setCreatedAt(e.getCreatedAt());
        return vo;
    }

    public static List<ClassificationRuleVO> from(List<ClassificationRuleEntity> list) {
        if (list == null) {
            return null;
        }
        return list.stream().map(ClassificationRuleVO::from).collect(Collectors.toList());
    }

    /** Entity 分页 → VO 分页；只换 records 元素类型，分页元信息原样透传 */
    public static IPage<ClassificationRuleVO> from(IPage<ClassificationRuleEntity> page) {
        if (page == null) {
            return null;
        }
        com.baomidou.mybatisplus.extension.plugins.pagination.Page<ClassificationRuleVO> out =
                new com.baomidou.mybatisplus.extension.plugins.pagination.Page<>(
                        page.getCurrent(), page.getSize(), page.getTotal());
        out.setRecords(from(page.getRecords()));
        return out;
    }
}
