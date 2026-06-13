package com.huicai.module.finance.dto;

import com.huicai.module.finance.entity.VoucherTemplateEntity;
import com.huicai.module.finance.entity.VoucherTemplateLineEntity;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

/**
 * 凭证模板视图对象 (含分录行).
 */
@Data
public class VoucherTemplateVO {

    private Long id;
    private String name;
    private String description;
    private String classification;
    private String numberPrefix;
    private Boolean isActive;
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
        private Integer lineOrder;
    }

    public static VoucherTemplateVO fromEntity(VoucherTemplateEntity e, List<VoucherTemplateLineEntity> lines) {
        VoucherTemplateVO vo = new VoucherTemplateVO();
        vo.setId(e.getId());
        vo.setName(e.getName());
        vo.setDescription(e.getDescription());
        vo.setClassification(e.getClassification());
        vo.setNumberPrefix(e.getNumberPrefix());
        vo.setIsActive(e.getIsActive());
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
        vo.setLineOrder(line.getLineOrder());
        return vo;
    }
}
