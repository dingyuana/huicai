package com.huicai.sme.cash.dto;

import com.huicai.base.business.entity.ClassificationRuleEntity;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * 流水分类规则新增/修改入参（REQ-2026-129 / P102 DTO 隔离 批次④）。
 *
 * <p><b>四个允许集全部以 {@code pg_get_constraintdef} 实测查证为准</b>，
 * 其中 {@code direction} 是<b>小写</b>（{@code in} / {@code out}），
 * 与 {@code chk_direction} 在其它表上的大写形态相反（AGENTS §4.2 第 14 条）：
 * <ul>
 *   <li>{@code chk_rule_type}：keyword / keyword_regex / counterparty_match</li>
 *   <li>{@code chk_match_field}：description / counterparty</li>
 *   <li>{@code chk_direction}：in / out（小写）</li>
 *   <li>{@code chk_route_type}：A / B / C</li>
 * </ul>
 *
 * <p>剔除 {@code tenantId}（Service 默认 1L，且与多租户隔离无关）、
 * {@code createdBy/updatedBy}（Service <b>硬编码 1L</b>，属可疑项，见下）、
 * {@code createdAt/updatedAt/deleted}。
 */
@Data
public class ClassificationRuleSaveDTO {

    @NotBlank(message = "规则名称不能为空")
    @Size(max = 128, message = "规则名称长度不能超过 128")
    private String name;

    @Pattern(regexp = "^$|keyword|keyword_regex|counterparty_match",
            message = "规则类型仅支持 keyword / keyword_regex / counterparty_match")
    private String ruleType;

    @Size(max = 255, message = "匹配模式过长")
    private String pattern;

    @Pattern(regexp = "^$|description|counterparty",
            message = "匹配字段仅支持 description / counterparty")
    private String matchField;

    @Pattern(regexp = "^$|in|out", message = "收支方向仅支持 in / out（小写）")
    private String direction;

    @NotBlank(message = "分类不能为空")
    @Size(max = 64, message = "分类长度不能超过 64")
    private String classification;

    private Integer priority;

    private Boolean isActive;

    @Pattern(regexp = "^$|A|B|C", message = "路由类型仅支持 A / B / C")
    private String routeType;

    private Boolean isSystem;

    private Long debitSubjectId;

    private Long creditSubjectId;

    private String subjectLevel1;

    private String subjectLevel2;

    private String subjectLevel3;

    public ClassificationRuleEntity toEntity() {
        ClassificationRuleEntity e = new ClassificationRuleEntity();
        e.setName(name);
        e.setRuleType(ruleType);
        e.setPattern(pattern);
        e.setMatchField(matchField);
        e.setDirection(direction);
        e.setClassification(classification);
        e.setPriority(priority);
        e.setIsActive(isActive);
        e.setRouteType(routeType);
        e.setIsSystem(isSystem);
        e.setDebitSubjectId(debitSubjectId);
        e.setCreditSubjectId(creditSubjectId);
        e.setSubjectLevel1(subjectLevel1);
        e.setSubjectLevel2(subjectLevel2);
        e.setSubjectLevel3(subjectLevel3);
        return e;
    }
}
