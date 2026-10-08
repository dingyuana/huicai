package com.huicai.base.voucher.dto;

import com.huicai.base.voucher.entity.VoucherTemplateEntity;
import com.huicai.base.voucher.entity.VoucherTemplateLineEntity;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

/**
 * 凭证模板视图对象 (含分录行).
 *
 * <p><b>P110 Phase 1（2026-10-06）</b>：字段与 {@link VoucherTemplateEntity} 对齐，
 * 删除了从未落库的 6 个野列（description/classification/source/direction/matchPriority/numberPrefix）
 * —— 旧实现中 {@code e.getXxx()} 恒 null，VO 输出对应字段也恒 null（AGENTS §4.5 第 38 类）。
 * 新增 3 个从未回读的列：templateCode/voucherTypeCode/summary（来源 Entity 已补对齐）。
 */
@Data
public class VoucherTemplateVO {

    private Long id;
    private String templateCode;
    private String name;
    private String businessType;
    private String voucherTypeCode;
    private String summary;
    private String entries;
    private Boolean isActive;
    private String remark;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    private List<LineVO> lines;

    @Data
    public static class LineVO {
        private Long id;
        private Long subjectId;
        private String subjectCode;
        private String subjectName;
        private String drAmountTemplate;
        private String crAmountTemplate;
        private String summaryTemplate;
        private String direction;
        private String assistType;
        private Boolean assistRequired;
        private Integer lineOrder;
    }

    public static VoucherTemplateVO fromEntity(VoucherTemplateEntity e, List<VoucherTemplateLineEntity> lines) {
        VoucherTemplateVO vo = new VoucherTemplateVO();
        vo.setId(e.getId());
        vo.setTemplateCode(e.getTemplateCode());
        vo.setName(e.getName());
        vo.setBusinessType(e.getBusinessType());
        vo.setVoucherTypeCode(e.getVoucherTypeCode());
        vo.setSummary(e.getSummary());
        vo.setEntries(e.getEntries());
        vo.setIsActive(e.getIsActive());
        vo.setRemark(e.getRemark());
        vo.setCreatedAt(e.getCreatedAt());
        vo.setUpdatedAt(e.getUpdatedAt());
        if (lines != null) {
            vo.setLines(lines.stream().map(VoucherTemplateVO::fromLineEntity).collect(Collectors.toList()));
        } else {
            vo.setLines(new ArrayList<>());
        }
        return vo;
    }

    public static LineVO fromLineEntity(VoucherTemplateLineEntity line) {
        LineVO vo = new LineVO();
        vo.setId(line.getId());
        vo.setSubjectId(line.getSubjectId());
        vo.setDrAmountTemplate(line.getDrAmountTemplate());
        vo.setCrAmountTemplate(line.getCrAmountTemplate());
        vo.setSummaryTemplate(line.getSummaryTemplate());
        vo.setDirection(line.getDirection());
        vo.setAssistType(line.getAssistType());
        vo.setAssistRequired(line.getAssistRequired());
        vo.setLineOrder(line.getLineOrder());
        return vo;
    }
}
